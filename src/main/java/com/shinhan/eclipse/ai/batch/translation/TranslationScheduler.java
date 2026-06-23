package com.shinhan.eclipse.ai.batch.translation;

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
public class TranslationScheduler {

    private final JobLauncher jobLauncher;
    @Qualifier("translationJob")
    private final Job translationJob;

    @Scheduled(cron = "0 30 5 * * *")
    public void run() {
        try {
            jobLauncher.run(translationJob, new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters());
        } catch (Exception e) {
            log.error("TranslationJob 실행 실패: {}", e.getMessage(), e);
        }
    }
}
