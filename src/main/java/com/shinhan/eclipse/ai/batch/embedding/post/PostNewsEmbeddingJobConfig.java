package com.shinhan.eclipse.ai.batch.embedding.post;

import com.shinhan.eclipse.ai.batch.embedding.EmbeddingItem;
import com.shinhan.eclipse.ai.batch.embedding.PgVectorEmbeddingWriter;
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
public class PostNewsEmbeddingJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PostNewsEmbeddingReader reader;
    private final PostNewsEmbeddingProcessor processor;
    private final PgVectorEmbeddingWriter writer;

    @Bean
    public Job postNewsEmbeddingJob() {
        return new JobBuilder("postNewsEmbeddingJob", jobRepository)
                .start(embedPostNewsStep())
                .build();
    }

    @Bean
    public Step embedPostNewsStep() {
        return new StepBuilder("embedPostNewsStep", jobRepository)
                .<IpoNews, EmbeddingItem>chunk(10, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
