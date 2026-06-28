package com.example.ratelimiter.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/metrics")
@RequiredArgsConstructor
public class AdminController {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @GetMapping("/dau")
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
}
