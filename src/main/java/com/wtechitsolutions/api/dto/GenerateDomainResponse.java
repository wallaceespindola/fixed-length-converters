package com.wtechitsolutions.api.dto;

import java.time.Instant;

public record GenerateDomainResponse(
        Long operationId,
        int accountsGenerated,
        int transactionsGenerated,
        Instant timestamp) {
}
