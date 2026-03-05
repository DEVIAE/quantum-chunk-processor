package com.quantum.processor.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch client configuration.
 * Creates the ElasticsearchClient bean using ELASTIC_URL and ELASTIC_API_KEY.
 */
@Configuration
public class ElasticsearchConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfig.class);

    @Value("${elasticsearch.url:http://localhost:9200}")
    private String elasticsearchUrl;

    @Value("${elasticsearch.api-key:}")
    private String apiKey;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        try {
            java.net.URI uri = java.net.URI.create(elasticsearchUrl);
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            String host = uri.getHost() != null ? uri.getHost() : "localhost";
            int port = uri.getPort() != -1 ? uri.getPort() : (scheme.equals("https") ? 443 : 9200);

            RestClient restClient;
            if (apiKey != null && !apiKey.isBlank()) {
                restClient = RestClient.builder(new HttpHost(host, port, scheme))
                        .setDefaultHeaders(new Header[] {
                                new BasicHeader("Authorization", "ApiKey " + apiKey)
                        })
                        .build();
            } else {
                restClient = RestClient.builder(new HttpHost(host, port, scheme)).build();
            }

            ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

            log.info("Elasticsearch client configured for {}://{}:{}", scheme, host, port);
            return new ElasticsearchClient(transport);
        } catch (Exception e) {
            log.warn("Failed to configure Elasticsearch client: {}. Using fallback.", e.getMessage());
            RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200, "http")).build();
            ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            return new ElasticsearchClient(transport);
        }
    }
}
