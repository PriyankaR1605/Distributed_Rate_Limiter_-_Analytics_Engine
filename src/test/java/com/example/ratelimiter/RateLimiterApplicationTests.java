package com.example.ratelimiter;

import com.example.ratelimiter.config.RateLimitConfigManager;
import com.example.ratelimiter.config.RateLimitProperties;
import com.example.ratelimiter.model.UserTier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class RateLimiterApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitConfigManager configManager;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private RedisScript<Long> rateLimitScript;

    @Test
    public void testAnonymousAccessAllowedAndBlocked() throws Exception {
        // Arrange: Redis returns 1 (Allowed) for first 2 requests, then 0 (Blocked) for 3rd request
        Mockito.when(redisTemplate.execute(
                eq(rateLimitScript),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(1L).thenReturn(1L).thenReturn(0L);

        // Act & Assert
        // Request 1: Allowed
        mockMvc.perform(get("/api/v1/data")
                .remoteAddress("192.168.1.100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Request 2: Allowed
        mockMvc.perform(get("/api/v1/data")
                .remoteAddress("192.168.1.100"))
                .andExpect(status().isOk());

        // Request 3: Throttled (HTTP 429)
        mockMvc.perform(get("/api/v1/data")
                .remoteAddress("192.168.1.100"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    @Test
    public void testPremiumTierQuotas() throws Exception {
        // Arrange: Redis returns 1 (Allowed) for premium key requests
        Mockito.when(redisTemplate.execute(
                eq(rateLimitScript),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(1L);

        // Act & Assert
        mockMvc.perform(get("/api/v1/data")
                .header("X-API-KEY", "key_premium_abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    public void testRedisOutageFailoverToLocalLimiter() throws Exception {
        // Arrange: Simulate Redis connection outage by throwing an exception
        Mockito.when(redisTemplate.execute(
                eq(rateLimitScript),
                any(),
                any(),
                any(),
                any()
        )).thenThrow(new RedisConnectionFailureException("Redis connection refused"));

        // Act & Assert: Anonymous capacity on /api/v1/data is 2 requests/min
        // Request 1: Local fallback allows it
        mockMvc.perform(get("/api/v1/data")
                .remoteAddress("192.168.1.200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Request 2: Local fallback allows it
        mockMvc.perform(get("/api/v1/data")
                .remoteAddress("192.168.1.200"))
                .andExpect(status().isOk());

        // Request 3: Local fallback blocks it (429)
        mockMvc.perform(get("/api/v1/data")
                .remoteAddress("192.168.1.200"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Rate limit exceeded for tier ANONYMOUS")));
    }

    @Test
    public void testDynamicConfigUpdate() {
        // Arrange: Make sure the manager has the initial config
        RateLimitProperties.RateLimitConfig initialConfig = configManager.getConfig("/api/v1/data");
        Assertions.assertNotNull(initialConfig);
        
        int initialLimit = initialConfig.getLimits().get(UserTier.ANONYMOUS);
        Assertions.assertEquals(2, initialLimit);

        // Act: Update configuration dynamically
        RateLimitProperties.RateLimitConfig newConfig = new RateLimitProperties.RateLimitConfig();
        newConfig.setPath("/api/v1/data");
        newConfig.setWindowMs(60000);
        newConfig.setLimits(Map.of(
                UserTier.ANONYMOUS, 5,
                UserTier.STANDARD, 25,
                UserTier.PREMIUM, 150
        ));
        configManager.updateConfig(newConfig);

        // Assert: ConfigManager has updated configs
        RateLimitProperties.RateLimitConfig updatedConfig = configManager.getConfig("/api/v1/data");
        Assertions.assertEquals(5, updatedConfig.getLimits().get(UserTier.ANONYMOUS));
        Assertions.assertEquals(25, updatedConfig.getLimits().get(UserTier.STANDARD));
        Assertions.assertEquals(150, updatedConfig.getLimits().get(UserTier.PREMIUM));
    }
}
