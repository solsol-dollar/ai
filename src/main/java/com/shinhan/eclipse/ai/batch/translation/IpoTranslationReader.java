package com.shinhan.eclipse.ai.batch.translation;

import com.shinhan.eclipse.ai.domain.score.IpoScore;
import com.shinhan.eclipse.ai.domain.score.IpoScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class IpoTranslationReader implements ItemReader<IpoScore> {

    private final IpoScoreRepository scoreRepository;
    private Iterator<IpoScore> iterator;

    @Override
    public IpoScore read() {
        if (iterator == null) {
            List<IpoScore> targets = scoreRepository.findBySummaryIsNullAndTopNewsIdsIsNotNull();
            log.info("TranslationJob: 통합 요약 대상 IPO {}건", targets.size());
            iterator = targets.iterator();
        }
        return iterator.hasNext() ? iterator.next() : null;
    }
}
