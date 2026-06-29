package com.shinhan.eclipse.ai.batch.translation.post;

import com.shinhan.eclipse.ai.domain.score.IpoScore;
import com.shinhan.eclipse.ai.domain.score.IpoScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class PostTranslationWriter implements ItemWriter<IpoScore> {

    private final IpoScoreRepository ipoScoreRepository;

    @Override
    public void write(Chunk<? extends IpoScore> chunk) {
        for (IpoScore score : chunk.getItems()) {
            ipoScoreRepository.save(score);
        }
        log.info("PostTranslationJob 요약 저장 완료: {}건", chunk.size());
    }
}
