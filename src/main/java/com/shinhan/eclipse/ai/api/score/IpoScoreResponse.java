package com.shinhan.eclipse.ai.api.score;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.eclipse.ai.domain.score.IpoScore;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class IpoScoreResponse {

    private Long ipoId;
    private String ticker;
    private Integer finalScore;
    private String grade;
    private String reason;
    private String summary;
    private List<Long> topNewsIds;
    private Integer newsCount;
    private LocalDateTime scoredAt;

    public static IpoScoreResponse from(IpoScore score) {
        List<Long> parsedNewsIds = parseTopNewsIds(score.getTopNewsIds());
        return IpoScoreResponse.builder()
                .ipoId(score.getIpoId())
                .ticker(score.getTicker())
                .finalScore(score.getFinalScore())
                .grade(score.getGrade())
                .reason(score.getReason())
                .summary(score.getSummary())
                .topNewsIds(parsedNewsIds)
                .newsCount(score.getNewsCount())
                .scoredAt(score.getScoredAt())
                .build();
    }

    private static List<Long> parseTopNewsIds(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return new ObjectMapper().readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
