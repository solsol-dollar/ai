package com.shinhan.eclipse.ai.api.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
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
}
