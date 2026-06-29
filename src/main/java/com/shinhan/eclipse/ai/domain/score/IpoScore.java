package com.shinhan.eclipse.ai.domain.score;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "ipo_score")
public class IpoScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ipoId;
    private String ticker;

    @Column(nullable = false)
    private Integer finalScore;

    @Column(nullable = false, length = 20)
    private String grade;

    @Column(length = 500)
    private String reason;

    @Column(columnDefinition = "JSON")
    private String topNewsIds;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private BigDecimal sentimentComponent;
    private BigDecimal recencyComponent;
    private BigDecimal volumeComponent;
    private BigDecimal sourceReliabilityComponent;
    private BigDecimal signalStrengthComponent;
    private BigDecimal marketTimingComponent;
    private BigDecimal consistencyComponent;
    private BigDecimal riskPenalty;
    private Integer newsCount;
    private LocalDateTime scoredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Integer postFinalScore;

    @Column(length = 30)
    private String postGrade;

    @Column(length = 500)
    private String postReason;

    @Column(columnDefinition = "JSON")
    private String postTopNewsIds;

    @Column(columnDefinition = "TEXT")
    private String postSummary;

    private Integer postNewsCount;
    private LocalDateTime postScoredAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static IpoScore of(Long ipoId, String ticker, int finalScore, String grade,
            String reason, String topNewsIds, double sentiment, double recency, double volume,
            double sourceRel, double signal, double timing, double consistency, double risk,
            int newsCount) {
        IpoScore s = new IpoScore();
        s.ipoId = ipoId;
        s.ticker = ticker;
        s.finalScore = finalScore;
        s.grade = grade;
        s.reason = reason;
        s.topNewsIds = topNewsIds;
        s.sentimentComponent      = BigDecimal.valueOf(sentiment);
        s.recencyComponent        = BigDecimal.valueOf(recency);
        s.volumeComponent         = BigDecimal.valueOf(volume);
        s.sourceReliabilityComponent = BigDecimal.valueOf(sourceRel);
        s.signalStrengthComponent = BigDecimal.valueOf(signal);
        s.marketTimingComponent   = BigDecimal.valueOf(timing);
        s.consistencyComponent    = BigDecimal.valueOf(consistency);
        s.riskPenalty             = BigDecimal.valueOf(risk);
        s.newsCount = newsCount;
        s.scoredAt = LocalDateTime.now();
        return s;
    }

    public void updatePostScore(int postFinalScore, String postGrade, String postReason,
            String postTopNewsIds, int postNewsCount) {
        this.postFinalScore = postFinalScore;
        this.postGrade = postGrade;
        this.postReason = postReason;
        this.postTopNewsIds = postTopNewsIds;
        this.postNewsCount = postNewsCount;
        this.postScoredAt = LocalDateTime.now();
    }

    public void updatePostSummary(String postSummary) {
        this.postSummary = postSummary;
    }
}
