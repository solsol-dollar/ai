package com.shinhan.eclipse.ai.batch.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsEmbeddingScheduler {

    private final JobLauncher jobLauncher;

    @Qualifier("newsEmbeddingJob")
    private final Job newsEmbeddingJob;

    @Scheduled(cron = "0 30 1 * * *")   // 매일 새벽 1:30 (FinnhubSync 이후)
    public void run() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("runAt", System.currentTimeMillis())
                .toJobParameters();
        log.info("NewsEmbeddingJob 시작");
        jobLauncher.run(newsEmbeddingJob, params);
        log.info("NewsEmbeddingJob 완료");
    }
}
