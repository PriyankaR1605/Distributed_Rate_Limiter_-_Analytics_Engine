package com.example.ratelimiter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitConfigManager {

    private final RateLimitProperties rateLimitProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, RateLimitProperties.RateLimitConfig> configMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Load initial bootstrap configurations from application.yml
        if (rateLimitProperties.getConfigs() != null) {
            for (RateLimitProperties.RateLimitConfig config : rateLimitProperties.getConfigs()) {
                configMap.put(config.getPath(), config);
            }
            log.info("Initialized local rate limit configurations map from bootstrap properties: {}", configMap.keySet());
        }

        // Override with configurations saved in Redis if present
        try {
            if (redisTemplate.opsForHash() == null) {
                log.warn("RedisTemplate.opsForHash() is null (possibly mock environment). Skipping Redis configurations load.");
                return;
            }
            Map<Object, Object> redisConfigs = redisTemplate.opsForHash().entries("ratelimiter:configs");
            if (redisConfigs != null && !redisConfigs.isEmpty()) {
                for (Map.Entry<Object, Object> entry : redisConfigs.entrySet()) {
                    String path = (String) entry.getKey();
                    String jsonVal = (String) entry.getValue();
                    RateLimitProperties.RateLimitConfig config = objectMapper.readValue(jsonVal, RateLimitProperties.RateLimitConfig.class);
                    configMap.put(path, config);
                }
                log.info("Successfully loaded and overrode rate limit configurations from Redis: {}", redisConfigs.keySet());
            }
        } catch (Exception e) {
            log.error("Failed to load rate limit configurations from Redis at startup. Falling back to bootstrap properties.", e);
        }
    }

    public Map<String, RateLimitProperties.RateLimitConfig> getAllConfigs() {
        return configMap;
    }

    public RateLimitProperties.RateLimitConfig getConfig(String uri) {
        for (Map.Entry<String, RateLimitProperties.RateLimitConfig> entry : configMap.entrySet()) {
            if (pathMatcher.match(entry.getKey(), uri)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public void updateConfig(RateLimitProperties.RateLimitConfig newConfig) {
        if (newConfig == null || newConfig.getPath() == null) {
            return;
        }
        configMap.put(newConfig.getPath(), newConfig);
        log.info("Rate limit configuration updated dynamically for path [{}]: limits={}, windowMs={}",
                newConfig.getPath(), newConfig.getLimits(), newConfig.getWindowMs());
    }
}
