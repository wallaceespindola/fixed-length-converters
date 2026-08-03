package com.wtechitsolutions.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Removes generated banking files from the {@code output/} directory. Runs automatically on
 * application startup so every run begins with a clean slate, and on demand from the
 * Clear Database endpoint.
 */
@Component
public class OutputCleaner {

    private static final Logger log = LoggerFactory.getLogger(OutputCleaner.class);
    private static final Path OUTPUT_DIR = Path.of("output");

    @EventListener(ApplicationReadyEvent.class)
    public void cleanOnStartup() {
        long deleted = clean();
        log.info("Startup cleanup: removed {} generated files from {}", deleted, OUTPUT_DIR.toAbsolutePath());
    }

    /**
     * Deletes every generated {@code .txt} file in the output directory.
     *
     * @return number of files deleted
     */
    public long clean() {
        if (!Files.isDirectory(OUTPUT_DIR)) {
            return 0;
        }
        long deleted = 0;
        try (Stream<Path> files = Files.list(OUTPUT_DIR)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".txt")).toList()) {
                try {
                    Files.delete(file);
                    deleted++;
                } catch (IOException e) {
                    log.warn("Could not delete output file {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to list output directory {}: {}", OUTPUT_DIR, e.getMessage());
        }
        return deleted;
    }
}
