package com.example.ratelimiter.service;

import com.example.ratelimiter.model.UserTier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApiKeyService {

    private final Map<String, UserTier> apiKeyRegistry = new ConcurrentHashMap<>();

    public ApiKeyService() {
        // Seed standard test api keys
        apiKeyRegistry.put("key_premium_abc123", UserTier.PREMIUM);
        apiKeyRegistry.put("key_standard_xyz789", UserTier.STANDARD);
    }

    public UserTier resolveTier(String apiKey) {
        if (apiKey == null || !apiKeyRegistry.containsKey(apiKey)) {
            return UserTier.ANONYMOUS;
        }
        return apiKeyRegistry.get(apiKey);
    }
}
