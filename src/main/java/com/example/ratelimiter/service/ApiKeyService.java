package com.example.ratelimiter.service;

import com.example.ratelimiter.model.UserTier;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, UserTier> fallbackRegistry = new ConcurrentHashMap<>();
    private static final String REDIS_HASH_KEY = "ratelimiter:api_keys";

    @PostConstruct
    public void init() {
        // Seed standard test api keys locally for fallback resiliency
        fallbackRegistry.put("key_premium_abc123", UserTier.PREMIUM);
        fallbackRegistry.put("key_standard_xyz789", UserTier.STANDARD);

        // Seed them into Redis on startup if the hash is empty
        try {
            if (redisTemplate.opsForHash() == null) {
                log.warn("RedisTemplate.opsForHash() is null (possibly mock environment). Skipping API key seeding in Redis.");
                return;
            }
            Boolean hasKey = redisTemplate.hasKey(REDIS_HASH_KEY);
            if (Boolean.FALSE.equals(hasKey) || redisTemplate.opsForHash().size(REDIS_HASH_KEY) == 0) {
                redisTemplate.opsForHash().put(REDIS_HASH_KEY, "key_premium_abc123", UserTier.PREMIUM.name());
                redisTemplate.opsForHash().put(REDIS_HASH_KEY, "key_standard_xyz789", UserTier.STANDARD.name());
                log.info("Seeded initial API keys into Redis.");
            }
        } catch (Exception e) {
            log.error("Failed to seed API keys in Redis. Will use local fallback registry.", e);
        }
    }

    public UserTier resolveTier(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return UserTier.ANONYMOUS;
        }

        try {
            Object tierNameObj = redisTemplate.opsForHash().get(REDIS_HASH_KEY, apiKey);
            if (tierNameObj != null) {
                return UserTier.valueOf(tierNameObj.toString());
            }
        } catch (Exception e) {
            log.error("Redis lookup failed for API key [{}], using fallback registry.", apiKey, e);
            if (fallbackRegistry.containsKey(apiKey)) {
                return fallbackRegistry.get(apiKey);
            }
        }

        return UserTier.ANONYMOUS;
    }

    public Map<String, String> getAllKeys() {
        Map<String, String> keys = new ConcurrentHashMap<>();
        try {
            Map<Object, Object> redisKeys = redisTemplate.opsForHash().entries(REDIS_HASH_KEY);
            for (Map.Entry<Object, Object> entry : redisKeys.entrySet()) {
                keys.put(entry.getKey().toString(), entry.getValue().toString());
            }
        } catch (Exception e) {
            log.error("Failed to get API keys from Redis, returning fallback registry.", e);
            for (Map.Entry<String, UserTier> entry : fallbackRegistry.entrySet()) {
                keys.put(entry.getKey(), entry.getValue().name());
            }
        }
        return keys;
    }

    public void registerKey(String apiKey, UserTier tier) {
        if (apiKey == null || apiKey.trim().isEmpty() || tier == null) {
            throw new IllegalArgumentException("API key and tier cannot be null or empty");
        }
        try {
            redisTemplate.opsForHash().put(REDIS_HASH_KEY, apiKey.trim(), tier.name());
            log.info("Registered API key: {} with tier: {}", apiKey, tier);
        } catch (Exception e) {
            log.error("Failed to register API key in Redis", e);
            throw new RuntimeException("Redis error: " + e.getMessage());
        }
    }

    public void revokeKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        try {
            redisTemplate.opsForHash().delete(REDIS_HASH_KEY, apiKey.trim());
            log.info("Revoked API key: {}", apiKey);
        } catch (Exception e) {
            log.error("Failed to revoke API key in Redis", e);
            throw new RuntimeException("Redis error: " + e.getMessage());
        }
    }
}
