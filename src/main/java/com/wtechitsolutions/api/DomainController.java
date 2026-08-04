package com.wtechitsolutions.api;

import com.wtechitsolutions.api.dto.GenerateDomainResponse;
import com.wtechitsolutions.api.dto.ResetDatabaseResponse;
import com.wtechitsolutions.batch.BatchJobService;
import com.wtechitsolutions.batch.OutputCleaner;
import com.wtechitsolutions.benchmark.BenchmarkService;
import com.wtechitsolutions.domain.DomainDataGenerator;
import com.wtechitsolutions.domain.LoadProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/domain")
@Tag(name = "Domain", description = "Banking domain data generation")
public class DomainController {

    private static final Logger log = LoggerFactory.getLogger(DomainController.class);

    private final DomainDataGenerator generator;
    private final BenchmarkService benchmarkService;
    private final OutputCleaner outputCleaner;
    private final BatchJobService batchJobService;

    public DomainController(DomainDataGenerator generator, BenchmarkService benchmarkService,
                            OutputCleaner outputCleaner, BatchJobService batchJobService) {
        this.generator = generator;
        this.benchmarkService = benchmarkService;
        this.outputCleaner = outputCleaner;
        this.batchJobService = batchJobService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate sample banking domain data and persist to H2",
            description = "Pass loadProfile=LOW (10 accounts/100 txns), MEDIUM (100 accounts/1000 txns), or HIGH (1000 accounts/10000 txns). Defaults to LOW.")
    public ResponseEntity<GenerateDomainResponse> generate(
            @RequestParam(name = "loadProfile", required = false, defaultValue = "LOW") LoadProfile loadProfile) {
        log.info("Generating banking domain data, profile={}", loadProfile);
        DomainDataGenerator.GenerationResult result = generator.generate(loadProfile);
        return ResponseEntity.ok(new GenerateDomainResponse(
                result.operationId(),
                result.accounts(),
                result.transactions(),
                Instant.now()));
    }

    @DeleteMapping("/reset")
    @Operation(summary = "Delete all generated domain data, benchmark results, batch history and output files",
            description = "Wipes accounts, transactions, statements, stored benchmark metrics, Spring Batch "
                    + "job-execution metadata (Batch History) and the generated files in the output/ "
                    + "directory so a new benchmark can start from a clean slate.")
    public ResponseEntity<ResetDatabaseResponse> reset() {
        log.info("Resetting database: deleting domain data, benchmark results, batch history and output files");
        DomainDataGenerator.ResetResult domain = generator.reset();
        long benchmarks = benchmarkService.deleteAll();
        long jobExecutions = batchJobService.clearJobMetadata();
        long files = outputCleaner.clean();
        return ResponseEntity.ok(new ResetDatabaseResponse(
                domain.accounts(),
                domain.transactions(),
                domain.statements(),
                benchmarks,
                jobExecutions,
                files,
                Instant.now()));
    }
}
