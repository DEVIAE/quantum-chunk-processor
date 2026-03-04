package com.quantum.processor.service;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ElasticsearchService {

    private final ElasticsearchClient client;

    @Value("${elastic.index}")
    private String indexName;

    public ElasticsearchService(ElasticsearchClient client) {
        this.client = client;
    }

    public void indexChunkResult(ChunkResult result) {

        try {
    
            ChunkIndexedEvent event = new ChunkIndexedEvent(result);
    
            client.index(i -> i
                    .index(indexName)
                    .document(event)
            );
    
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}