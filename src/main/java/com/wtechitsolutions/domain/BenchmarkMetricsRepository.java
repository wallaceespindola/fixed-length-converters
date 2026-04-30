package com.wtechitsolutions.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BenchmarkMetricsRepository extends JpaRepository<BenchmarkMetrics, Long> {
    List<BenchmarkMetrics> findTop50ByOrderByTimestampDesc();
}
