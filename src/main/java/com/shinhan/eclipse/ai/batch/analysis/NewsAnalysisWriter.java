package com.shinhan.eclipse.ai.batch.analysis;

import com.shinhan.eclipse.ai.domain.analysis.IpoNewsAnalysis;
import com.shinhan.eclipse.ai.domain.analysis.IpoNewsAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class NewsAnalysisWriter implements ItemWriter<AnalysisResult> {

    private final IpoNewsAnalysisRepository analysisRepository;

    @Override
    public void write(Chunk<? extends AnalysisResult> chunk) {
        for (AnalysisResult result : chunk.getItems()) {
            IpoNewsAnalysis analysis = result.getAnalysis();
            // UPSERT: 기존 레코드 있으면 삭제 후 저장
            analysisRepository.findByIpoId(analysis.getIpoId()).ifPresent(existing -> {
                analysisRepository.delete(existing);
                analysisRepository.flush();
            });
            analysisRepository.save(analysis);
            log.info("분석 저장: ipoId={}, status={}", analysis.getIpoId(), analysis.getAnalysisStatus());
        }
    }
}
