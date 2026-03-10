package com.quantum.processor.service;

import com.quantum.common.dto.ChunkResult;
import com.quantum.common.model.FileProcessingRecord;
import com.quantum.common.model.ProcessingStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

    private final ConcurrentHashMap<String, FileProcessingRecord> fileRecords = new ConcurrentHashMap<>();

    /** Evita escribir el status file más de una vez por archivo. */
    private final Set<String> statusWritten = ConcurrentHashMap.newKeySet();

    private final MeterRegistry meterRegistry;
    private final FileStatusWriterService fileStatusWriterService;

    public TrackingService(MeterRegistry meterRegistry,
                           FileStatusWriterService fileStatusWriterService) {
        this.meterRegistry            = meterRegistry;
        this.fileStatusWriterService  = fileStatusWriterService;
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
        // Actualiza contadores de forma atómica y captura el estado resultante.
        FileProcessingRecord finalRecord = fileRecords.compute(fileName, (key, record) -> {
            if (record == null) {
                record = new FileProcessingRecord(fileName, -1);
                record.setStatus(ProcessingStatus.IN_PROGRESS);
            }

            if (result.getStatus() == ProcessingStatus.COMPLETED) {
                record.incrementProcessed();
            } else {
                record.incrementFailed();
            }
            return record;
        });

        ProcessingStatus status = finalRecord.getStatus();

        // Escribe el archivo de estado solo una vez al llegar a estado terminal.
        if (isTerminal(status) && statusWritten.add(fileName)) {
            log.info("File {} processing complete — status: {}. Processed: {}, Failed: {}",
                    fileName, status, finalRecord.getProcessedChunks(), finalRecord.getFailedChunks());

            if (status == ProcessingStatus.COMPLETED) {
                // Todos los chunks exitosos → escribe en /data/output/
                fileStatusWriterService.writeSuccess(fileName, finalRecord);
            } else {
                // PARTIALLY_COMPLETED o FAILED → escribe en /data/failed/ + /data/output/
                String errorMsg = result.getErrorMessage() != null
                        ? result.getErrorMessage()
                        : "Some or all chunks failed (status=" + status + ")";
                fileStatusWriterService.writeFailure(fileName, finalRecord, errorMsg);
            }
        }
    }

    public FileProcessingRecord getFileStatus(String fileName) {
        return fileRecords.get(fileName);
    }

    public ConcurrentHashMap<String, FileProcessingRecord> getAllFileStatuses() {
        return fileRecords;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static boolean isTerminal(ProcessingStatus status) {
        return status == ProcessingStatus.COMPLETED
                || status == ProcessingStatus.PARTIALLY_COMPLETED
                || status == ProcessingStatus.FAILED;
    }
}
