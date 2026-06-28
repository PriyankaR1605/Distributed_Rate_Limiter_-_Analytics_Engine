package com.example.ratelimiter.listener;

import com.example.ratelimiter.event.RequestAllowedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Async
    @EventListener
    public void onRequestAllowed(RequestAllowedEvent event) {
        String clientIp = event.getClientIp();
        String dateStr = LocalDate.now().format(DATE_FORMATTER);
        String hllKey = String.format("analytics:dau:%s", dateStr);

        try {
            // PFADD analytics:dau:{YYYY-MM-DD} {client_ip}
            Long added = redisTemplate.opsForHyperLogLog().add(hllKey, clientIp);
            if (added != null && added > 0) {
                log.debug("New unique consumer recorded for today ({}): {}", dateStr, clientIp);
            }
        } catch (Exception e) {
            log.error("Failed to update HyperLogLog analytics for client IP: {}", clientIp, e);
        }
    }
}
