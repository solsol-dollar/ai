package com.shinhan.eclipse.ai.batch.translation;

import com.shinhan.eclipse.ai.domain.ipo.IpoNews;

public record TranslationItem(
        IpoNews news,
        String titleKo,
        String summaryKo
) {
    public static TranslationItem of(IpoNews news, String titleKo, String summaryKo) {
        return new TranslationItem(news, titleKo, summaryKo);
    }
}
