package com.shinhan.eclipse.ai.batch.analysis;

import com.shinhan.eclipse.ai.domain.analysis.IpoNewsAnalysis;
import lombok.Getter;

@Getter
public class AnalysisResult {
    private final Long ipoId;
    private final IpoNewsAnalysis analysis;

    private AnalysisResult(Long ipoId, IpoNewsAnalysis analysis) {
        this.ipoId = ipoId;
        this.analysis = analysis;
    }

    public static AnalysisResult of(Long ipoId, IpoNewsAnalysis analysis) {
        return new AnalysisResult(ipoId, analysis);
    }
}
