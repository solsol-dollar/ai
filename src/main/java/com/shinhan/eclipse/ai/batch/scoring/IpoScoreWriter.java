package com.shinhan.eclipse.ai.batch.scoring;

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
public class IpoScoreWriter implements ItemWriter<IpoScore> {

    private final IpoScoreRepository scoreRepository;

    @Override
    public void write(Chunk<? extends IpoScore> chunk) {
        for (IpoScore score : chunk.getItems()) {
            scoreRepository.findByIpoId(score.getIpoId()).ifPresent(existing -> {
                scoreRepository.delete(existing);
                scoreRepository.flush();
            });
            scoreRepository.save(score);
            log.info("점수 저장: ipoId={}, score={}, grade={}",
                    score.getIpoId(), score.getFinalScore(), score.getGrade());
        }
    }
}
