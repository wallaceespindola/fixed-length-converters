package com.wtechitsolutions.api.dto;

import java.time.Instant;

/**
 * Result of wiping the generated data so a benchmark can start from a clean database.
 * Spring Batch's own job-execution metadata is not touched — only the rows this application creates.
 */
public record ResetDatabaseResponse(
        long accountsDeleted,
        long transactionsDeleted,
        long statementsDeleted,
        long benchmarkResultsDeleted,
        Instant timestamp) {
}
