package com.shinhan.eclipse.ai.api.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobLauncher jobLauncher;
    private final ApplicationContext context;

    @Qualifier("newsEmbeddingJob")
    private final Job newsEmbeddingJob;

    @Qualifier("newsAnalysisJob")
    private final Job newsAnalysisJob;

    @Qualifier("scoringJob")
    private final Job scoringJob;

    @Qualifier("postNewsEmbeddingJob")
    private final Job postNewsEmbeddingJob;

    @Qualifier("postScoringJob")
    private final Job postScoringJob;

    @Qualifier("translationJob")
    private final Job translationJob;

    @Qualifier("postTranslationJob")
    private final Job postTranslationJob;

    @PostMapping("/{jobName}/run")
    public ResponseEntity<Map<String, String>> run(@PathVariable String jobName) {
        try {
            Job job = context.getBean(jobName, Job.class);
            jobLauncher.run(job,
                new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters());
            log.info("잡 수동 실행: {}", jobName);
            return ResponseEntity.ok(Map.of("status", "started", "job", jobName));
        } catch (Exception e) {
            log.error("잡 실행 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/pipeline/run")
    public ResponseEntity<Map<String, String>> runPipeline() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters();

            log.info("전체 파이프라인 수동 트리거");
            jobLauncher.run(newsEmbeddingJob, params);
            jobLauncher.run(newsAnalysisJob, params);
            jobLauncher.run(scoringJob, params);
            jobLauncher.run(postNewsEmbeddingJob, params);
            jobLauncher.run(postScoringJob, params);
            jobLauncher.run(translationJob, params);
            jobLauncher.run(postTranslationJob, params);
            log.info("전체 파이프라인 완료");

            return ResponseEntity.ok(Map.of("status", "completed"));
        } catch (Exception e) {
            log.error("파이프라인 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
