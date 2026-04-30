package com.wtechitsolutions.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "benchmark_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long jobExecutionId;

    @Enumerated(EnumType.STRING)
    private Library library;

    @Enumerated(EnumType.STRING)
    private FileType fileType;

    private Double throughputRps;
    private Long generationDurationMs;
    private Long parseDurationMs;
    private Long memoryUsedBytes;
    private Long batchDurationMs;
    private Long chunkDurationMs;
    private Double cpuUsagePct;
    private Double successRate;
    private Long failedCount;
    private Double symmetryRate;
    private Long recordsProcessed;
    private Instant timestamp;
}
