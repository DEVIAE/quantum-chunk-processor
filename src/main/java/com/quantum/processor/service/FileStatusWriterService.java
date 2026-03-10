package com.quantum.processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.quantum.common.model.FileProcessingRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escribe archivos JSON de estado en los directorios de salida del chunk-processor.
 *
 * <h2>Contratos de escritura</h2>
 * <ul>
 *   <li><b>Éxito</b>: {@code /data/output/<filename>.result.json}</li>
 *   <li><b>Error</b>: {@code /data/failed/<filename>.error.json}
 *              AND  {@code /data/output/<filename>.error.json}</li>
 * </ul>
 */
@Service
public class FileStatusWriterService {

    private static final Logger log = LoggerFactory.getLogger(FileStatusWriterService.class);

    @Value("${processor.paths.output-dir:/data/output}")
    private String outputDir;

    @Value("${processor.paths.failed-dir:/data/failed}")
    private String failedDir;

    private final ObjectMapper objectMapper;

    public FileStatusWriterService() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Escribe un archivo de resultado exitoso en {@code /data/output/<fileName>.result.json}.
     *
     * @param fileName nombre original del archivo procesado
     * @param record   registro de seguimiento con contadores de chunks
     */
    public void writeSuccess(String fileName, FileProcessingRecord record) {
        try {
            ensureDir(outputDir);
            Path outPath = Paths.get(outputDir, fileName + ".result.json");
            Map<String, Object> payload = buildPayload(fileName, "COMPLETED", record, null);
            writeJson(outPath, payload);
            log.info("Success status written: {}", outPath);
        } catch (Exception ex) {
            log.error("Could not write success status for '{}': {}", fileName, ex.getMessage(), ex);
        }
    }

    /**
     * Escribe archivos de error en {@code /data/failed/<fileName>.error.json}
     * y en {@code /data/output/<fileName>.error.json}.
     *
     * @param fileName     nombre original del archivo procesado
     * @param record       registro de seguimiento con contadores de chunks
     * @param errorMessage mensaje de error representativo
     */
    public void writeFailure(String fileName, FileProcessingRecord record, String errorMessage) {
        try {
            ensureDir(failedDir);
            ensureDir(outputDir);

            Map<String, Object> payload = buildPayload(fileName, "FAILED", record, errorMessage);
            payload.put("failedPath", failedDir + File.separator + fileName);

            Path failedPath = Paths.get(failedDir, fileName + ".error.json");
            writeJson(failedPath, payload);
            log.info("Failure status written (failed): {}", failedPath);

            Path outputPath = Paths.get(outputDir, fileName + ".error.json");
            writeJson(outputPath, payload);
            log.info("Failure status written (output): {}", outputPath);

        } catch (Exception ex) {
            log.error("Could not write failure status for '{}': {}", fileName, ex.getMessage(), ex);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> buildPayload(String fileName, String status,
                                              FileProcessingRecord record, String errorMessage) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fileName",      fileName);
        map.put("status",        status);
        map.put("timestamp",     Instant.now().toString());
        map.put("service",       "quantum-chunk-processor");
        map.put("totalChunks",   record.getTotalChunks());
        map.put("processedChunks", record.getProcessedChunks());
        map.put("failedChunks",  record.getFailedChunks());
        if (errorMessage != null) {
            map.put("error", errorMessage);
        }
        return map;
    }

    private void writeJson(Path path, Map<String, Object> payload) throws Exception {
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(payload)
                .getBytes(StandardCharsets.UTF_8);
        Files.write(path, bytes);
    }

    private void ensureDir(String dir) throws Exception {
        Files.createDirectories(Paths.get(dir));
    }
}
