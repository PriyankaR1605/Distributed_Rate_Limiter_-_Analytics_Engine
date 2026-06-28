package com.example.ratelimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private List<RateLimitConfig> configs;

    @Data
    public static class RateLimitConfig {
        private String path;
        private int limit;
        private long windowMs;
    }
}
