package com.example.ratelimiter.config;

import com.example.ratelimiter.model.UserTier;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private List<RateLimitConfig> configs;

    @Data
    public static class RateLimitConfig {
        private String path;
        private long windowMs;
        private Map<UserTier, Integer> limits;
    }
}
