package com.shinhan.eclipse.ai.batch.translation;

import com.shinhan.eclipse.ai.domain.ipo.IpoNewsRepository;
import com.shinhan.eclipse.ai.domain.score.IpoScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpoTranslationWriter implements ItemWriter<IpoTranslationItem> {

    private final IpoScoreRepository scoreRepository;
    private final IpoNewsRepository newsRepository;

    @Override
    public void write(Chunk<? extends IpoTranslationItem> chunk) {
        for (IpoTranslationItem item : chunk) {
            if (item.newsId1() != null && item.titleKo1() != null)
                newsRepository.updateTitleKo(item.newsId1(), item.titleKo1());
            if (item.newsId1() != null && item.contentKo1() != null)
                newsRepository.updateContentKo(item.newsId1(), item.contentKo1());
            if (item.newsId2() != null && item.titleKo2() != null)
                newsRepository.updateTitleKo(item.newsId2(), item.titleKo2());
            if (item.newsId2() != null && item.contentKo2() != null)
                newsRepository.updateContentKo(item.newsId2(), item.contentKo2());
            if (item.summary() != null)
                scoreRepository.updateSummary(item.ipoId(), item.summary());
        }
        log.info("통합 요약 저장 완료: {}건", chunk.size());
    }
}
