package com.wtechitsolutions.api.dto;

import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;

import java.time.Instant;

public record BatchJobResponse(
        Long jobExecutionId,
        FileType fileType,
        Library library,
        String status,
        String fileContent,
        String fileName,
        Instant timestamp) {
}
