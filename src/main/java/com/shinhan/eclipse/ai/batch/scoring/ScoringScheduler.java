package com.shinhan.eclipse.ai.batch.scoring;

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
public class ScoringScheduler {

    private final JobLauncher jobLauncher;

    @Qualifier("scoringJob")
    private final Job scoringJob;

    @Scheduled(cron = "0 0 5 * * *")
    public void run() throws Exception {
        log.info("ScoringJob 시작");
        jobLauncher.run(scoringJob,
            new JobParametersBuilder().addLong("runAt", System.currentTimeMillis()).toJobParameters());
        log.info("ScoringJob 완료");
    }
}
