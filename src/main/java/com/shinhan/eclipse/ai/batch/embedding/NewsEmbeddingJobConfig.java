package com.shinhan.eclipse.ai.batch.embedding;

import com.shinhan.eclipse.ai.domain.ipo.IpoNews;
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
public class NewsEmbeddingJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final IpoNewsEmbeddingReader reader;
    private final NewsEmbeddingProcessor processor;
    private final PgVectorEmbeddingWriter writer;

    @Bean
    public Job newsEmbeddingJob() {
        return new JobBuilder("newsEmbeddingJob", jobRepository)
                .start(embedNewsStep())
                .build();
    }

    @Bean
    public Step embedNewsStep() {
        return new StepBuilder("embedNewsStep", jobRepository)
                .<IpoNews, EmbeddingItem>chunk(10, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
