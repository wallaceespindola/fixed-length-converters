package com.wtechitsolutions.api;

import com.wtechitsolutions.api.dto.BenchmarkResultResponse;
import com.wtechitsolutions.benchmark.BenchmarkService;
import com.wtechitsolutions.domain.BenchmarkMetrics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
@Tag(name = "Benchmark", description = "Benchmark metrics and export")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    @GetMapping("/results")
    @Operation(summary = "Retrieve the last 50 benchmark metric results")
    public ResponseEntity<List<BenchmarkResultResponse>> results() {
        return ResponseEntity.ok(
                benchmarkService.getAll().stream().map(this::toResponse).toList());
    }

    @GetMapping("/export/csv")
    @Operation(summary = "Export all benchmark results as CSV")
    public ResponseEntity<String> exportCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"benchmark.csv\"")
                .body(benchmarkService.exportAsCsv());
    }

    @GetMapping("/export/markdown")
    @Operation(summary = "Export all benchmark results as Markdown")
    public ResponseEntity<String> exportMarkdown() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/markdown")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"benchmark.md\"")
                .body(benchmarkService.exportAsMarkdown());
    }

    @GetMapping("/export/json")
    @Operation(summary = "Export all benchmark results as JSON")
    public ResponseEntity<String> exportJson() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(benchmarkService.exportAsJson());
    }

    private BenchmarkResultResponse toResponse(BenchmarkMetrics m) {
        return new BenchmarkResultResponse(
                m.getId(),
                m.getJobExecutionId(),
                m.getFileType(),
                m.getLibrary(),
                m.getThroughputRps() != null ? m.getThroughputRps() : 0.0,
                m.getGenerationDurationMs() != null ? m.getGenerationDurationMs() : 0L,
                m.getParseDurationMs() != null ? m.getParseDurationMs() : 0L,
                m.getMemoryUsedBytes() != null ? m.getMemoryUsedBytes() : 0L,
                m.getSuccessRate() != null ? m.getSuccessRate() : 0.0,
                m.getSymmetryRate() != null ? m.getSymmetryRate() : 0.0,
                m.getTimestamp());
    }
}
