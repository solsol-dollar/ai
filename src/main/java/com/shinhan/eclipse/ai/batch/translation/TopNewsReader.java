package com.shinhan.eclipse.ai.batch.translation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.eclipse.ai.domain.ipo.IpoNews;
import com.shinhan.eclipse.ai.domain.ipo.IpoNewsRepository;
import com.shinhan.eclipse.ai.domain.score.IpoScore;
import com.shinhan.eclipse.ai.domain.score.IpoScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class TopNewsReader implements ItemReader<IpoNews> {

    private final IpoScoreRepository scoreRepository;
    private final IpoNewsRepository newsRepository;
    private final ObjectMapper objectMapper;

    private Iterator<IpoNews> iterator;

    @Override
    public IpoNews read() {
        if (iterator == null) {
            List<IpoNews> targets = scoreRepository.findAll().stream()
                    .filter(s -> s.getTopNewsIds() != null)
                    .flatMap(s -> parseIds(s.getTopNewsIds()).stream())
                    .distinct()
                    .map(id -> newsRepository.findById(id).orElse(null))
                    .filter(n -> n != null && "PENDING".equals(n.getTranslationStatus()))
                    .toList();
            log.info("TranslationJob: 번역 대상 뉴스 {}건", targets.size());
            iterator = targets.iterator();
        }
        return iterator.hasNext() ? iterator.next() : null;
    }

    private List<Long> parseIds(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
