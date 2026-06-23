package com.shinhan.eclipse.ai.batch.analysis;

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
public class NewsAnalysisJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EmbeddedNewsReader reader;
    private final NewsAnalysisProcessor processor;
    private final NewsAnalysisWriter writer;

    @Bean
    public Job newsAnalysisJob() {
        return new JobBuilder("newsAnalysisJob", jobRepository)
                .start(analyzeNewsStep())
                .build();
    }

    @Bean
    public Step analyzeNewsStep() {
        return new StepBuilder("analyzeNewsStep", jobRepository)
                .<Long, AnalysisResult>chunk(1, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
