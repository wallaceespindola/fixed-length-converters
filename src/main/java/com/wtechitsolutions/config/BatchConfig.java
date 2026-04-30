package com.wtechitsolutions.config;

import com.wtechitsolutions.batch.BatchMetricsListener;
import com.wtechitsolutions.batch.ChunkTimingListener;
import com.wtechitsolutions.batch.DomainEntityItemReader;
import com.wtechitsolutions.batch.FileGenerationItemProcessor;
import com.wtechitsolutions.batch.FileOutputItemWriter;
import com.wtechitsolutions.domain.Transaction;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch job and step configuration.
 * No @EnableBatchProcessing — Spring Boot 3.x auto-configures the batch infrastructure.
 */
@Configuration
public class BatchConfig {

    private final DomainEntityItemReader itemReader;
    private final FileGenerationItemProcessor itemProcessor;
    private final FileOutputItemWriter itemWriter;
    private final BatchMetricsListener metricsListener;
    private final ChunkTimingListener chunkTimingListener;

    public BatchConfig(DomainEntityItemReader itemReader,
                       FileGenerationItemProcessor itemProcessor,
                       FileOutputItemWriter itemWriter,
                       BatchMetricsListener metricsListener,
                       ChunkTimingListener chunkTimingListener) {
        this.itemReader = itemReader;
        this.itemProcessor = itemProcessor;
        this.itemWriter = itemWriter;
        this.metricsListener = metricsListener;
        this.chunkTimingListener = chunkTimingListener;
    }

    @Bean
    public Job bankingFileGenerationJob(JobRepository jobRepository, Step fileGenerationStep) {
        return new JobBuilder("bankingFileGenerationJob", jobRepository)
                .listener(metricsListener)
                .start(fileGenerationStep)
                .build();
    }

    @Bean
    public Step fileGenerationStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder("fileGenerationStep", jobRepository)
                .<Transaction, String>chunk(100, txManager)
                .reader(itemReader)
                .processor(itemProcessor)
                .writer(itemWriter)
                .listener(chunkTimingListener)
                .build();
    }
}
