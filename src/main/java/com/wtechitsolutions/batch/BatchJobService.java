package com.wtechitsolutions.batch;

import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BatchJobService {

    private static final Logger log = LoggerFactory.getLogger(BatchJobService.class);

    private final Job bankingFileGenerationJob;
    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;

    public BatchJobService(Job bankingFileGenerationJob, JobLauncher jobLauncher, JobExplorer jobExplorer) {
        this.bankingFileGenerationJob = bankingFileGenerationJob;
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
    }

    public BatchJobResult launch(FileType fileType, Library library) {
        String runTimestamp = Instant.now().toString().replace(":", "-").replace(".", "-");

        JobParameters params = new JobParametersBuilder()
                .addString("fileType", fileType.name())
                .addString("library", library.name())
                .addLong("operationId", System.currentTimeMillis())
                .addString("runTimestamp", runTimestamp)
                .toJobParameters();

        try {
            JobExecution execution = jobLauncher.run(bankingFileGenerationJob, params);
            log.info("Launched job: id={}, status={}", execution.getId(), execution.getStatus());
            String fileContent = extractContext(execution, "fileContent");
            String fileName = extractContext(execution, "fileName");
            return new BatchJobResult(execution.getId(), execution.getStatus().name(), fileContent, fileName);
        } catch (Exception e) {
            log.error("Failed to launch batch job: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to launch batch job: " + e.getMessage(), e);
        }
    }

    public List<JobExecution> getHistory() {
        return jobExplorer.findJobInstancesByJobName("bankingFileGenerationJob", 0, 50)
                .stream()
                .flatMap(ji -> jobExplorer.getJobExecutions(ji).stream())
                .sorted(Comparator.comparing(
                        (JobExecution ex) -> ex.getStartTime() != null ? ex.getStartTime() : LocalDateTime.MIN,
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    private static String extractContext(JobExecution execution, String key) {
        return execution.getStepExecutions().stream()
                .findFirst()
                .map(s -> s.getExecutionContext().getString(key, ""))
                .orElse("");
    }

    public record BatchJobResult(Long jobExecutionId, String status, String fileContent, String fileName) {}
}
