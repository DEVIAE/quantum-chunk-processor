package com.quantum.processor.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.quantum.common.dto.ChunkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for indexing chunk processing results into Elasticsearch.
 * Provides direct observability of chunk events in the quantum-chunk-events
 * index.
 */
@Service
public class ElasticSearchService {

    private static final Logger log = LoggerFactory.getLogger(ElasticSearchService.class);
    private static final String INDEX_NAME = "quantum-chunk-events";

    @Value("${POD_NAME:local}")
    private String podName;

    @Value("${POD_NAMESPACE:local}")
    private String namespace;

    private final ElasticsearchClient client;

    public ElasticSearchService(ElasticsearchClient client) {
        this.client = client;
    }

    /**
     * Index a chunk processing result into Elasticsearch.
     * Creates a document combining ChunkResult data with pod metadata.
     */
    public void indexChunkResult(ChunkResult result) {
        try {
            Map<String, Object> document = new HashMap<>();
            document.put("chunkId", result.getChunkId());
            document.put("fileName", result.getFileName());
            document.put("status", result.getStatus().name());
            document.put("linesProcessed", result.getLinesProcessed());
            document.put("linesFailed", result.getLinesFailed());
            document.put("processingTimeMs", result.getProcessingTimeMs());
            document.put("completedAt", result.getCompletedAt() != null
                    ? result.getCompletedAt().toString()
                    : null);
            document.put("podName", podName);
            document.put("namespace", namespace);
            document.put("indexedAt", Instant.now().toString());

            client.index(i -> i
                    .index(INDEX_NAME)
                    .id(result.getChunkId())
                    .document(document));

            log.debug("Indexed chunk result: {} in pod {}", result.getChunkId(), podName);
        } catch (Exception e) {
            log.error("Failed to index chunk result {} into Elasticsearch: {}",
                    result.getChunkId(), e.getMessage());
            throw new RuntimeException("Elasticsearch indexing failed", e);
        }
    }
}
