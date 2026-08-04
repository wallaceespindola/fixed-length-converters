package com.wtechitsolutions.batch;

import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.strategy.FileGenerationStrategy;
import com.wtechitsolutions.strategy.StrategyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchJobServiceTest {

    @Mock Job job;
    @Mock JobLauncher jobLauncher;
    @Mock JobExplorer jobExplorer;
    @Mock JobRepository jobRepository;
    @Mock StrategyResolver strategyResolver;
    @Mock FileGenerationStrategy strategy;

    BatchJobService service;

    @BeforeEach
    void setUp() {
        service = new BatchJobService(job, jobLauncher, jobExplorer, jobRepository, strategyResolver);
    }

    @Test
    void launch_runs_discarded_warmup_conversion_before_the_measured_job() throws Exception {
        when(strategyResolver.resolve(FileType.CODA, Library.BEANIO)).thenReturn(strategy);
        when(strategy.generate(anyList(), anyList())).thenReturn("warm");
        when(jobLauncher.run(any(Job.class), any(JobParameters.class)))
                .thenReturn(new JobExecution(1L, new JobParameters()));

        service.launch(FileType.CODA, Library.BEANIO);

        InOrder order = inOrder(strategy, jobLauncher);
        order.verify(strategy).generate(anyList(), anyList());
        order.verify(jobLauncher).run(any(Job.class), any(JobParameters.class));
    }

    @Test
    void launch_proceeds_when_warmup_fails() throws Exception {
        when(strategyResolver.resolve(FileType.SWIFT, Library.BINDY)).thenReturn(strategy);
        when(strategy.generate(anyList(), anyList())).thenThrow(new IllegalStateException("boom"));
        when(jobLauncher.run(any(Job.class), any(JobParameters.class)))
                .thenReturn(new JobExecution(2L, new JobParameters()));

        BatchJobService.BatchJobResult result = service.launch(FileType.SWIFT, Library.BINDY);

        assertEquals(2L, result.jobExecutionId());
    }
}
