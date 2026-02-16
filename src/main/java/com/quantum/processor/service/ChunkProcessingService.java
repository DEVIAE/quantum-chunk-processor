package com.quantum.processor.service;

import com.quantum.common.dto.ChunkResult;
import com.quantum.common.model.Chunk;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ChunkProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkProcessingService.class);

    private final Timer chunkProcessingTimer;
    private final Counter linesProcessedCounter;
    private final Counter linesFailedCounter;
    private final Counter chunksProcessedCounter;
    private final LineProcessorService lineProcessorService;

    public ChunkProcessingService(MeterRegistry meterRegistry,
                                  LineProcessorService lineProcessorService) {
        this.lineProcessorService = lineProcessorService;
        this.chunkProcessingTimer = Timer.builder("chunk.processing.time")
                .description("Time to process a chunk")
                .register(meterRegistry);
        this.linesProcessedCounter = Counter.builder("lines.processed.total")
                .description("Total lines processed successfully")
                .register(meterRegistry);
        this.linesFailedCounter = Counter.builder("lines.failed.total")
                .description("Total lines failed")
                .register(meterRegistry);
        this.chunksProcessedCounter = Counter.builder("chunks.processed.total")
                .description("Total chunks processed")
                .register(meterRegistry);
    }

    public ChunkResult processChunk(Chunk chunk) {
        return chunkProcessingTimer.record(() -> {
            AtomicInteger processedCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();

            for (String line : chunk.getLines()) {
                try {
                    lineProcessorService.processLine(line, chunk.getFileName());
                    processedCount.incrementAndGet();
                    linesProcessedCounter.increment();
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                    linesFailedCounter.increment();
                    log.warn("Failed to process line in chunk {}: {}",
                            chunk.getChunkId(), e.getMessage());
                }
            }

            chunksProcessedCounter.increment();
            long processingTimeMs = System.currentTimeMillis() - startTime;

            return ChunkResult.success(
                    chunk.getChunkId(),
                    chunk.getFileName(),
                    processedCount.get(),
                    processingTimeMs
            );
        });
    }
}
