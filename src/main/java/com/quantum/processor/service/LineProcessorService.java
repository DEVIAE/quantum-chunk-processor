package com.quantum.processor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LineProcessorService {

    private static final Logger log = LoggerFactory.getLogger(LineProcessorService.class);

    public void processLine(String line, String fileName) {
        if (line == null || line.isBlank()) {
            return;
        }

        // ---------------------------------------------------------------
        // Business logic goes here.
        // This method is called once per line inside every chunk.
        //
        // Examples of what you might do:
        //   - Parse CSV/fixed-width fields
        //   - Validate data
        //   - Transform and persist to a database
        //   - Call an external API
        //   - Aggregate metrics
        // ---------------------------------------------------------------

        log.trace("Processing line from {}: length={}", fileName, line.length());
    }
}
