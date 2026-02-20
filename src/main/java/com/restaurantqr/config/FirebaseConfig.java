package com.restaurantqr.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void init() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                log.info("Firebase already initialized.");
                return;
            }

            ClassPathResource resource = new ClassPathResource("firebase-service-account.json");
            try (InputStream is = resource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(is))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase", e);
            throw new IllegalStateException("Firebase init failed", e);
        }
    }
}
