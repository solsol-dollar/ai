package com.shinhan.eclipse.ai.batch.embedding;

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
public class IpoNewsEmbeddingReader implements ItemReader<IpoNews> {

    private final IpoNewsRepository ipoNewsRepository;

    private Iterator<IpoNews> iterator;
    private final Set<Long> seenIds = new HashSet<>();

    @Override
    public IpoNews read() {
        if (iterator == null) {
            List<IpoNews> pending = ipoNewsRepository
                    .findByStatusAndEmbeddingStatusOrderById("ACTIVE", "PENDING");
            log.info("NewsEmbeddingJob: 처리 대상 {}건", pending.size());
            iterator = pending.iterator();
        }

        while (iterator.hasNext()) {
            IpoNews news = iterator.next();
            if (seenIds.contains(news.getId())) {
                log.warn("중복 감지, 스킵: newsId={}", news.getId());
                continue;
            }
            seenIds.add(news.getId());
            return news;
        }
        return null;
    }
}
