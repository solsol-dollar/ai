package com.shinhan.eclipse.ai.sqs;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class IpoEventConsumer {

    private final JobLauncher jobLauncher;

    @Qualifier("newsEmbeddingJob")
    private final Job newsEmbeddingJob;

    @Qualifier("newsAnalysisJob")
    private final Job newsAnalysisJob;

    @Qualifier("scoringJob")
    private final Job scoringJob;

    @Qualifier("translationJob")
    private final Job translationJob;

    @SqsListener("${sqs.queue-url}")
    public void consume(IpoEventMessage message) throws Exception {
        log.info("SQS 이벤트 수신: eventType={}, ipoId={}, ticker={}",
                message.eventType(), message.ipoId(), message.ticker());
        runPipeline(message.ipoId());
        log.info("파이프라인 완료: ipoId={}", message.ipoId());
    }

    private void runPipeline(Long ipoId) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("ipoId", ipoId)
                .addLong("runAt", System.currentTimeMillis())
                .toJobParameters();

        log.info("[{}] 임베딩 시작", ipoId);
        jobLauncher.run(newsEmbeddingJob, params);

        log.info("[{}] 분석 시작", ipoId);
        jobLauncher.run(newsAnalysisJob, params);

        log.info("[{}] 스코어링 시작", ipoId);
        jobLauncher.run(scoringJob, params);

        log.info("[{}] 번역 시작", ipoId);
        jobLauncher.run(translationJob, params);
    }
}
