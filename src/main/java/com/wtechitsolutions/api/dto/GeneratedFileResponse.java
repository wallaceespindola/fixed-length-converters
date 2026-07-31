package com.wtechitsolutions.api.dto;

import java.time.Instant;

/** One generated banking file in the {@code output/} directory. */
public record GeneratedFileResponse(
        String fileName,
        long sizeBytes,
        Instant modifiedAt) {
}
