package com.shinhan.eclipse.ai.api.score;

import com.shinhan.eclipse.ai.domain.score.IpoScore;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class IpoScoreResponse {

    private Long ipoId;
    private String ticker;
    private Integer finalScore;
    private String grade;
    private Integer newsCount;
    private LocalDateTime scoredAt;

    public static IpoScoreResponse from(IpoScore score) {
        return IpoScoreResponse.builder()
                .ipoId(score.getIpoId())
                .ticker(score.getTicker())
                .finalScore(score.getFinalScore())
                .grade(score.getGrade())
                .newsCount(score.getNewsCount())
                .scoredAt(score.getScoredAt())
                .build();
    }
}
