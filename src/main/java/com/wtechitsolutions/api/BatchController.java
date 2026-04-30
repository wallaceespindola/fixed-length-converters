package com.wtechitsolutions.api;

import com.wtechitsolutions.api.dto.BatchHistoryResponse;
import com.wtechitsolutions.api.dto.BatchJobRequest;
import com.wtechitsolutions.api.dto.BatchJobResponse;
import com.wtechitsolutions.batch.BatchJobService;
import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/batch")
@Tag(name = "Batch", description = "Spring Batch job management and history")
public class BatchController {

    private static final Logger log = LoggerFactory.getLogger(BatchController.class);

    private final BatchJobService batchJobService;

    public BatchController(BatchJobService batchJobService) {
        this.batchJobService = batchJobService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Trigger a Spring Batch file generation job")
    public ResponseEntity<BatchJobResponse> generate(@RequestBody @Valid BatchJobRequest request) {
        log.info("Triggering batch job: fileType={}, library={}", request.fileType(), request.library());
        BatchJobService.BatchJobResult result = batchJobService.launch(request.fileType(), request.library());
        return ResponseEntity.ok(new BatchJobResponse(
                result.jobExecutionId(),
                request.fileType(),
                request.library(),
                result.status(),
                result.fileContent(),
                result.fileName(),
                Instant.now()));
    }

    @GetMapping("/history")
    @Operation(summary = "Retrieve the last 50 batch job execution records")
    public ResponseEntity<List<BatchHistoryResponse>> history() {
        List<BatchHistoryResponse> history = batchJobService.getHistory().stream()
                .map(this::toHistoryResponse)
                .toList();
        return ResponseEntity.ok(history);
    }

    private BatchHistoryResponse toHistoryResponse(JobExecution ex) {
        JobParameters params = ex.getJobParameters();
        FileType fileType = safeFileType(params.getString("fileType", "CODA"));
        Library library = safeLibrary(params.getString("library", "BEANIO"));

        LocalDateTime start = ex.getStartTime();
        LocalDateTime end = ex.getEndTime();
        long durationMs = (start != null && end != null) ? Duration.between(start, end).toMillis() : 0;

        Instant startInstant = start != null ? start.atZone(ZoneId.systemDefault()).toInstant() : null;
        Instant endInstant = end != null ? end.atZone(ZoneId.systemDefault()).toInstant() : null;

        return new BatchHistoryResponse(ex.getId(), fileType, library,
                ex.getStatus().name(), durationMs, startInstant, endInstant);
    }

    private static FileType safeFileType(String s) {
        try { return FileType.valueOf(s); } catch (Exception e) { return FileType.CODA; }
    }

    private static Library safeLibrary(String s) {
        try { return Library.valueOf(s); } catch (Exception e) { return Library.BEANIO; }
    }
}
