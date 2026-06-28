package com.example.ratelimiter.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitConfigManager {

    private final RateLimitProperties rateLimitProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, RateLimitProperties.RateLimitConfig> configMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Load initial bootstrap configurations from application.yml
        if (rateLimitProperties.getConfigs() != null) {
            for (RateLimitProperties.RateLimitConfig config : rateLimitProperties.getConfigs()) {
                configMap.put(config.getPath(), config);
            }
            log.info("Initialized local rate limit configurations map: {}", configMap.keySet());
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
