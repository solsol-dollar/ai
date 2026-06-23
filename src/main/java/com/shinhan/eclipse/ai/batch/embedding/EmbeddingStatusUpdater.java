package com.shinhan.eclipse.ai.batch.embedding;

import com.shinhan.eclipse.ai.domain.ipo.IpoNewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EmbeddingStatusUpdater {

    private final IpoNewsRepository ipoNewsRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void update(Long newsId, String status, String docId, String contentHash) {
        if (docId != null) {
            ipoNewsRepository.updateEmbeddingResult(newsId, status, docId, contentHash);
        } else {
            ipoNewsRepository.updateEmbeddingStatus(newsId, status);
        }
    }
}
