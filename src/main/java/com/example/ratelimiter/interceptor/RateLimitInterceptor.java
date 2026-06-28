package com.example.ratelimiter.interceptor;

import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.event.RequestAllowedEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<Long> rateLimitScript;
    private final RateLimitProperties rateLimitProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();
        
        RateLimitProperties.RateLimitConfig config = findMatchingConfig(requestUri);
        if (config == null) {
            return true;
        }

        String clientIp = extractClientIp(request);
        String sanitizedPath = config.getPath().replace("/", "_").replaceAll("^_", "");
        String redisKey = String.format("rate_limit:%s:%s", sanitizedPath, clientIp);

        long now = System.currentTimeMillis();
        long windowStart = now - config.getWindowMs();
        long limit = config.getLimit();

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
                eventPublisher.publishEvent(new RequestAllowedEvent(this, clientIp, requestUri));
                return true;
            }

            // Rate limit exceeded - Return 429
            sendThrottledResponse(response, config);
            return false;
        } catch (Exception e) {
            // Fail-open logic to prevent Redis availability issues from blocking API traffic
            log.error("Error executing rate limit script in Redis for key: {}", redisKey, e);
            return true;
        }
    }

    private RateLimitProperties.RateLimitConfig findMatchingConfig(String uri) {
        if (rateLimitProperties.getConfigs() == null) {
            return null;
        }
        for (RateLimitProperties.RateLimitConfig config : rateLimitProperties.getConfigs()) {
            if (pathMatcher.match(config.getPath(), uri)) {
                return config;
            }
        }
        return null;
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

    private void sendThrottledResponse(HttpServletResponse response, RateLimitProperties.RateLimitConfig config) throws Exception {
        response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        long retryAfterSec = config.getWindowMs() / 1000;
        response.setHeader("Retry-After", String.valueOf(retryAfterSec));

        String jsonResponse = String.format(
                "{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded. Maximum allowed: %d requests per %d seconds. Please try again after %d seconds.\"}",
                config.getLimit(),
                config.getWindowMs() / 1000,
                retryAfterSec
        );

        try (PrintWriter writer = response.getWriter()) {
            writer.print(jsonResponse);
            writer.flush();
        }
    }
}
