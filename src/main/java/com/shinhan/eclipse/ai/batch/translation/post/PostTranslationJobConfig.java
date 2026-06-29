package com.shinhan.eclipse.ai.batch.translation.post;

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
public class PostTranslationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PostTranslationReader reader;
    private final PostTranslationProcessor processor;
    private final PostTranslationWriter writer;

    @Bean
    public Job postTranslationJob() {
        return new JobBuilder("postTranslationJob", jobRepository)
                .start(postTranslationStep())
                .build();
    }

    @Bean
    public Step postTranslationStep() {
        return new StepBuilder("postTranslationStep", jobRepository)
                .<IpoScore, IpoScore>chunk(1, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
