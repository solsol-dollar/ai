package com.shinhan.eclipse.ai.batch.embedding;

import com.shinhan.eclipse.ai.domain.ipo.IpoNewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class PgVectorEmbeddingWriter implements ItemWriter<EmbeddingItem> {

    private final VectorStore vectorStore;
    private final IpoNewsRepository ipoNewsRepository;
    private final EmbeddingStatusUpdater statusUpdater;

    @Override
    public void write(Chunk<? extends EmbeddingItem> chunk) {
        List<? extends EmbeddingItem> items = chunk.getItems();

        // 스킵된 항목 처리 (상장 후 뉴스 또는 콘텐츠 기준 미달)
        items.stream().filter(EmbeddingItem::isSkipped)
             .forEach(item -> statusUpdater.update(item.getNewsId(), "SKIPPED", null, null));

        // 임베딩 대상만 필터링
        List<? extends EmbeddingItem> toEmbed = items.stream()
                .filter(item -> !item.isSkipped())
                .toList();
        if (toEmbed.isEmpty()) return;

        List<Document> documents = toEmbed.stream().map(EmbeddingItem::getDocument).toList();
        try {
            vectorStore.add(documents);
            toEmbed.forEach(item ->
                    statusUpdater.update(item.getNewsId(), "COMPLETED", item.getDocId(), item.getContentHash()));
            log.info("임베딩 완료: {}건", toEmbed.size());
        } catch (Exception e) {
            log.error("임베딩 실패: {}", e.getMessage());
            toEmbed.forEach(item -> statusUpdater.update(item.getNewsId(), "FAILED", null, null));
        }
    }
}
