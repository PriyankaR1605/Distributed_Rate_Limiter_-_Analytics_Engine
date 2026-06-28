package com.example.ratelimiter.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
