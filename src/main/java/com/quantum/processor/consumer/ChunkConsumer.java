package com.quantum.processor.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantum.common.config.QueueConstants;
import com.quantum.common.dto.ChunkResult;
import com.quantum.common.model.Chunk;
import com.quantum.common.util.LoggingUtils;
import com.quantum.processor.service.ChunkProcessingService;
import com.quantum.processor.service.IdempotencyService;
import com.quantum.processor.service.TrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Transaction;
import org.springframework.stereotype.Component;

@Component
public class ChunkConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChunkConsumer.class);

    private final ObjectMapper objectMapper;
    private final ChunkProcessingService processingService;
    private final IdempotencyService idempotencyService;
    private final TrackingService trackingService;
    private final JmsTemplate jmsTemplate;

    public ChunkConsumer(ObjectMapper objectMapper,
            ChunkProcessingService processingService,
            IdempotencyService idempotencyService,
            TrackingService trackingService,
            JmsTemplate jmsTemplate) {
        this.objectMapper = objectMapper;
        this.processingService = processingService;
        this.idempotencyService = idempotencyService;
        this.trackingService = trackingService;
        this.jmsTemplate = jmsTemplate;
    }

    @JmsListener(destination = QueueConstants.CHUNK_QUEUE, concurrency = "${processor.consumer.concurrency:1-5}")
    public void processChunk(String chunkJson) {
        Chunk chunk = null;
        try {
            chunk = objectMapper.readValue(chunkJson, Chunk.class);

            // ── APM: etiquetar transaccion con nombre de archivo y chunk ─────────────
            Transaction txn = ElasticApm.currentTransaction();
            txn.setName("chunk-processor: " + chunk.getFileName());
            txn.addLabel("filename", chunk.getFileName());
            txn.addLabel("chunk_id", chunk.getChunkId());
            txn.addLabel("stage", "4-chunk-processor");
            // ─────────────────────────────────────────────────────────────────────────

            // R3: Set MDC context for structured logging
            LoggingUtils.setChunkContext(chunk.getChunkId(), chunk.getFileName(),
                    chunk.getChunkIndex(), chunk.getTotalChunks());

            log.info("Received chunk: {}", chunk.getChunkId());

            if (idempotencyService.isDuplicate(chunk.getChunkId())) {
                log.warn("Duplicate chunk detected, skipping: {}", chunk.getChunkId());
                return;
            }

            ChunkResult result = processingService.processChunk(chunk);

            trackingService.recordChunkCompletion(chunk.getFileName(), result);

            sendResult(result);

            // R21: Audit chunk processing
            LoggingUtils.audit("CHUNK_PROCESSED", chunk.getChunkId(),
                    result.getStatus().name(),
                    "lines=" + result.getLinesProcessed() + ", time=" + result.getProcessingTimeMs() + "ms");

            log.info("Chunk {} processed successfully ({} lines)",
                    chunk.getChunkId(), result.getLinesProcessed());

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize chunk message, sending to DLQ", e);
            // R27: Log failure details
            LoggingUtils.logFailure(log, "chunk-processor", "ChunkConsumer",
                    "deserialize", e);
            try {
                jmsTemplate.convertAndSend(QueueConstants.DLQ, chunkJson);
            } catch (Exception dlqEx) {
                log.error("Failed to send poison message to DLQ", dlqEx);
            }
        } catch (Exception e) {
            log.error("Failed to process chunk: {}",
                    chunk != null ? chunk.getChunkId() : "unknown", e);
            // R27: Log failure details
            LoggingUtils.logFailure(log, "chunk-processor", "ChunkConsumer",
                    "processChunk", e);
            if (chunk != null) {
                ChunkResult failResult = ChunkResult.failure(
                        chunk.getChunkId(), chunk.getFileName(), e.getMessage());
                trackingService.recordChunkCompletion(chunk.getFileName(), failResult);
                sendResult(failResult);
            }
            throw new RuntimeException("Chunk processing failed", e);
        } finally {
            // R3: Clear MDC after processing
            LoggingUtils.clearChunkContext();
        }
    }

    private void sendResult(ChunkResult result) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            jmsTemplate.convertAndSend(QueueConstants.RESULT_QUEUE, resultJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize chunk result", e);
        }
    }
}
