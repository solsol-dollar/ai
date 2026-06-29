package com.shinhan.eclipse.ai.batch.scoring.post;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.eclipse.ai.domain.ipo.Ipo;
import com.shinhan.eclipse.ai.domain.ipo.IpoNews;
import com.shinhan.eclipse.ai.domain.ipo.IpoNewsRepository;
import com.shinhan.eclipse.ai.domain.ipo.IpoRepository;
import com.shinhan.eclipse.ai.domain.score.IpoScore;
import com.shinhan.eclipse.ai.domain.score.IpoScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class PostScoringProcessor implements ItemProcessor<Long, IpoScore> {

    private final IpoScoreRepository ipoScoreRepository;
    private final IpoNewsRepository ipoNewsRepository;
    private final IpoRepository ipoRepository;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    private static final String POST_ANALYSIS_SYSTEM = """
            당신은 IPO 상장 후 뉴스 분석 보조입니다.
            제공된 뉴스 목록을 분석하여 아래 JSON만 출력하세요.

            {
              "sentiment": <-1.0~1.0, 부정→양수>,
              "signalStrength": <0.0~1.0>,
              "consistencyScore": <0.0~1.0>,
              "riskFactors": ["<리스크 요인 문자열>"]
            }

            규칙:
            - 상장 후 시장 반응, 주가 흐름, 기관 평가 중심으로 분석
            - JSON 외 다른 텍스트 출력 금지
            """;

    private static final String REASON_SYSTEM_PROMPT = """
            당신은 IPO 분석 보조입니다.
            제공된 점수 지표와 뉴스 목록을 바탕으로 아래 JSON만 출력하세요.

            {
              "reason": "<이 IPO가 해당 등급을 받은 이유, 한국어 40자 이내 한 문장>",
              "topNewsIds": [<가장 관련도 높은 뉴스 id 2개, Long 타입>]
            }

            규칙:
            - "감성", "감정" 표현 금지 → 대신 "시장 심리", "투자 심리", "긍정적 기조" 사용
            - 수치 나열 금지
            - 투자 권유 표현 금지
            - reason은 마침표로 끝낼 것
            - JSON 외 다른 텍스트 출력 금지
            """;

    @Override
    public IpoScore process(Long ipoId) throws Exception {
        IpoScore ipoScore = ipoScoreRepository.findByIpoId(ipoId).orElse(null);
        if (ipoScore == null) {
            log.warn("PostScoringJob: IpoScore 없음 - ipoId={}", ipoId);
            return null;
        }

        Ipo ipo = ipoRepository.findById(ipoId).orElse(null);
        LocalDate listingDate = ipo != null ? ipo.getListingDate() : null;

        List<IpoNews> allNews = ipoNewsRepository
                .findByIpoIdAndStatusAndEmbeddingStatus(ipoId, "ACTIVE", "COMPLETED");

        // 상장일 이후 뉴스만 필터
        List<IpoNews> postNews = allNews.stream()
                .filter(n -> n.getPublishedAt() != null
                        && listingDate != null
                        && !n.getPublishedAt().toLocalDate().isBefore(listingDate))
                .toList();

        if (postNews.isEmpty()) {
            log.info("PostScoringJob: 상장 후 뉴스 없음 - ipoId={}", ipoId);
            return null;
        }

        // LLM 분석
        AnalysisResult analysisResult = analyzePostNews(ipoId, postNews);

        // 8개 요소 계산
        double sentiment  = (analysisResult.sentiment() + 1.0) / 2.0;
        double recency    = computeRecency(postNews, listingDate);
        double volume     = Math.min(1.0, Math.log1p(postNews.size()) / Math.log1p(20));
        double sourceRel  = computeSourceReliability(postNews);
        double signal     = analysisResult.signalStrength();
        double timing     = 0.5; // 상장 후 고정
        double consistency = analysisResult.consistencyScore();
        double risk       = computeRiskPenalty(analysisResult.riskFactorsJson());

        double raw = 0.25 * sentiment + 0.15 * recency + 0.10 * volume
                   + 0.15 * sourceRel + 0.15 * signal + 0.10 * timing
                   + 0.10 * consistency - 0.20 * risk;
        int postFinalScore = Math.max(0, Math.min(100, (int) (raw * 100)));
        String postGrade = toGrade(postFinalScore);

        ReasonResult reasonResult = generateReason(ipoId, postFinalScore, postGrade,
                sentiment, signal, consistency, risk, postNews.size(),
                analysisResult.riskFactorsJson(), postNews);

        ipoScore.updatePostScore(postFinalScore, postGrade, reasonResult.reason(),
                reasonResult.topNewsIds(), postNews.size());

        log.info("PostScoringJob 완료: ipoId={}, postScore={}, postGrade={}", ipoId, postFinalScore, postGrade);
        return ipoScore;
    }

    record AnalysisResult(double sentiment, double signalStrength, double consistencyScore, String riskFactorsJson) {}
    record ReasonResult(String reason, String topNewsIds) {}

    @SuppressWarnings("unchecked")
    private AnalysisResult analyzePostNews(Long ipoId, List<IpoNews> postNews) {
        try {
            String newsLines = postNews.stream()
                    .filter(n -> n.getTitle() != null)
                    .limit(5)
                    .map(n -> String.format("[id=%d] %s", n.getId(), n.getTitle()))
                    .collect(Collectors.joining("\n"));

            String raw = chatModel.call(
                    new Prompt(List.of(
                            new SystemMessage(POST_ANALYSIS_SYSTEM),
                            new UserMessage(newsLines)))
            ).getResult().getOutput().getText().trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("```", "")
                    .trim();

            Map<String, Object> parsed = objectMapper.readValue(raw, Map.class);
            double sentiment       = toDouble(parsed.get("sentiment"), 0.0);
            double signalStrength  = toDouble(parsed.get("signalStrength"), 0.5);
            double consistencyScore = toDouble(parsed.get("consistencyScore"), 0.5);

            List<?> riskList = parsed.get("riskFactors") instanceof List<?> list ? list : List.of();
            String riskJson = objectMapper.writeValueAsString(riskList);

            return new AnalysisResult(sentiment, signalStrength, consistencyScore, riskJson);
        } catch (Exception e) {
            log.warn("PostScoringJob LLM 분석 실패: ipoId={}, error={}", ipoId, e.getMessage());
            return new AnalysisResult(0.0, 0.5, 0.5, "[]");
        }
    }

    @SuppressWarnings("unchecked")
    private ReasonResult generateReason(Long ipoId, int finalScore, String grade,
            double sentiment, double signal, double consistency, double risk,
            int newsCount, String riskFactorsJson, List<IpoNews> postNews) {
        try {
            List<String> riskFactors = parseRiskFactors(riskFactorsJson);

            String newsLines = postNews.stream()
                    .filter(n -> n.getTitle() != null && !n.getTitle().isBlank())
                    .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> {
                            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
                            return list.stream()
                                    .filter(n -> seen.add(n.getTitle().trim().toLowerCase()))
                                    .limit(5)
                                    .map(n -> String.format("[id=%d] %s", n.getId(), n.getTitle()))
                                    .collect(Collectors.joining("\n"));
                        }
                    ));

            String userPrompt = String.format("""
                    종합점수: %d/100 (%s)
                    시장 심리: %.2f / 신호강도: %.2f / 일관성: %.2f / 리스크패널티: %.2f
                    리스크 요인: %s
                    뉴스 목록:
                    %s
                    뉴스 수: %d건
                    """,
                    finalScore, grade,
                    sentiment, signal, consistency, risk,
                    riskFactors.isEmpty() ? "없음" : String.join(", ", riskFactors),
                    newsLines.isBlank() ? "없음" : newsLines,
                    newsCount);

            String raw = chatModel.call(
                    new Prompt(List.of(
                            new SystemMessage(REASON_SYSTEM_PROMPT),
                            new UserMessage(userPrompt)))
            ).getResult().getOutput().getText().trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("```", "")
                    .trim();

            Map<String, Object> parsed = objectMapper.readValue(raw, Map.class);
            String reason = (String) parsed.get("reason");
            if (reason != null && reason.length() > 40) reason = reason.substring(0, 39) + ".";

            List<Object> ids = (List<Object>) parsed.get("topNewsIds");
            String topNewsIds = ids != null && !ids.isEmpty()
                    ? objectMapper.writeValueAsString(ids.stream().limit(2).toList())
                    : null;

            return new ReasonResult(reason, topNewsIds);
        } catch (Exception e) {
            log.warn("PostScoringJob 판단 근거 생성 실패: ipoId={}, error={}", ipoId, e.getMessage());
            return new ReasonResult(null, null);
        }
    }

    private double computeRecency(List<IpoNews> postNews, LocalDate listingDate) {
        if (postNews.isEmpty() || listingDate == null) return 0.5;
        double avgDecay = postNews.stream()
                .filter(n -> n.getPublishedAt() != null)
                .mapToDouble(n -> {
                    long daysSinceListing = ChronoUnit.DAYS.between(
                            listingDate, n.getPublishedAt().toLocalDate());
                    return Math.exp(-0.05 * Math.max(0, daysSinceListing));
                })
                .average().orElse(0.5);
        return Math.min(1.0, avgDecay);
    }

    private double computeSourceReliability(List<IpoNews> newsList) {
        if (newsList.isEmpty()) return 0.6;
        return newsList.stream().mapToDouble(n -> {
            String src = n.getSource() != null ? n.getSource().toUpperCase() : "";
            if (src.contains("FINNHUB")) return 0.9;
            if (src.contains("EODHD"))   return 0.75;
            return 0.6;
        }).average().orElse(0.6);
    }

    private double computeRiskPenalty(String riskFactorsJson) {
        if (riskFactorsJson == null || riskFactorsJson.isBlank()) return 0.0;
        try {
            List<?> factors = objectMapper.readValue(riskFactorsJson, new TypeReference<List<?>>() {});
            int count = factors.size();
            if (count == 0) return 0.0;
            if (count == 1) return 0.2;
            if (count == 2) return 0.4;
            if (count == 3) return 0.6;
            return 0.8;
        } catch (Exception e) {
            return 0.3;
        }
    }

    private List<String> parseRiskFactors(String riskFactorsJson) {
        if (riskFactorsJson == null || riskFactorsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(riskFactorsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toGrade(int score) {
        if (score >= 80) return "STRONG_POSITIVE";
        if (score >= 60) return "POSITIVE";
        if (score >= 40) return "NEUTRAL";
        if (score >= 20) return "NEGATIVE";
        return "STRONG_NEGATIVE";
    }

    private double toDouble(Object val, double def) {
        if (val instanceof Number n) return n.doubleValue();
        return def;
    }
}
