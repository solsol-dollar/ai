package com.shinhan.eclipse.ai.batch.scoring;

import com.shinhan.eclipse.ai.domain.score.IpoScore;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class ScoringJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AnalyzedIpoReader reader;
    private final ScoringProcessor processor;
    private final IpoScoreWriter writer;

    @Bean
    public Job scoringJob() {
        return new JobBuilder("scoringJob", jobRepository)
                .start(scoreIpoStep())
                .build();
    }

    @Bean
    public Step scoreIpoStep() {
        return new StepBuilder("scoreIpoStep", jobRepository)
                .<Long, IpoScore>chunk(10, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
