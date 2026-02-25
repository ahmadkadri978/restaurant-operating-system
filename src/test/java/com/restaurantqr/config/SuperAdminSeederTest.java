package com.restaurantqr.config;

import com.restaurantqr.user.Role;
import com.restaurantqr.user.User;
import com.restaurantqr.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperAdminSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SuperAdminSeeder seeder;

    @Test
    @DisplayName("run – superadmin does not exist – creates user with null restaurant")
    void run_noExistingSuperAdmin_seedsUser() {
        when(userRepository.existsByUsername("superadmin")).thenReturn(false);
        when(passwordEncoder.encode("admin123")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        seeder.run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo("superadmin");
        assertThat(created.getPasswordHash()).isEqualTo("$2a$encoded");
        assertThat(created.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(created.getRestaurant()).isNull();
        assertThat(created.isActive()).isTrue();
    }

    @Test
    @DisplayName("run – superadmin already exists – skips seeding")
    void run_superAdminExists_skips() {
        when(userRepository.existsByUsername("superadmin")).thenReturn(true);

        seeder.run();

        verify(userRepository, never()).save(any());
    }
}

