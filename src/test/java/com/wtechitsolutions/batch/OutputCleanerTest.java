package com.wtechitsolutions.batch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputCleanerTest {

    private final OutputCleaner cleaner = new OutputCleaner();

    @Test
    void clean_deletes_txt_files_and_keeps_others() throws IOException {
        Path outputDir = Path.of("output");
        Files.createDirectories(outputDir);
        Path txt = Files.writeString(outputDir.resolve("CODA_BEANIO_test.txt"), "x");
        Path other = Files.writeString(outputDir.resolve("keep.log"), "x");

        try {
            long deleted = cleaner.clean();

            assertTrue(deleted >= 1);
            assertFalse(Files.exists(txt));
            assertTrue(Files.exists(other));
        } finally {
            Files.deleteIfExists(txt);
            Files.deleteIfExists(other);
        }
    }

}
