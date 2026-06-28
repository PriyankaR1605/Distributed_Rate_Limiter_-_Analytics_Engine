package com.example.ratelimiter.listener;

import com.example.ratelimiter.config.RateLimitConfigManager;
import com.example.ratelimiter.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigUpdateMessageListener implements MessageListener {

    private final RateLimitConfigManager configManager;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            RateLimitProperties.RateLimitConfig configUpdate = objectMapper.readValue(body, RateLimitProperties.RateLimitConfig.class);
            configManager.updateConfig(configUpdate);
            log.info("Synchronized dynamic rate limit configuration for path: {}", configUpdate.getPath());
        } catch (IOException e) {
            log.error("Failed to deserialize dynamic rate limit config update payload", e);
        }
    }
}
