package com.shinhan.eclipse.ai.batch.translation;

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
public class TranslationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final IpoTranslationReader translationReader;
    private final IpoTranslationProcessor translationProcessor;
    private final IpoTranslationWriter translationWriter;

    @Bean
    public Job translationJob() {
        return new JobBuilder("translationJob", jobRepository)
                .start(translationStep())
                .build();
    }

    @Bean
    public Step translationStep() {
        return new StepBuilder("translationStep", jobRepository)
                .<IpoScore, IpoTranslationItem>chunk(5, transactionManager)
                .reader(translationReader)
                .processor(translationProcessor)
                .writer(translationWriter)
                .build();
    }
}
