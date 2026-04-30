package com.wtechitsolutions.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wtechitsolutions.domain.BenchmarkMetrics;
import com.wtechitsolutions.domain.BenchmarkMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BenchmarkService {

    private final BenchmarkMetricsRepository repository;

    public List<BenchmarkMetrics> getAll() {
        return repository.findTop50ByOrderByTimestampDesc();
    }

    public String exportAsCsv() {
        List<BenchmarkMetrics> metrics = repository.findAll();
        StringBuilder csv = new StringBuilder(
                "id,jobExecutionId,fileType,library,throughputRps,batchDurationMs,recordsProcessed,successRate,timestamp\n");
        for (BenchmarkMetrics m : metrics) {
            csv.append(String.join(",",
                    str(m.getId()),
                    str(m.getJobExecutionId()),
                    str(m.getFileType()),
                    str(m.getLibrary()),
                    str(m.getThroughputRps()),
                    str(m.getBatchDurationMs()),
                    str(m.getRecordsProcessed()),
                    str(m.getSuccessRate()),
                    str(m.getTimestamp())
            )).append("\n");
        }
        return csv.toString();
    }

    public String exportAsMarkdown() {
        List<BenchmarkMetrics> metrics = repository.findAll();
        StringBuilder md = new StringBuilder(
                "| ID | FileType | Library | Throughput RPS | Duration Ms | Records | Success Rate | Timestamp |\n");
        md.append("|---|---|---|---|---|---|---|---|\n");
        for (BenchmarkMetrics m : metrics) {
            md.append(String.format("| %s | %s | %s | %.2f | %s | %s | %.2f | %s |\n",
                    str(m.getId()), str(m.getFileType()), str(m.getLibrary()),
                    m.getThroughputRps() != null ? m.getThroughputRps() : 0.0,
                    str(m.getBatchDurationMs()), str(m.getRecordsProcessed()),
                    m.getSuccessRate() != null ? m.getSuccessRate() : 0.0,
                    str(m.getTimestamp())));
        }
        return md.toString();
    }

    public String exportAsJson() {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(repository.findAll());
        } catch (Exception e) {
            log.error("Failed to serialize benchmark metrics as JSON", e);
            return "[]";
        }
    }

    private static String str(Object o) {
        return o != null ? o.toString() : "";
    }
}
