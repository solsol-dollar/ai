package com.shinhan.eclipse.ai.domain.analysis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "ipo_news_analysis")
public class IpoNewsAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ipoId;

    private String ticker;

    @Column(nullable = false, length = 20)
    private String analysisStatus = "PENDING";

    @Column(precision = 5, scale = 4)
    private java.math.BigDecimal sentimentScore;

    @Column(precision = 5, scale = 4)
    private java.math.BigDecimal signalStrength;

    @Column(precision = 5, scale = 4)
    private java.math.BigDecimal consistencyScore;

    @Column(columnDefinition = "JSON")
    private String riskFactors;

    @Column(columnDefinition = "JSON")
    private String sourceNewsIndexes;

    @Column(columnDefinition = "TEXT")
    private String rawLlmResponse;

    private Integer newsCount;
    private LocalDateTime analyzedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static IpoNewsAnalysis insufficient(Long ipoId, String ticker) {
        IpoNewsAnalysis a = new IpoNewsAnalysis();
        a.ipoId = ipoId;
        a.ticker = ticker;
        a.analysisStatus = "INSUFFICIENT_DATA";
        a.newsCount = 0;
        a.analyzedAt = LocalDateTime.now();
        return a;
    }

    public static IpoNewsAnalysis completed(Long ipoId, String ticker,
            java.math.BigDecimal sentiment, java.math.BigDecimal signal,
            java.math.BigDecimal consistency, String riskFactors,
            String sourceIndexes, String rawResponse, int newsCount) {
        IpoNewsAnalysis a = new IpoNewsAnalysis();
        a.ipoId = ipoId;
        a.ticker = ticker;
        a.analysisStatus = "COMPLETED";
        a.sentimentScore = sentiment;
        a.signalStrength = signal;
        a.consistencyScore = consistency;
        a.riskFactors = riskFactors;
        a.sourceNewsIndexes = sourceIndexes;
        a.rawLlmResponse = rawResponse;
        a.newsCount = newsCount;
        a.analyzedAt = LocalDateTime.now();
        return a;
    }
}
