package com.shinhan.eclipse.ai.batch.translation;

import com.shinhan.eclipse.ai.domain.ipo.IpoNewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TranslationStatusUpdater {

    private final IpoNewsRepository newsRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void update(Long newsId, String titleKo, String summaryKo) {
        newsRepository.updateTranslation(newsId, titleKo, summaryKo, "COMPLETED");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long newsId) {
        newsRepository.updateTranslationStatus(newsId, "FAILED");
    }
}
