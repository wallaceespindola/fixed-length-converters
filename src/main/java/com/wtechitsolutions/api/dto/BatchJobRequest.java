package com.wtechitsolutions.api.dto;

import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;
import jakarta.validation.constraints.NotNull;

public record BatchJobRequest(
        @NotNull FileType fileType,
        @NotNull Library library) {
}
