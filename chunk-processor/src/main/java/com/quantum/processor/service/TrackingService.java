package com.quantum.processor.service;

import com.quantum.common.dto.ChunkResult;
import com.quantum.common.model.FileProcessingRecord;
import com.quantum.common.model.ProcessingStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

    private final ConcurrentHashMap<String, FileProcessingRecord> fileRecords = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public TrackingService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void initializeTracking(String fileName, int totalChunks) {
        FileProcessingRecord record = new FileProcessingRecord(fileName, totalChunks);
        record.setStatus(ProcessingStatus.IN_PROGRESS);
        fileRecords.put(fileName, record);

        Gauge.builder("file.processing.progress", record, r ->
                        r.getTotalChunks() > 0
                                ? (double) r.getProcessedChunks() / r.getTotalChunks() * 100
                                : 0)
                .tag("fileName", fileName)
                .description("File processing progress percentage")
                .register(meterRegistry);

        log.info("Initialized tracking for file: {} with {} chunks", fileName, totalChunks);
    }

    public void recordChunkCompletion(String fileName, ChunkResult result) {
        fileRecords.compute(fileName, (key, record) -> {
            if (record == null) {
                record = new FileProcessingRecord(fileName, -1);
                record.setStatus(ProcessingStatus.IN_PROGRESS);
            }

            if (result.getStatus() == ProcessingStatus.COMPLETED) {
                record.incrementProcessed();
            } else {
                record.incrementFailed();
            }

            if (record.getStatus() == ProcessingStatus.COMPLETED
                    || record.getStatus() == ProcessingStatus.PARTIALLY_COMPLETED) {
                log.info("File {} processing complete. Processed: {}, Failed: {}",
                        fileName, record.getProcessedChunks(), record.getFailedChunks());
            }

            return record;
        });
    }

    public FileProcessingRecord getFileStatus(String fileName) {
        return fileRecords.get(fileName);
    }

    public ConcurrentHashMap<String, FileProcessingRecord> getAllFileStatuses() {
        return fileRecords;
    }
}
