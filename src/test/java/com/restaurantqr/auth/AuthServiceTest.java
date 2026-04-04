package com.restaurantqr.auth;

import com.restaurantqr.auth.dto.LoginRequest;
import com.restaurantqr.auth.dto.LoginResponse;
import com.restaurantqr.exception.InvalidCredentialsException;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.user.Role;
import com.restaurantqr.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private AuthenticationManager authManager;
    @Mock private JwtService jwtService;

    private AuthService authService;

    private static final Long USER_ID = 10L;
    private static final Long RESTAURANT_ID = 1L;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authManager, jwtService);
    }

    // ==================== Login Success Tests ====================

    @Nested
    @DisplayName("Login Success")
    class LoginSuccessTests {

        @Test
        @DisplayName("Should return token and user context on successful login")
        void shouldReturnTokenOnSuccess() {
            LoginRequest req = new LoginRequest("manager1", "password123");
            User user = buildUser(USER_ID, Role.MANAGER, RESTAURANT_ID);
            Authentication auth = mockAuthentication(user);

            when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(auth);
            when(jwtService.generateToken(USER_ID, "MANAGER", RESTAURANT_ID))
                    .thenReturn("jwt-token");

            LoginResponse response = authService.login(req);

            assertThat(response.token()).isEqualTo("jwt-token");
            assertThat(response.role()).isEqualTo("MANAGER");
            assertThat(response.restaurantId()).isEqualTo(RESTAURANT_ID);
            assertThat(response.userId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("Should handle ADMIN user with null restaurantId")
        void shouldHandleAdminWithNullRestaurant() {
            LoginRequest req = new LoginRequest("admin", "adminpass");
            User user = buildUser(99L, Role.ADMIN, null);
            Authentication auth = mockAuthentication(user);

            when(authManager.authenticate(any())).thenReturn(auth);
            when(jwtService.generateToken(99L, "ADMIN", null))
                    .thenReturn("admin-token");

            LoginResponse response = authService.login(req);

            assertThat(response.token()).isEqualTo("admin-token");
            assertThat(response.role()).isEqualTo("ADMIN");
            assertThat(response.restaurantId()).isNull();
            assertThat(response.userId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("Should pass correct credentials to AuthenticationManager")
        void shouldPassCorrectCredentialsToAuthManager() {
            LoginRequest req = new LoginRequest("staff1", "pass456");
            User user = buildUser(5L, Role.STAFF, RESTAURANT_ID);
            Authentication auth = mockAuthentication(user);

            when(authManager.authenticate(any())).thenReturn(auth);
            when(jwtService.generateToken(anyLong(), anyString(), anyLong())).thenReturn("token");

            authService.login(req);

            verify(authManager).authenticate(argThat(token ->
                    token instanceof UsernamePasswordAuthenticationToken upt &&
                    "staff1".equals(upt.getPrincipal()) &&
                    "pass456".equals(upt.getCredentials())
            ));
        }
    }

    // ==================== Login Failure Tests ====================

    @Nested
    @DisplayName("Login Failure")
    class LoginFailureTests {

        @Test
        @DisplayName("Should throw InvalidCredentialsException on BadCredentials")
        void shouldThrowOnBadCredentials() {
            LoginRequest req = new LoginRequest("wronguser", "wrongpass");

            when(authManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("wrong username/password");
        }

        @Test
        @DisplayName("Should throw InvalidCredentialsException on generic AuthenticationException")
        void shouldThrowOnGenericAuthException() {
            LoginRequest req = new LoginRequest("lockeduser", "pass");

            when(authManager.authenticate(any()))
                    .thenThrow(new AuthenticationException("Account locked") {});

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("wrong username/password");
        }

        @Test
        @DisplayName("Should propagate unexpected RuntimeException")
        void shouldPropagateUnexpectedException() {
            LoginRequest req = new LoginRequest("user1", "pass");

            when(authManager.authenticate(any()))
                    .thenThrow(new RuntimeException("DB connection failed"));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB connection failed");
        }
    }

    // ==================== Helpers ====================

    private User buildUser(Long id, Role role, Long restaurantId) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setUsername("user" + id);
        if (restaurantId != null) {
            Restaurant restaurant = new Restaurant();
            restaurant.setId(restaurantId);
            user.setRestaurant(restaurant);
        }
        return user;
    }

    private Authentication mockAuthentication(User user) {
        DbUserDetails details = new DbUserDetails(user);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(details);
        return auth;
    }
}

