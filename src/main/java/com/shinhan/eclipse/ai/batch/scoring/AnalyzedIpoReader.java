package com.shinhan.eclipse.ai.batch.scoring;

import com.shinhan.eclipse.ai.domain.analysis.IpoNewsAnalysisRepository;
import com.shinhan.eclipse.ai.domain.score.IpoScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class AnalyzedIpoReader implements ItemReader<Long> {

    private final IpoNewsAnalysisRepository analysisRepository;
    private final IpoScoreRepository scoreRepository;

    private Iterator<Long> iterator;
    private final Set<Long> seenIds = new HashSet<>();

    @Override
    public Long read() {
        if (iterator == null) {
            List<Long> ipoIds = analysisRepository.findIpoIdsByAnalysisStatus("COMPLETED")
                    .stream()
                    .filter(id -> scoreRepository.findByIpoId(id)
                            .map(s -> s.getReason() == null || s.getTopNewsIds() == null)
                            .orElse(true))
                    .toList();
            log.info("ScoringJob: 스코어링 대상 IPO {}개", ipoIds.size());
            iterator = ipoIds.iterator();
        }
        while (iterator.hasNext()) {
            Long id = iterator.next();
            if (seenIds.contains(id)) continue;
            seenIds.add(id);
            return id;
        }
        return null;
    }
}
