package com.wtechitsolutions.api.dto;

import java.time.Instant;

/**
 * Result of wiping the generated data so a benchmark can start from a clean slate:
 * domain rows, benchmark metrics, Spring Batch job metadata and generated output files.
 */
public record ResetDatabaseResponse(
        long accountsDeleted,
        long transactionsDeleted,
        long statementsDeleted,
        long benchmarkResultsDeleted,
        long jobExecutionsDeleted,
        long filesDeleted,
        Instant timestamp) {
}
