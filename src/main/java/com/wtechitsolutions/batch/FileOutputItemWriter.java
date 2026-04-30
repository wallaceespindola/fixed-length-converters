package com.wtechitsolutions.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@StepScope
@Slf4j
public class FileOutputItemWriter implements ItemWriter<String> {

    private final List<String> buffer = new ArrayList<>();

    @Override
    public void write(Chunk<? extends String> chunk) {
        buffer.addAll(chunk.getItems());
    }

    @AfterStep
    public ExitStatus afterStep(StepExecution stepExecution) {
        String fileTypeStr = stepExecution.getJobParameters().getString("fileType", "CODA");
        String libraryStr = stepExecution.getJobParameters().getString("library", "BEANIO");
        String runTimestamp = stepExecution.getJobParameters().getString("runTimestamp", "ts");
        String content = String.join("\n", buffer);
        String fileName = fileTypeStr + "_" + libraryStr + "_" + runTimestamp + ".txt";

        try {
            Path outputDir = Path.of("output");
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve(fileName), content, StandardCharsets.UTF_8);
            stepExecution.getExecutionContext().putString("fileContent", content);
            stepExecution.getExecutionContext().putString("fileName", fileName);
            log.info("Output file written: {}", fileName);
            return ExitStatus.COMPLETED;
        } catch (IOException e) {
            log.error("Failed to write output file {}: {}", fileName, e.getMessage());
            return ExitStatus.FAILED;
        }
    }
}
