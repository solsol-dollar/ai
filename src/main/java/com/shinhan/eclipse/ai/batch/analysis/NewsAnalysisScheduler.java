package com.shinhan.eclipse.ai.batch.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsAnalysisScheduler {

    private final JobLauncher jobLauncher;

    @Qualifier("newsAnalysisJob")
    private final Job newsAnalysisJob;

    @Scheduled(cron = "0 30 3 * * *")
    public void run() throws Exception {
        log.info("NewsAnalysisJob 시작");
        jobLauncher.run(newsAnalysisJob,
            new JobParametersBuilder().addLong("runAt", System.currentTimeMillis()).toJobParameters());
        log.info("NewsAnalysisJob 완료");
    }
}
