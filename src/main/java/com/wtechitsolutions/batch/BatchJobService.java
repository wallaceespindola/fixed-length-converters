package com.wtechitsolutions.batch;

import com.wtechitsolutions.domain.Account;
import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.domain.Transaction;
import com.wtechitsolutions.domain.TransactionType;
import com.wtechitsolutions.strategy.FileGenerationStrategy;
import com.wtechitsolutions.strategy.StrategyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    private final JobRepository jobRepository;
    private final StrategyResolver strategyResolver;

    public BatchJobService(Job bankingFileGenerationJob, JobLauncher jobLauncher, JobExplorer jobExplorer,
                           JobRepository jobRepository, StrategyResolver strategyResolver) {
        this.bankingFileGenerationJob = bankingFileGenerationJob;
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.jobRepository = jobRepository;
        this.strategyResolver = strategyResolver;
    }

    public BatchJobResult launch(FileType fileType, Library library) {
        warmUp(fileType, library);
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

    /**
     * Runs one discarded conversion on synthetic data before the measured job so first-use costs
     * (classloading, library initialisation, JIT compilation of the formatter path) do not land in
     * the benchmark numbers of whichever library happens to run first.
     */
    private void warmUp(FileType fileType, Library library) {
        try {
            FileGenerationStrategy strategy = strategyResolver.resolve(fileType, library);
            Account account = Account.builder()
                    .id(0L)
                    .accountNumber("BE000000000000000000")
                    .iban("BE000000000000000000")
                    .bankCode("300")
                    .currency("EUR")
                    .balance(BigDecimal.TEN)
                    .holderName("Warm-up")
                    .createdAt(Instant.now())
                    .build();
            Transaction txn = Transaction.builder()
                    .accountId(0L)
                    .reference("REF000000000000")
                    .amount(BigDecimal.ONE)
                    .type(TransactionType.CREDIT)
                    .description("Warm-up transaction")
                    .valueDate(LocalDate.now())
                    .entryDate(LocalDate.now())
                    .createdAt(Instant.now())
                    .build();
            long t0 = System.nanoTime();
            strategy.generate(List.of(txn), List.of(account));
            log.debug("Warm-up conversion for {}_{} took {} µs (discarded)",
                    fileType, library, (System.nanoTime() - t0) / 1000);
        } catch (Exception e) {
            log.warn("Warm-up conversion for {}_{} failed (continuing): {}", fileType, library, e.getMessage());
        }
    }

    /**
     * Deletes every job instance (and its executions, steps and contexts) of the banking file
     * generation job so the Batch History view starts empty after a Clear Database.
     *
     * @return number of job executions removed
     */
    public long clearJobMetadata() {
        long executions = 0;
        List<JobInstance> instances;
        do {
            instances = jobExplorer.findJobInstancesByJobName("bankingFileGenerationJob", 0, 100);
            for (JobInstance instance : instances) {
                executions += jobExplorer.getJobExecutions(instance).size();
                jobRepository.deleteJobInstance(instance);
            }
        } while (!instances.isEmpty());
        log.info("Cleared Spring Batch metadata: {} job executions deleted", executions);
        return executions;
    }

    private static String extractContext(JobExecution execution, String key) {
        return execution.getStepExecutions().stream()
                .findFirst()
                .map(s -> s.getExecutionContext().getString(key, ""))
                .orElse("");
    }

    public record BatchJobResult(Long jobExecutionId, String status, String fileContent, String fileName) {}
}
