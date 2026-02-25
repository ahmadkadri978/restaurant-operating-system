package com.restaurantqr.config;

import com.restaurantqr.user.Role;
import com.restaurantqr.user.User;
import com.restaurantqr.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class SuperAdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminSeeder.class);

    private static final String SUPER_ADMIN_USERNAME = "superadmin";
    private static final String SUPER_ADMIN_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminSeeder(UserRepository userRepository,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername(SUPER_ADMIN_USERNAME)) {
            log.info("SUPER_ADMIN already exists, skipping seed.");
            return;
        }

        User superAdmin = new User();
        superAdmin.setUsername(SUPER_ADMIN_USERNAME);
        superAdmin.setPasswordHash(passwordEncoder.encode(SUPER_ADMIN_PASSWORD));
        superAdmin.setRole(Role.SUPER_ADMIN);
        superAdmin.setRestaurant(null);
        superAdmin.setActive(true);

        userRepository.save(superAdmin);
        log.info("SUPER_ADMIN user seeded successfully (no restaurant).");
    }
}

