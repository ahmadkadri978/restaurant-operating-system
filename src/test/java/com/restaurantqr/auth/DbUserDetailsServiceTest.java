package com.restaurantqr.auth;

import com.restaurantqr.user.Role;
import com.restaurantqr.user.User;
import com.restaurantqr.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DbUserDetailsService Unit Tests")
class DbUserDetailsServiceTest {

    @Mock private UserRepository userRepository;

    private DbUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new DbUserDetailsService(userRepository);
    }

    @Test
    @DisplayName("Should return UserDetails when user exists")
    void shouldReturnUserDetailsWhenUserExists() {
        User user = new User();
        user.setId(1L);
        user.setUsername("manager1");
        user.setPasswordHash("$2a$10$hashed");
        user.setRole(Role.MANAGER);
        user.setActive(true);

        when(userRepository.findByUsername("manager1")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("manager1");

        assertThat(details).isNotNull();
        assertThat(details.getUsername()).isEqualTo("manager1");
        assertThat(details.getPassword()).isEqualTo("$2a$10$hashed");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_MANAGER");
    }

    @Test
    @DisplayName("Should return disabled UserDetails when user is inactive")
    void shouldReturnDisabledWhenInactive() {
        User user = new User();
        user.setId(2L);
        user.setUsername("disabled1");
        user.setPasswordHash("hash");
        user.setRole(Role.STAFF);
        user.setActive(false);

        when(userRepository.findByUsername("disabled1")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("disabled1");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException when user not found")
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    @DisplayName("Should return DbUserDetails with domain user access")
    void shouldReturnDbUserDetailsWithDomainUser() {
        User user = new User();
        user.setId(5L);
        user.setUsername("owner1");
        user.setPasswordHash("hash");
        user.setRole(Role.OWNER);
        user.setActive(true);

        when(userRepository.findByUsername("owner1")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("owner1");

        assertThat(details).isInstanceOf(DbUserDetails.class);
        DbUserDetails dbDetails = (DbUserDetails) details;
        assertThat(dbDetails.getDomainUser()).isSameAs(user);
        assertThat(dbDetails.getDomainUser().getId()).isEqualTo(5L);
    }
}

