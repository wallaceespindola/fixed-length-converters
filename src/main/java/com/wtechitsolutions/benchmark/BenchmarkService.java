package com.wtechitsolutions.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wtechitsolutions.domain.BenchmarkMetrics;
import com.wtechitsolutions.domain.BenchmarkMetricsRepository;
import jakarta.annotation.PostConstruct;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

@Service
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);
    private static final String HTML_TEMPLATE = "velocity/benchmark-report.vm";

    private final BenchmarkMetricsRepository repository;
    private VelocityEngine velocityEngine;

    public BenchmarkService(BenchmarkMetricsRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void initVelocity() {
        velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        velocityEngine.setProperty("resource.loader.classpath.class",
                ClasspathResourceLoader.class.getName());
        velocityEngine.init();
    }

    public List<BenchmarkMetrics> getAll() {
        return repository.findTop50ByOrderByTimestampDesc();
    }

    public String exportAsCsv() {
        List<BenchmarkMetrics> metrics = repository.findAll();
        StringBuilder csv = new StringBuilder(
                "id,jobExecutionId,fileType,library,throughputRps,batchDurationMs,generationDurationMs,parseDurationMs,recordsProcessed,successRate,timestamp\n");
        for (BenchmarkMetrics m : metrics) {
            csv.append(String.join(",",
                    str(m.getId()), str(m.getJobExecutionId()), str(m.getFileType()), str(m.getLibrary()),
                    str(m.getThroughputRps()), str(m.getBatchDurationMs()),
                    str(m.getGenerationDurationMs()), str(m.getParseDurationMs()),
                    str(m.getRecordsProcessed()), str(m.getSuccessRate()), str(m.getTimestamp())
            )).append("\n");
        }
        return csv.toString();
    }

    public String exportAsMarkdown() {
        List<BenchmarkMetrics> metrics = repository.findAll();
        StringBuilder md = new StringBuilder(
                "| ID | FileType | Library | Throughput (ops/s) | Batch Duration (ms) | Gen Duration (ms) | Records | Success Rate | Timestamp |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");
        for (BenchmarkMetrics m : metrics) {
            md.append(String.format("| %s | %s | %s | %.2f | %s | %s | %s | %.2f | %s |\n",
                    str(m.getId()), str(m.getFileType()), str(m.getLibrary()),
                    m.getThroughputRps() != null ? m.getThroughputRps() : 0.0,
                    str(m.getBatchDurationMs()), str(m.getGenerationDurationMs()),
                    str(m.getRecordsProcessed()),
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

    public String exportAsHtml() {
        try {
            List<BenchmarkMetrics> metrics = repository.findAll();
            VelocityContext context = new VelocityContext();
            context.put("metrics", metrics);
            context.put("count", metrics.size());
            context.put("generatedAt", Instant.now().toString());
            StringWriter sw = new StringWriter();
            velocityEngine.getTemplate(HTML_TEMPLATE).merge(context, sw);
            return sw.toString();
        } catch (Exception e) {
            log.error("Failed to render benchmark HTML report", e);
            return "<html><body><h1>Benchmark Report</h1><p>Rendering failed: "
                    + e.getMessage() + "</p></body></html>";
        }
    }

    private static String str(Object o) {
        return o != null ? o.toString() : "";
    }
}
