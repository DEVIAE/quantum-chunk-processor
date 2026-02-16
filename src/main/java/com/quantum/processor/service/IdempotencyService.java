package com.quantum.processor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "chunk:processed:";

    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, Boolean> localCache;
    private final boolean redisAvailable;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.localCache = new ConcurrentHashMap<>();
        this.redisAvailable = checkRedisAvailability();

        if (!redisAvailable) {
            log.warn("Redis not available, falling back to in-memory idempotency store. "
                    + "This will NOT survive pod restarts.");
        }
    }

    public boolean isDuplicate(String chunkId) {
        if (redisAvailable) {
            return isDuplicateRedis(chunkId);
        }
        return isDuplicateLocal(chunkId);
    }

    private boolean isDuplicateRedis(String chunkId) {
        String key = KEY_PREFIX + chunkId;
        try {
            Boolean wasAbsent = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL);
            return wasAbsent != null && !wasAbsent;
        } catch (Exception e) {
            log.warn("Redis error during idempotency check, falling back to local: {}", e.getMessage());
            return isDuplicateLocal(chunkId);
        }
    }

    private boolean isDuplicateLocal(String chunkId) {
        return localCache.putIfAbsent(chunkId, Boolean.TRUE) != null;
    }

    private boolean checkRedisAvailability() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
