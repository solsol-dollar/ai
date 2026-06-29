package com.shinhan.eclipse.ai.batch.embedding.post;

import com.shinhan.eclipse.ai.domain.ipo.IpoNews;
import com.shinhan.eclipse.ai.domain.ipo.IpoNewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class PostNewsEmbeddingReader implements ItemReader<IpoNews> {

    private final IpoNewsRepository ipoNewsRepository;

    private Iterator<IpoNews> iterator;
    private final Set<Long> seenIds = new HashSet<>();

    @Override
    public IpoNews read() {
        if (iterator == null) {
            // 상장 후 뉴스 게이트에 의해 SKIPPED 처리된 뉴스 + 신규 PENDING 뉴스 모두 대상
            List<IpoNews> pending = ipoNewsRepository
                    .findByStatusAndEmbeddingStatusInOrderById("ACTIVE", List.of("PENDING", "SKIPPED"));
            log.info("PostNewsEmbeddingJob: 처리 대상 {}건", pending.size());
            iterator = pending.iterator();
        }

        while (iterator.hasNext()) {
            IpoNews news = iterator.next();
            if (seenIds.contains(news.getId())) continue;
            seenIds.add(news.getId());
            return news;
        }
        return null;
    }
}
