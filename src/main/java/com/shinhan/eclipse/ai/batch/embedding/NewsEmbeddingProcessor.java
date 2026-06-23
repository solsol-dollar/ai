package com.shinhan.eclipse.ai.batch.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.shinhan.eclipse.ai.domain.ipo.IpoNews;

import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class NewsEmbeddingProcessor implements ItemProcessor<IpoNews, EmbeddingItem> {

    @Value("${app.embedding.min-summary-length:200}")
    private int minSummaryLength;

    @Override
    public EmbeddingItem process(IpoNews news) throws Exception {
        String headline = news.getTitle() != null ? news.getTitle() : "";
        String summary  = news.getSummary() != null ? news.getSummary() : "";
        String content  = news.getContent() != null ? news.getContent() : "";

        // 콘텐츠 게이트
        String body = !summary.isBlank() ? summary : content;
        if (body.length() < minSummaryLength && headline.length() < 30) {
            log.debug("콘텐츠 기준 미달 스킵: newsId={}", news.getId());
            return EmbeddingItem.skipped(news.getId());
        }

        // 임베딩 텍스트 조합
        String ticker = (news.getSource() != null) ? news.getSource() : "";
        String publishedStr = news.getPublishedAt() != null
                ? news.getPublishedAt().format(DateTimeFormatter.ISO_DATE_TIME) : "";
        String bodyText = body.length() >= 200 ? body.substring(0, Math.min(500, body.length())) : headline;

        String embeddingText = String.format("[%s] %s\n%s\n발행일: %s\n출처: %s",
                ticker, headline, bodyText, publishedStr, news.getSource() != null ? news.getSource() : "");

        // Content hash (멱등성)
        String contentHash = sha256(embeddingText);

        // Document UUID
        String docId = UUID.nameUUIDFromBytes(("news-" + news.getId()).getBytes()).toString();

        // pgvector 메타데이터
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("news_id",      news.getId());
        metadata.put("ipo_id",       news.getIpoId());
        metadata.put("ticker",       ticker);
        metadata.put("source",       news.getSource() != null ? news.getSource() : "");
        metadata.put("published_at", publishedStr);

        Document document = new Document(docId, embeddingText, metadata);
        return EmbeddingItem.of(news.getId(), contentHash, docId, document);
    }

    private String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(text.getBytes());
        return HexFormat.of().formatHex(hash);
    }
}
