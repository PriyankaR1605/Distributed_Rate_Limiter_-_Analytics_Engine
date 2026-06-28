package com.example.ratelimiter.interceptor;

import com.example.ratelimiter.config.RateLimitConfigManager;
import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.event.RequestAllowedEvent;
import com.example.ratelimiter.event.RequestBlockedEvent;
import com.example.ratelimiter.model.UserTier;
import com.example.ratelimiter.service.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<Long> rateLimitScript;
    private final RateLimitConfigManager configManager;
    private final ApiKeyService apiKeyService;
    private final ApplicationEventPublisher eventPublisher;
    
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<Long>> localCache = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();
        
        RateLimitProperties.RateLimitConfig config = configManager.getConfig(requestUri);
        if (config == null) {
            return true;
        }

        // Identify consumer and user tier
        String apiKey = request.getHeader("X-API-KEY");
        UserTier tier = apiKeyService.resolveTier(apiKey);
        
        String clientIdentifier = (tier == UserTier.ANONYMOUS) ? extractClientIp(request) : apiKey;
        int limit = config.getLimits().getOrDefault(tier, config.getLimits().getOrDefault(UserTier.ANONYMOUS, 1));

        String sanitizedPath = config.getPath().replace("/", "_").replaceAll("^_", "");
        String redisKey = String.format("rate_limit:%s:%s:%s", sanitizedPath, tier.name().toLowerCase(), clientIdentifier);

        long now = System.currentTimeMillis();
        long windowStart = now - config.getWindowMs();

        try {
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(now),
                    String.valueOf(windowStart),
                    String.valueOf(limit)
            );

            if (result != null && result == 1L) {
                // Publish event to update analytics asynchronously
                eventPublisher.publishEvent(new RequestAllowedEvent(this, clientIdentifier, tier, requestUri));
                return true;
            }

            // Rate limit exceeded - Return 429
            eventPublisher.publishEvent(new RequestBlockedEvent(this, clientIdentifier, tier, requestUri));
            sendThrottledResponse(response, tier, limit, config.getWindowMs());
            return false;
        } catch (Exception e) {
            // Failover to local in-memory sliding window log during Redis outages
            log.warn("Redis check failed for key: {}. Falling back to local rate limiting. Error: {}", redisKey, e.getMessage());
            
            boolean allowed = isAllowedLocal(redisKey, limit, config.getWindowMs());
            if (allowed) {
                eventPublisher.publishEvent(new RequestAllowedEvent(this, clientIdentifier, tier, requestUri));
                return true;
            } else {
                eventPublisher.publishEvent(new RequestBlockedEvent(this, clientIdentifier, tier, requestUri));
                sendThrottledResponse(response, tier, limit, config.getWindowMs());
                return false;
            }
        }
    }

    private boolean isAllowedLocal(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowMs;

        ConcurrentSkipListSet<Long> timestamps = localCache.computeIfAbsent(key, k -> new ConcurrentSkipListSet<>());

        // Clean up elements older than window start
        timestamps.headSet(windowStart).clear();

        if (timestamps.size() < limit) {
            timestamps.add(now);
            return true;
        }
        return false;
    }

    @Scheduled(fixedRate = 300000) // Clean idle cache keys every 5 minutes to prevent memory leak
    public void cleanIdleLocalCache() {
        long oneHourAgo = System.currentTimeMillis() - 3600000;
        localCache.forEach((key, timestamps) -> {
            timestamps.headSet(oneHourAgo).clear();
            if (timestamps.isEmpty()) {
                localCache.remove(key);
            }
        });
    }

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private void sendThrottledResponse(HttpServletResponse response, UserTier tier, int limit, long windowMs) throws Exception {
        response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        long retryAfterSec = windowMs / 1000;
        response.setHeader("Retry-After", String.valueOf(retryAfterSec));

        String jsonResponse = String.format(
                "{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded for tier %s. Maximum allowed: %d requests per %d seconds. Please try again after %d seconds.\"}",
                tier.name(),
                limit,
                windowMs / 1000,
                retryAfterSec
        );

        try (PrintWriter writer = response.getWriter()) {
            writer.print(jsonResponse);
            writer.flush();
        }
    }
}
