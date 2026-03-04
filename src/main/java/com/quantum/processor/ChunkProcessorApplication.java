package com.quantum.processor;

import com.quantum.common.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication
@EnableJms
@EnableAsync
@EnableScheduling
public class ChunkProcessorApplication {

    private static final Logger log = LoggerFactory.getLogger(ChunkProcessorApplication.class);

    @Value("${spring.application.name:quantum-chunk-processor}")
    private String appName;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    public static void main(String[] args) {
        SpringApplication.run(ChunkProcessorApplication.class, args);
    }

    @PostConstruct
    public void init() {
        // R3: Initialize service context for structured logging
        LoggingUtils.initServiceContext(appName, "2.0.0-SNAPSHOT", activeProfile);
        // R21: Audit service start
        LoggingUtils.audit("SERVICE_START", appName, "SUCCESS", "Chunk Processor started");
        log.info("Quantum Chunk Processor started with ELK logging enabled");
    }
}
