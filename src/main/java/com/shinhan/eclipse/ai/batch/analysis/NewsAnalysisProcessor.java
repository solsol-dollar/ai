package com.shinhan.eclipse.ai.batch.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.eclipse.ai.domain.analysis.IpoNewsAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class NewsAnalysisProcessor implements ItemProcessor<Long, AnalysisResult> {

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Value("${app.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${app.rag.min-news-count:3}")
    private int minNewsCount;

    private static final String SYSTEM_PROMPT = """
            You are an IPO news analysis assistant. Analyze the provided news articles about an IPO and return a JSON response.

            Rules:
            1. Base your analysis ONLY on the provided news articles. No speculation.
            2. You MUST include sourceNewsIndexes (list of article indexes you referenced).
            3. NEVER use phrases like "investment attractiveness" or "buy recommendation".
            4. Headlines alone are valid evidence.

            Return JSON only:
            {
              "sentimentScore": <float -1.0 to 1.0>,
              "signalStrength": <float 0.0 to 1.0>,
              "consistencyScore": <float 0.0 to 1.0>,
              "riskFactors": [<string>, ...],
              "sourceNewsIndexes": [<int>, ...]
            }
            """;

    @Override
    @SuppressWarnings("unchecked")
    public AnalysisResult process(Long ipoId) throws Exception {
        List<Document> docs = searchWithFallback(ipoId);

        if (docs.size() < minNewsCount) {
            log.info("뉴스 부족 INSUFFICIENT_DATA: ipoId={}, 수집={}건", ipoId, docs.size());
            return AnalysisResult.of(ipoId, IpoNewsAnalysis.insufficient(ipoId, null));
        }

        String newsContext = buildNewsContext(docs);
        String userPrompt = "IPO ID: " + ipoId + "\n\nNews articles:\n" + newsContext;

        String rawResponse = chatModel.call(
            new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userPrompt)))
        ).getResult().getOutput().getText();

        log.info("LLM 응답 수신: ipoId={}, newsCount={}", ipoId, docs.size());

        // 코드펜스 제거 (GPT가 ```json ... ``` 로 감쌀 수 있음)
        String jsonStr = rawResponse.trim()
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("```", "")
                .trim();

        try {
            Map<String, Object> parsed = objectMapper.readValue(jsonStr, Map.class);
            List<Integer> sourceIndexes = (List<Integer>) parsed.get("sourceNewsIndexes");

            if (sourceIndexes == null || sourceIndexes.isEmpty()) {
                log.warn("sourceNewsIndexes 누락: ipoId={}", ipoId);
                return AnalysisResult.of(ipoId, IpoNewsAnalysis.insufficient(ipoId, null));
            }

            IpoNewsAnalysis analysis = IpoNewsAnalysis.completed(
                ipoId, null,
                toBigDecimal(parsed.get("sentimentScore")),
                toBigDecimal(parsed.get("signalStrength")),
                toBigDecimal(parsed.get("consistencyScore")),
                objectMapper.writeValueAsString(parsed.get("riskFactors")),
                objectMapper.writeValueAsString(sourceIndexes),
                rawResponse,
                docs.size()
            );
            return AnalysisResult.of(ipoId, analysis);

        } catch (Exception e) {
            log.error("LLM 응답 파싱 실패: ipoId={}, raw=[{}], error={}", ipoId, rawResponse, e.getMessage());
            return AnalysisResult.of(ipoId, IpoNewsAnalysis.insufficient(ipoId, null));
        }
    }

    private List<Document> searchWithFallback(Long ipoId) {
        // F-0: ipo_id 필터 기본 검색
        Filter.Expression ipoFilter = new FilterExpressionBuilder()
                .eq("ipo_id", ipoId)
                .build();

        SearchRequest base = SearchRequest.builder()
                .query("IPO news stock market analysis ipo_id:" + ipoId)
                .topK(10)
                .similarityThreshold(similarityThreshold)
                .filterExpression(ipoFilter)
                .build();

        List<Document> docs = vectorStore.similaritySearch(base);
        if (docs.size() >= minNewsCount) return docs;

        // F-1: threshold 완화
        docs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query("IPO news stock market analysis ipo_id:" + ipoId)
                .topK(15)
                .similarityThreshold(0.5)
                .filterExpression(ipoFilter)
                .build());
        if (docs.size() >= minNewsCount) return docs;

        // F-2: threshold 대폭 완화
        docs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query("IPO news stock market analysis ipo_id:" + ipoId)
                .topK(20)
                .similarityThreshold(0.3)
                .filterExpression(ipoFilter)
                .build());
        if (docs.size() >= minNewsCount) return docs;

        // F-3: 필터 없이 쿼리만
        docs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query("IPO news stock market analysis ipo_id:" + ipoId)
                .topK(10)
                .similarityThreshold(0.3)
                .build());

        return docs;
    }

    private String buildNewsContext(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            sb.append("[").append(i).append("] ").append(docs.get(i).getText()).append("\n\n");
        }
        return sb.toString();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
