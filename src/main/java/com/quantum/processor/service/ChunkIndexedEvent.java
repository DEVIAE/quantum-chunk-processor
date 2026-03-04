package com.quantum.processor.service;

import com.quantum.common.dto.ChunkResult;
import java.time.Instant;

public class ChunkIndexedEvent {

    private String chunkId;
    private String fileName;
    private int processedLines;
    private int failedLines;
    private long processingTimeMs;
    private Instant timestamp;

    public ChunkIndexedEvent(ChunkResult result) {
        this.chunkId = result.getChunkId();
        this.fileName = result.getFileName();
        this.processedLines = result.getProcessedLines();
        this.failedLines = result.getFailedLines();
        this.processingTimeMs = result.getProcessingTimeMs();
        this.timestamp = Instant.now();
    }

    public String getChunkId() { return chunkId; }
    public String getFileName() { return fileName; }
    public int getProcessedLines() { return processedLines; }
    public int getFailedLines() { return failedLines; }
    public long getProcessingTimeMs() { return processingTimeMs; }
    public Instant getTimestamp() { return timestamp; }
}