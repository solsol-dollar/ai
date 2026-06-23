package com.shinhan.eclipse.ai.batch.embedding;

import lombok.Getter;
import org.springframework.ai.document.Document;

@Getter
public class EmbeddingItem {
    private final Long newsId;
    private final String contentHash;
    private final String docId;
    private final Document document;
    private final boolean skipped;

    private EmbeddingItem(Long newsId, String contentHash, String docId, Document document, boolean skipped) {
        this.newsId      = newsId;
        this.contentHash = contentHash;
        this.docId       = docId;
        this.document    = document;
        this.skipped     = skipped;
    }

    public static EmbeddingItem of(Long newsId, String contentHash, String docId, Document document) {
        return new EmbeddingItem(newsId, contentHash, docId, document, false);
    }

    public static EmbeddingItem skipped(Long newsId) {
        return new EmbeddingItem(newsId, null, null, null, true);
    }
}
