package com.restaurantqr.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseConfig {
    @PostConstruct
    public void init() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase already initialized.");
            return;
        }
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build());
            log.info("Firebase initialized using Application Default Credentials.");
        } catch (IOException e) {
            log.error("Firebase credentials unavailable. Configure workload identity or GOOGLE_APPLICATION_CREDENTIALS.");
            throw new IllegalStateException("Firebase credentials unavailable", e);
        }
    }
}
