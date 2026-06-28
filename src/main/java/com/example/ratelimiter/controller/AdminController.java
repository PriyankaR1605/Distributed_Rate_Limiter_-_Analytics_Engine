package com.example.ratelimiter.controller;

import com.example.ratelimiter.config.RateLimitConfigManager;
import com.example.ratelimiter.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic configUpdateTopic;
    private final ObjectMapper objectMapper;
    private final RateLimitConfigManager configManager;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @GetMapping("/metrics/dau")
    public ResponseEntity<Map<String, Object>> getDau(@RequestParam(value = "date", required = false) String date) {
        String queryDate = date;
        if (queryDate == null || queryDate.isEmpty()) {
            queryDate = LocalDate.now().format(DATE_FORMATTER);
        }

        String hllKey = String.format("analytics:dau:%s", queryDate);
        long count = 0;
        try {
            Long size = redisTemplate.opsForHyperLogLog().size(hllKey);
            if (size != null) {
                count = size;
            }
        } catch (Exception e) {
            log.error("Failed to retrieve HyperLogLog size for key: {}", hllKey, e);
        }

        return ResponseEntity.ok(Map.of(
                "date", queryDate,
                "unique_users", count
        ));
    }

    @GetMapping("/metrics/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardMetrics(@RequestParam(value = "date", required = false) String date) {
        String queryDate = date;
        if (queryDate == null || queryDate.isEmpty()) {
            queryDate = LocalDate.now().format(DATE_FORMATTER);
        }

        String hllKey = String.format("analytics:dau:%s", queryDate);
        String allowedKey = String.format("analytics:allowed:%s", queryDate);
        String blockedKey = String.format("analytics:blocked:%s", queryDate);

        long uniqueUsers = 0;
        long allowedCount = 0;
        long blockedCount = 0;

        try {
            Long size = redisTemplate.opsForHyperLogLog().size(hllKey);
            if (size != null) {
                uniqueUsers = size;
            }

            Object allowedVal = redisTemplate.opsForValue().get(allowedKey);
            if (allowedVal != null) {
                allowedCount = Long.parseLong(allowedVal.toString());
            }

            Object blockedVal = redisTemplate.opsForValue().get(blockedKey);
            if (blockedVal != null) {
                blockedCount = Long.parseLong(blockedVal.toString());
            }
        } catch (Exception e) {
            log.error("Failed to retrieve dashboard metrics from Redis", e);
        }

        return ResponseEntity.ok(Map.of(
                "date", queryDate,
                "unique_users", uniqueUsers,
                "allowed_requests", allowedCount,
                "blocked_requests", blockedCount
        ));
    }

    @GetMapping(value = "/dashboard", produces = "text/html")
    @ResponseBody
    public String getDashboardHtml() throws IOException {
        try (InputStream is = new ClassPathResource("static/dashboard.html").getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, RateLimitProperties.RateLimitConfig>> getConfigs() {
        return ResponseEntity.ok(configManager.getAllConfigs());
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, String>> updateConfig(@RequestBody RateLimitProperties.RateLimitConfig newConfig) {
        if (newConfig == null || newConfig.getPath() == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid configuration payload"));
        }

        try {
            // Serialize update to JSON
            String jsonPayload = objectMapper.writeValueAsString(newConfig);
            
            // Publish message via Redis Pub/Sub topic to sync all application instances
            redisTemplate.convertAndSend(configUpdateTopic.getTopic(), jsonPayload);
            
            log.info("Published rate limit config updates event for path: {}", newConfig.getPath());
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Configuration update event published successfully for path: " + newConfig.getPath()
            ));
        } catch (Exception e) {
            log.error("Failed to publish rate limit config update event", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to publish config update: " + e.getMessage()
            ));
        }
    }
}
