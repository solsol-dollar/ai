package com.shinhan.eclipse.ai.batch.embedding.post;

import com.shinhan.eclipse.ai.batch.embedding.EmbeddingItem;
import com.shinhan.eclipse.ai.domain.ipo.Ipo;
import com.shinhan.eclipse.ai.domain.ipo.IpoNews;
import com.shinhan.eclipse.ai.domain.ipo.IpoNewsRepository;
import com.shinhan.eclipse.ai.domain.ipo.IpoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * 상장 후 뉴스(publishedAt >= listingDate)만 pgvector에 임베딩.
 * newsEmbeddingJob의 상장 전 게이트와 반대 방향으로 동작한다.
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class PostNewsEmbeddingProcessor implements ItemProcessor<IpoNews, EmbeddingItem> {

    @Value("${app.embedding.min-summary-length:200}")
    private int minSummaryLength;

    private final IpoRepository ipoRepository;
    private final IpoNewsRepository ipoNewsRepository;

    @Override
    public EmbeddingItem process(IpoNews news) throws Exception {
        String headline = news.getTitle() != null ? news.getTitle() : "";
        String summary  = news.getSummary() != null ? news.getSummary() : "";
        String content  = news.getContent() != null ? news.getContent() : "";

        // 상장 후 뉴스 게이트: listingDate 이후 뉴스만 임베딩
        if (news.getPublishedAt() != null) {
            LocalDate listingDate = ipoRepository.findById(news.getIpoId())
                    .map(Ipo::getListingDate)
                    .orElse(null);
            if (listingDate == null || news.getPublishedAt().toLocalDate().isBefore(listingDate)) {
                log.debug("상장 전 뉴스 스킵: newsId={}", news.getId());
                return EmbeddingItem.skipped(news.getId());
            }
        } else {
            return EmbeddingItem.skipped(news.getId());
        }

        // 콘텐츠 게이트
        String body = !summary.isBlank() ? summary : content;
        if (body.length() < minSummaryLength && headline.length() < 30) {
            log.debug("콘텐츠 기준 미달 스킵: newsId={}", news.getId());
            return EmbeddingItem.skipped(news.getId());
        }

        String ticker     = "ipo_" + news.getIpoId();
        String publishedStr = news.getPublishedAt().format(DateTimeFormatter.ISO_DATE_TIME);
        String bodyText   = body.length() >= 200 ? body.substring(0, Math.min(500, body.length())) : headline;

        String embeddingText = String.format("[%s] %s\n%s\n발행일: %s\n출처: %s",
                ticker, headline, bodyText, publishedStr, news.getSource() != null ? news.getSource() : "");

        String contentHash = sha256(embeddingText);
        if (ipoNewsRepository.findByContentHashAndEmbeddingStatus(contentHash, "COMPLETED").isPresent()) {
            log.debug("중복 콘텐츠 스킵: newsId={}", news.getId());
            return EmbeddingItem.skipped(news.getId());
        }

        String docId = UUID.nameUUIDFromBytes(("post-news-" + news.getId()).getBytes()).toString();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("news_id",      news.getId());
        metadata.put("ipo_id",       news.getIpoId());
        metadata.put("ticker",       ticker);
        metadata.put("source",       news.getSource() != null ? news.getSource() : "");
        metadata.put("published_at", publishedStr);

        Document document = new Document(docId, embeddingText, metadata);
        log.debug("상장 후 뉴스 임베딩 준비: newsId={}, ipoId={}", news.getId(), news.getIpoId());
        return EmbeddingItem.of(news.getId(), contentHash, docId, document);
    }

    private String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
