package com.example.ratelimiter.event;

import org.springframework.context.ApplicationEvent;

public class RequestAllowedEvent extends ApplicationEvent {
    private final String clientIp;
    private final String path;

    public RequestAllowedEvent(Object source, String clientIp, String path) {
        super(source);
        this.clientIp = clientIp;
        this.path = path;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getPath() {
        return path;
    }
}
