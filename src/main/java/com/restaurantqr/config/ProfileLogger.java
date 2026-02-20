package com.restaurantqr.config;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProfileLogger {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @PostConstruct
    public void logProfile() {
        System.out.println("🚀 ACTIVE SPRING PROFILE = " + activeProfile);
    }
}
