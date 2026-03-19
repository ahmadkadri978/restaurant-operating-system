package com.restaurantqr.config;

import com.restaurantqr.user.Role;
import com.restaurantqr.user.User;
import com.restaurantqr.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Always ensures an ADMIN user exists in the system.
 * This runs on every startup, regardless of demo.seed property.
 */
@Configuration
public class AdminUserSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @Bean
    @Order(1) // Run first before other seeders
    CommandLineRunner ensureAdminUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            log.info("Checking for ADMIN user...");
            
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(passwordEncoder.encode(adminPassword));
                admin.setRole(Role.ADMIN);
                admin.setRestaurant(null); // ADMIN is system-wide
                admin.setActive(true);
                userRepository.save(admin);
                
                // Security: Only log password hint in non-production
                if ("admin123".equals(adminPassword)) {
                    log.warn("⚠️ ADMIN user created with DEFAULT password. Set ADMIN_PASSWORD env var in production!");
                } else {
                    log.info("✅ ADMIN user created with custom password");
                }
            } else {
                log.info("✅ ADMIN user already exists");
            }
        };
    }
}

