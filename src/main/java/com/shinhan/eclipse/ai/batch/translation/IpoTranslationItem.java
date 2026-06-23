package com.shinhan.eclipse.ai.batch.translation;

public record IpoTranslationItem(
        Long ipoId,
        Long newsId1, String titleKo1,
        Long newsId2, String titleKo2,
        String summary
) {}
