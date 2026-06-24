package com.shinhan.eclipse.ai.batch.translation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsTranslationWriter implements ItemWriter<TranslationItem> {

    private final TranslationStatusUpdater updater;

    @Override
    public void write(Chunk<? extends TranslationItem> chunk) {
        for (TranslationItem item : chunk) {
            if (item.titleKo() == null && item.summaryKo() == null) {
                updater.markFailed(item.news().getId());
            } else {
                updater.update(item.news().getId(), item.titleKo(), item.summaryKo());
            }
        }
        log.info("번역 저장 완료: {}건", chunk.size());
    }
}
