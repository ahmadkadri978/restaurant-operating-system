package com.restaurantqr.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtConfigLogger {

    private static final Logger log = LoggerFactory.getLogger(JwtConfigLogger.class);

    @Value("${app.jwt.expiration-minutes}")
    private long expirationMinutes;

    @PostConstruct
    public void logIt() {
        log.info("JWT expiration-minutes = {}", expirationMinutes);
    }
}


