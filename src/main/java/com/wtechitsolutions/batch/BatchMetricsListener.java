package com.wtechitsolutions.batch;

import com.wtechitsolutions.domain.BenchmarkMetrics;
import com.wtechitsolutions.domain.BenchmarkMetricsRepository;
import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchMetricsListener implements JobExecutionListener {

    private final BenchmarkMetricsRepository metricsRepository;

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            JobParameters params = jobExecution.getJobParameters();
            FileType fileType = FileType.valueOf(params.getString("fileType", "CODA"));
            Library library = Library.valueOf(params.getString("library", "BEANIO"));

            LocalDateTime start = jobExecution.getStartTime();
            LocalDateTime end = jobExecution.getEndTime();
            long durationMs = (start != null && end != null)
                    ? Duration.between(start, end).toMillis() : 0;

            long records = jobExecution.getStepExecutions().stream()
                    .mapToLong(StepExecution::getWriteCount).sum();
            double throughput = durationMs > 0 ? (records * 1000.0 / durationMs) : 0;

            BenchmarkMetrics metrics = BenchmarkMetrics.builder()
                    .jobExecutionId(jobExecution.getId())
                    .library(library)
                    .fileType(fileType)
                    .throughputRps(throughput)
                    .batchDurationMs(durationMs)
                    .recordsProcessed(records)
                    .successRate(BatchStatus.COMPLETED.equals(jobExecution.getStatus()) ? 1.0 : 0.0)
                    .failedCount(jobExecution.getStepExecutions().stream()
                            .mapToLong(StepExecution::getProcessSkipCount).sum())
                    .timestamp(Instant.now())
                    .build();

            metricsRepository.save(metrics);
            log.info("Saved benchmark metrics for job={}, status={}, duration={}ms, records={}",
                    jobExecution.getId(), jobExecution.getStatus(), durationMs, records);
        } catch (Exception e) {
            log.error("Failed to save benchmark metrics: {}", e.getMessage());
        }
    }
}
