package com.example.ratelimiter.event;

import com.example.ratelimiter.model.UserTier;
import org.springframework.context.ApplicationEvent;

public class RequestBlockedEvent extends ApplicationEvent {
    private final String clientIdentifier;
    private final UserTier tier;
    private final String path;

    public RequestBlockedEvent(Object source, String clientIdentifier, UserTier tier, String path) {
        super(source);
        this.clientIdentifier = clientIdentifier;
        this.tier = tier;
        this.path = path;
    }

    public String getClientIdentifier() {
        return clientIdentifier;
    }

    public UserTier getTier() {
        return tier;
    }

    public String getPath() {
        return path;
    }
}
