package com.wtechitsolutions.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChunkTimingListener implements ChunkListener {

    private long chunkStart;

    @Override
    public void beforeChunk(ChunkContext context) {
        chunkStart = System.currentTimeMillis();
    }

    @Override
    public void afterChunk(ChunkContext context) {
        long duration = System.currentTimeMillis() - chunkStart;
        context.getStepContext().getStepExecution()
                .getExecutionContext().putLong("lastChunkDurationMs", duration);
        log.debug("Chunk completed in {}ms", duration);
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        log.warn("Chunk error after {}ms", System.currentTimeMillis() - chunkStart);
    }
}
