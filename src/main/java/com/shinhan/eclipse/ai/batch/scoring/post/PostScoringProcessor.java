package com.shinhan.eclipse.ai.batch.scoring.post;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.eclipse.ai.domain.ipo.Ipo;
import com.shinhan.eclipse.ai.domain.ipo.IpoRepository;
import com.shinhan.eclipse.ai.domain.score.IpoScore;
import com.shinhan.eclipse.ai.domain.score.IpoScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class PostScoringProcessor implements ItemProcessor<Long, IpoScore> {

    private final IpoScoreRepository ipoScoreRepository;
    private final IpoRepository ipoRepository;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Value("${app.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${app.rag.min-news-count:3}")
    private int minNewsCount;

    private static final String ANALYSIS_SYSTEM_PROMPT = """
            You are an IPO post-listing news analysis assistant. Analyze the provided news articles and return a JSON response.

            Rules:
            1. Base your analysis ONLY on the provided news articles. No speculation.
            2. Focus on post-listing market reaction, stock performance, and institutional evaluation.
            3. You MUST include sourceNewsIndexes (list of article indexes you referenced).
            4. NEVER use phrases like "investment attractiveness" or "buy recommendation".

            Return JSON only:
            {
              "sentimentScore": <float -1.0 to 1.0>,
              "signalStrength": <float 0.0 to 1.0>,
              "consistencyScore": <float 0.0 to 1.0>,
              "riskFactors": [<string>, ...],
              "sourceNewsIndexes": [<int>, ...]
            }
            """;

    private static final String REASON_SYSTEM_PROMPT = """
            당신은 IPO 분석 보조입니다.
            제공된 점수 지표와 뉴스 목록을 바탕으로 아래 JSON만 출력하세요.

            {
              "reason": "<이 IPO가 해당 등급을 받은 이유, 한국어 40자 이내 한 문장>",
              "topNewsIds": [<관련도 높은 뉴스 id. 출처(source)가 2종류 이상이면 각 출처에서 1개씩 총 2개, 출처가 1종류면 1개만>]
            }

            규칙:
            - "감성", "감정" 표현 금지 → 대신 "시장 심리", "투자 심리", "긍정적 기조" 사용
            - 수치 나열 금지
            - 투자 권유 표현 금지
            - reason은 마침표로 끝낼 것
            - topNewsIds는 서로 다른 출처(source)의 기사에서만 선택할 것
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
        if (ipo == null || ipo.getListingDate() == null) {
            log.warn("PostScoringJob: IPO 또는 listingDate 없음 - ipoId={}", ipoId);
            return null;
        }
        LocalDate listingDate = ipo.getListingDate();

        // pgvector에서 전체 뉴스 검색 후 상장 후 뉴스만 필터
        List<Document> allDocs = searchByIpoId(ipoId);
        List<Document> postDocs = filterPostListingDocs(allDocs, listingDate);

        if (postDocs.size() < minNewsCount) {
            log.info("PostScoringJob: 상장 후 뉴스 부족 - ipoId={}, 수집={}건", ipoId, postDocs.size());
            return null;
        }

        // LLM 분석 (NewsAnalysisProcessor와 동일한 패턴)
        AnalysisResult analysis = analyzePostNews(ipoId, postDocs);

        // 8개 요소 계산
        double sentiment   = (analysis.sentimentScore() + 1.0) / 2.0;
        double recency     = computeRecency(postDocs, listingDate);
        double volume      = Math.min(1.0, Math.log1p(postDocs.size()) / Math.log1p(20));
        double sourceRel   = computeSourceReliability(postDocs);
        double signal      = analysis.signalStrength();
        double timing      = 0.5; // 상장 후 고정
        double consistency = analysis.consistencyScore();
        double risk        = computeRiskPenalty(analysis.riskFactorsJson());

        double raw = 0.25 * sentiment + 0.15 * recency + 0.10 * volume
                   + 0.15 * sourceRel + 0.15 * signal + 0.10 * timing
                   + 0.10 * consistency - 0.20 * risk;
        int postFinalScore = Math.max(0, Math.min(100, (int) (raw * 100)));
        String postGrade   = toGrade(postFinalScore);

        ReasonResult reason = generateReason(ipoId, postFinalScore, postGrade,
                sentiment, signal, consistency, risk, postDocs, analysis.riskFactorsJson());

        ipoScore.updatePostScore(postFinalScore, postGrade, reason.reason(),
                reason.topNewsIds(), postDocs.size());

        log.info("PostScoringJob 완료: ipoId={}, postScore={}, postGrade={}", ipoId, postFinalScore, postGrade);
        return ipoScore;
    }

    // --- pgvector 검색 (NewsAnalysisProcessor와 동일한 fallback 전략) ---

    private List<Document> searchByIpoId(Long ipoId) {
        Filter.Expression ipoFilter = new FilterExpressionBuilder()
                .eq("ipo_id", ipoId)
                .build();

        SearchRequest base = SearchRequest.builder()
                .query("IPO post-listing news stock market analysis ipo_id:" + ipoId)
                .topK(20)
                .similarityThreshold(similarityThreshold)
                .filterExpression(ipoFilter)
                .build();

        List<Document> docs = vectorStore.similaritySearch(base);
        if (!docs.isEmpty()) return docs;

        docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query("IPO post-listing news stock market analysis ipo_id:" + ipoId)
                .topK(30)
                .similarityThreshold(0.5)
                .filterExpression(ipoFilter)
                .build());
        if (!docs.isEmpty()) return docs;

        return vectorStore.similaritySearch(SearchRequest.builder()
                .query("IPO post-listing news stock market analysis ipo_id:" + ipoId)
                .topK(30)
                .similarityThreshold(0.3)
                .filterExpression(ipoFilter)
                .build());
    }

    private List<Document> filterPostListingDocs(List<Document> docs, LocalDate listingDate) {
        return docs.stream()
                .filter(doc -> {
                    String pubAt = (String) doc.getMetadata().get("published_at");
                    if (pubAt == null || pubAt.isBlank()) return false;
                    try {
                        LocalDate pubDate = LocalDateTime.parse(pubAt, DateTimeFormatter.ISO_DATE_TIME)
                                .toLocalDate();
                        return !pubDate.isBefore(listingDate);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toList();
    }

    // --- LLM 분석 ---

    @SuppressWarnings("unchecked")
    private AnalysisResult analyzePostNews(Long ipoId, List<Document> docs) {
        try {
            String newsContext = buildNewsContext(docs);
            String userPrompt = "IPO ID: " + ipoId + "\n\nNews articles:\n" + newsContext;

            String raw = chatModel.call(
                    new Prompt(List.of(new SystemMessage(ANALYSIS_SYSTEM_PROMPT), new UserMessage(userPrompt)))
            ).getResult().getOutput().getText().trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("```", "")
                    .trim();

            Map<String, Object> parsed = objectMapper.readValue(raw, Map.class);
            double sentiment       = toDouble(parsed.get("sentimentScore"), 0.0);
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
            List<Document> postDocs, String riskFactorsJson) {
        try {
            List<String> riskFactors = parseRiskFactors(riskFactorsJson);

            java.util.LinkedHashMap<String, Document> bySource = new java.util.LinkedHashMap<>();
            for (Document doc : postDocs) {
                String src = doc.getMetadata().getOrDefault("source", "unknown").toString();
                bySource.putIfAbsent(src, doc);
            }
            String newsLines = bySource.values().stream()
                    .map(doc -> {
                        Object newsId = doc.getMetadata().get("news_id");
                        String src = doc.getMetadata().getOrDefault("source", "unknown").toString();
                        String title = doc.getText().lines().findFirst().orElse("")
                                .replaceAll("^\\[.*?\\]\\s*", "");
                        return newsId != null
                                ? String.format("[id=%d] [%s] %s", ((Number) newsId).longValue(), src, title)
                                : title;
                    })
                    .collect(Collectors.joining("\n"));

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
                    postDocs.size());

            String raw = chatModel.call(
                    new Prompt(List.of(new SystemMessage(REASON_SYSTEM_PROMPT), new UserMessage(userPrompt)))
            ).getResult().getOutput().getText().trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("```", "")
                    .trim();

            Map<String, Object> parsed = objectMapper.readValue(raw, Map.class);
            String reason = (String) parsed.get("reason");
            if (reason != null && reason.length() > 40) reason = reason.substring(0, 39) + ".";

            // topNewsIds: pgvector metadata의 news_id 사용
            List<Object> ids = (List<Object>) parsed.get("topNewsIds");
            String topNewsIds = ids != null && !ids.isEmpty()
                    ? objectMapper.writeValueAsString(ids.stream().limit(2).toList())
                    : objectMapper.writeValueAsString(
                            postDocs.stream()
                                    .limit(2)
                                    .map(doc -> doc.getMetadata().get("news_id"))
                                    .filter(Objects::nonNull)
                                    .map(id -> ((Number) id).longValue())
                                    .toList());

            return new ReasonResult(reason, topNewsIds);
        } catch (Exception e) {
            log.warn("PostScoringJob 판단 근거 생성 실패: ipoId={}, error={}", ipoId, e.getMessage());
            return new ReasonResult(null, null);
        }
    }

    // --- 점수 구성 요소 계산 ---

    private double computeRecency(List<Document> postDocs, LocalDate listingDate) {
        return postDocs.stream()
                .mapToDouble(doc -> {
                    String pubAt = (String) doc.getMetadata().get("published_at");
                    if (pubAt == null || pubAt.isBlank()) return 0.5;
                    try {
                        LocalDate pubDate = LocalDateTime.parse(pubAt, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
                        long daysSinceListing = ChronoUnit.DAYS.between(listingDate, pubDate);
                        return Math.exp(-0.05 * Math.max(0, daysSinceListing));
                    } catch (Exception e) {
                        return 0.5;
                    }
                })
                .average().orElse(0.5);
    }

    private double computeSourceReliability(List<Document> postDocs) {
        if (postDocs.isEmpty()) return 0.6;
        return postDocs.stream().mapToDouble(doc -> {
            String src = ((String) doc.getMetadata().getOrDefault("source", "")).toUpperCase();
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

    // --- 유틸 ---

    private String buildNewsContext(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            sb.append("[").append(i).append("] ").append(docs.get(i).getText()).append("\n\n");
        }
        return sb.toString();
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

    record AnalysisResult(double sentimentScore, double signalStrength, double consistencyScore, String riskFactorsJson) {}
    record ReasonResult(String reason, String topNewsIds) {}
}
