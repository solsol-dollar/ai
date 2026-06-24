package com.shinhan.eclipse.ai.batch.scoring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.eclipse.ai.domain.analysis.IpoNewsAnalysis;
import com.shinhan.eclipse.ai.domain.analysis.IpoNewsAnalysisRepository;
import com.shinhan.eclipse.ai.domain.ipo.Ipo;
import com.shinhan.eclipse.ai.domain.ipo.IpoNews;
import com.shinhan.eclipse.ai.domain.ipo.IpoNewsRepository;
import com.shinhan.eclipse.ai.domain.ipo.IpoRepository;
import com.shinhan.eclipse.ai.domain.score.IpoScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class ScoringProcessor implements ItemProcessor<Long, IpoScore> {

    private final IpoNewsAnalysisRepository analysisRepository;
    private final IpoNewsRepository ipoNewsRepository;
    private final IpoRepository ipoRepository;
    private final ObjectMapper objectMapper;
    private final ChatModel chatModel;

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
        IpoNewsAnalysis analysis = analysisRepository.findByIpoId(ipoId)
                .orElseThrow(() -> new IllegalStateException("분석 결과 없음: " + ipoId));

        Ipo ipo = ipoRepository.findById(ipoId).orElse(null);
        LocalDate listingDate = ipo != null ? ipo.getListingDate() : null;

        List<IpoNews> newsList = ipoNewsRepository
                .findByIpoIdAndStatusAndEmbeddingStatus(ipoId, "ACTIVE", "COMPLETED")
                .stream()
                .filter(n -> listingDate == null
                        || n.getPublishedAt() == null
                        || n.getPublishedAt().toLocalDate().isBefore(listingDate))
                .toList();

        // 1. SentimentScore: (-1,1) → (0,1)
        double sentiment = analysis.getSentimentScore() != null
                ? (analysis.getSentimentScore().doubleValue() + 1.0) / 2.0 : 0.5;

        // 2. RecencyScore
        double recency = computeRecency(newsList, ipo != null ? ipo.getListingDate() : null);

        // 3. VolumeScore
        int newsCount = analysis.getNewsCount() != null ? analysis.getNewsCount() : newsList.size();
        double volume = Math.min(1.0, Math.log1p(newsCount) / Math.log1p(20));

        // 4. SourceReliabilityScore
        double sourceRel = computeSourceReliability(newsList);

        // 5. SignalStrengthScore
        double signal = analysis.getSignalStrength() != null
                ? analysis.getSignalStrength().doubleValue() : 0.5;

        // 6. MarketTimingScore
        double timing = computeMarketTiming(ipo != null ? ipo.getListingDate() : null);

        // 7. ConsistencyScore
        double consistency = analysis.getConsistencyScore() != null
                ? analysis.getConsistencyScore().doubleValue() : 0.5;

        // 8. RiskPenalty
        double risk = computeRiskPenalty(analysis.getRiskFactors());

        double raw = 0.25 * sentiment + 0.15 * recency + 0.10 * volume
                   + 0.15 * sourceRel + 0.15 * signal + 0.10 * timing
                   + 0.10 * consistency - 0.20 * risk;
        int finalScore = Math.max(0, Math.min(100, (int) (raw * 100)));
        String grade = toGrade(finalScore);

        ReasonResult reasonResult = generateReason(ipoId, finalScore, grade, sentiment, signal,
                consistency, risk, newsCount, analysis.getRiskFactors(), newsList);

        log.info("스코어링 완료: ipoId={}, score={}, grade={}, reason={}, topNewsIds={}",
                ipoId, finalScore, grade, reasonResult.reason(), reasonResult.topNewsIds());
        return IpoScore.of(ipoId, analysis.getTicker(), finalScore, grade,
                reasonResult.reason(), reasonResult.topNewsIds(),
                sentiment, recency, volume, sourceRel, signal, timing, consistency, risk, newsCount);
    }

    record ReasonResult(String reason, String topNewsIds) {}

    @SuppressWarnings("unchecked")
    private ReasonResult generateReason(Long ipoId, int finalScore, String grade,
            double sentiment, double signal, double consistency, double risk,
            int newsCount, String riskFactorsJson, List<IpoNews> newsList) {
        try {
            List<String> riskFactors = parseRiskFactors(riskFactorsJson);

            String newsLines = newsList.stream()
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

            java.util.Map<String, Object> parsed = objectMapper.readValue(raw, java.util.Map.class);

            String reason = (String) parsed.get("reason");
            if (reason != null && reason.length() > 40) reason = reason.substring(0, 39) + ".";

            List<Object> ids = (List<Object>) parsed.get("topNewsIds");
            String topNewsIds = ids != null && !ids.isEmpty()
                    ? objectMapper.writeValueAsString(ids.stream().limit(2).toList())
                    : null;

            return new ReasonResult(reason, topNewsIds);

        } catch (Exception e) {
            log.warn("판단 근거 생성 실패: ipoId={}, error={}", ipoId, e.getMessage());
            return new ReasonResult(null, null);
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

    private double computeRecency(List<IpoNews> newsList, LocalDate listingDate) {
        if (newsList.isEmpty() || listingDate == null) return 0.5;
        double avgDecay = newsList.stream()
                .filter(n -> n.getPublishedAt() != null)
                .mapToDouble(n -> {
                    long days = ChronoUnit.DAYS.between(n.getPublishedAt().toLocalDate(), listingDate);
                    return Math.exp(-0.02 * Math.max(0, days));
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

    private double computeMarketTiming(LocalDate listingDate) {
        if (listingDate == null) return 0.5;
        long daysToIpo = ChronoUnit.DAYS.between(LocalDate.now(), listingDate);
        if (daysToIpo < 0)   return 0.4;
        if (daysToIpo <= 7)  return 1.0;
        if (daysToIpo <= 30) return 0.8;
        if (daysToIpo <= 90) return 0.6;
        return 0.4;
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

    private String toGrade(int score) {
        if (score >= 80) return "STRONG_POSITIVE";
        if (score >= 60) return "POSITIVE";
        if (score >= 40) return "NEUTRAL";
        if (score >= 20) return "NEGATIVE";
        return "STRONG_NEGATIVE";
    }
}
