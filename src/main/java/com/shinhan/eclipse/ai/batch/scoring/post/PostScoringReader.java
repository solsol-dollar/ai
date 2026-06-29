package com.shinhan.eclipse.ai.batch.scoring.post;

import com.shinhan.eclipse.ai.domain.score.IpoScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class PostScoringReader implements ItemReader<Long> {

    private final IpoScoreRepository ipoScoreRepository;

    private Iterator<Long> iterator;
    private final Set<Long> seenIds = new HashSet<>();

    @Override
    public Long read() {
        if (iterator == null) {
            List<Long> ipoIds = ipoScoreRepository.findIpoIdsEligibleForPostScoring(
                    LocalDate.now().minusDays(7));
            log.info("PostScoringJob: 상장 후 스코어링 대상 IPO {}개", ipoIds.size());
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
