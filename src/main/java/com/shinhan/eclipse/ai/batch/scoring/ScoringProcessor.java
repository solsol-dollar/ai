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
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class ScoringProcessor implements ItemProcessor<Long, IpoScore> {

    private final IpoNewsAnalysisRepository analysisRepository;
    private final IpoNewsRepository ipoNewsRepository;
    private final IpoRepository ipoRepository;
    private final ObjectMapper objectMapper;

    @Override
    public IpoScore process(Long ipoId) throws Exception {
        IpoNewsAnalysis analysis = analysisRepository.findByIpoId(ipoId)
                .orElseThrow(() -> new IllegalStateException("분석 결과 없음: " + ipoId));

        Ipo ipo = ipoRepository.findById(ipoId).orElse(null);
        List<IpoNews> newsList = ipoNewsRepository
                .findByStatusAndEmbeddingStatusOrderById("ACTIVE", "COMPLETED")
                .stream().filter(n -> n.getIpoId().equals(ipoId)).toList();

        // 1. SentimentScore: (-1,1) → (0,1)
        double sentiment = analysis.getSentimentScore() != null
                ? (analysis.getSentimentScore().doubleValue() + 1.0) / 2.0 : 0.5;

        // 2. RecencyScore: 최신 뉴스일수록 높음 (IPO 7일 전이 최고)
        double recency = computeRecency(newsList, ipo != null ? ipo.getListingDate() : null);

        // 3. VolumeScore: log 정규화
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

        // 8. RiskPenalty: 리스크 요인 수 기반
        double risk = computeRiskPenalty(analysis.getRiskFactors());

        // 최종 공식
        double raw = 0.25 * sentiment + 0.15 * recency + 0.10 * volume
                   + 0.15 * sourceRel + 0.15 * signal + 0.10 * timing
                   + 0.10 * consistency - 0.20 * risk;
        int finalScore = Math.max(0, Math.min(100, (int) (raw * 100)));
        String grade = toGrade(finalScore);

        log.info("스코어링 완료: ipoId={}, score={}, grade={}", ipoId, finalScore, grade);
        return IpoScore.of(ipoId, analysis.getTicker(), finalScore, grade,
                sentiment, recency, volume, sourceRel, signal, timing, consistency, risk, newsCount);
    }

    private double computeRecency(List<IpoNews> newsList, LocalDate listingDate) {
        if (newsList.isEmpty() || listingDate == null) return 0.5;
        double avgDecay = newsList.stream()
                .filter(n -> n.getPublishedAt() != null)
                .mapToDouble(n -> {
                    long days = ChronoUnit.DAYS.between(n.getPublishedAt().toLocalDate(), listingDate);
                    return Math.exp(-0.02 * Math.max(0, days)); // λ=0.02
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
        if (daysToIpo < 0)   return 0.4;  // 이미 상장
        if (daysToIpo <= 7)  return 1.0;  // D-7 이내: 최고
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
            return 0.8; // 4개 이상
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
