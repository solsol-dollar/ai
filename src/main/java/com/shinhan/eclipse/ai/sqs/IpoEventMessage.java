package com.shinhan.eclipse.ai.sqs;

public record IpoEventMessage(
        String eventType,
        Long ipoId,
        String ticker
) {}
