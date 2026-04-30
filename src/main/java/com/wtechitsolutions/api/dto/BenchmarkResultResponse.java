package com.wtechitsolutions.api.dto;

import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;

import java.time.Instant;

public record BenchmarkResultResponse(
        Long id,
        Long jobExecutionId,
        FileType fileType,
        Library library,
        double throughputRps,
        long generationDurationMs,
        long parseDurationMs,
        long memoryUsedBytes,
        double successRate,
        double symmetryRate,
        Instant timestamp) {
}
