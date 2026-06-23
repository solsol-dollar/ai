package com.shinhan.eclipse.ai.batch.analysis;

import com.shinhan.eclipse.ai.domain.analysis.IpoNewsAnalysisRepository;
import com.shinhan.eclipse.ai.domain.ipo.IpoNewsRepository;
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
public class EmbeddedNewsReader implements ItemReader<Long> {

    private final IpoNewsRepository ipoNewsRepository;
    private final IpoNewsAnalysisRepository analysisRepository;

    private Iterator<Long> iterator;
    private final Set<Long> seenIds = new HashSet<>();

    @Override
    public Long read() {
        if (iterator == null) {
            // embedding_status=COMPLETED인 ipo_id 중 미분석 목록
            List<Long> ipoIds = ipoNewsRepository
                    .findDistinctIpoIdByEmbeddingStatus("COMPLETED")
                    .stream()
                    .filter(ipoId -> !analysisRepository
                            .existsByIpoIdAndAnalysisStatus(ipoId, "COMPLETED"))
                    .toList();
            log.info("NewsAnalysisJob: 분석 대상 IPO {}개", ipoIds.size());
            iterator = ipoIds.iterator();
        }

        while (iterator.hasNext()) {
            Long ipoId = iterator.next();
            if (seenIds.contains(ipoId)) continue;
            seenIds.add(ipoId);
            return ipoId;
        }
        return null;
    }
}
