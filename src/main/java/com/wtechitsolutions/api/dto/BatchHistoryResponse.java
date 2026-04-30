package com.wtechitsolutions.api.dto;

import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;

import java.time.Instant;

public record BatchHistoryResponse(
        Long jobExecutionId,
        FileType fileType,
        Library library,
        String status,
        long durationMs,
        Instant startTime,
        Instant endTime) {
}
