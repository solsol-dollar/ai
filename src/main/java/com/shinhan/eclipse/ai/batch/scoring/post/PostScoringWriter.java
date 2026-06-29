package com.shinhan.eclipse.ai.batch.scoring.post;

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
public class PostScoringWriter implements ItemWriter<IpoScore> {

    private final IpoScoreRepository ipoScoreRepository;

    @Override
    public void write(Chunk<? extends IpoScore> chunk) {
        for (IpoScore score : chunk.getItems()) {
            ipoScoreRepository.save(score);
            log.info("상장 후 점수 저장: ipoId={}, postScore={}, postGrade={}",
                    score.getIpoId(), score.getPostFinalScore(), score.getPostGrade());
        }
    }
}
