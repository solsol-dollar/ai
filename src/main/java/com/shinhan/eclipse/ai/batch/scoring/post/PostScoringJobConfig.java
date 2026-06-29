package com.shinhan.eclipse.ai.batch.scoring.post;

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
public class PostScoringJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PostScoringReader reader;
    private final PostScoringProcessor processor;
    private final PostScoringWriter writer;

    @Bean
    public Job postScoringJob() {
        return new JobBuilder("postScoringJob", jobRepository)
                .start(postScoreIpoStep())
                .build();
    }

    @Bean
    public Step postScoreIpoStep() {
        return new StepBuilder("postScoreIpoStep", jobRepository)
                .<Long, IpoScore>chunk(1, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
