package com.restaurantqr.auth;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.user.Role;
import com.restaurantqr.user.User;
import com.restaurantqr.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.impl.DefaultClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtAuthFilter Unit Tests")
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private FilterChain filterChain;

    private JwtAuthFilter filter;

    private static final Long USER_ID = 10L;
    private static final Long RESTAURANT_ID = 1L;
    private static final String VALID_TOKEN = "valid.jwt.token";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtService, userRepository);
        SecurityContextHolder.clearContext();
    }

    // ==================== Token Extraction Tests ====================

    @Nested
    @DisplayName("Token Extraction")
    class TokenExtractionTests {

        @Test
        @DisplayName("Should extract token from Authorization header")
        void shouldExtractFromAuthHeader() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            mockValidTokenParsing("MANAGER", RESTAURANT_ID);
            mockActiveUser();

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            AuthPrincipal principal = (AuthPrincipal) auth.getPrincipal();
            assertThat(principal.userId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("Should extract token from query param for SSE endpoints")
        void shouldExtractFromQueryParamForSse() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/sse/events");
            request.setParameter("token", VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            mockValidTokenParsing("STAFF", RESTAURANT_ID);
            mockActiveUser();

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
        }

        @Test
        @DisplayName("Should NOT extract query param for non-SSE endpoints")
        void shouldNotExtractQueryParamForNonSse() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/orders");
            request.setParameter("token", VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Should prefer header token over query param")
        void shouldPreferHeaderOverQueryParam() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/sse/events");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            request.setParameter("token", "other-token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            mockValidTokenParsing("MANAGER", RESTAURANT_ID);
            mockActiveUser();

            filter.doFilterInternal(request, response, filterChain);

            // Should have used header token (VALID_TOKEN), not "other-token"
            verify(jwtService).parse(VALID_TOKEN);
            verify(jwtService, never()).parse("other-token");
        }
    }

    // ==================== No Token Tests ====================

    @Nested
    @DisplayName("No Token Present")
    class NoTokenTests {

        @Test
        @DisplayName("Should pass through when no token is present")
        void shouldPassThroughWithNoToken() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("Should pass through when Authorization header lacks Bearer prefix")
        void shouldPassThroughWithNonBearerHeader() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Basic abc123");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    // ==================== Authentication Success Tests ====================

    @Nested
    @DisplayName("Authentication Success")
    class AuthSuccessTests {

        @Test
        @DisplayName("Should set SecurityContext with correct principal and role")
        void shouldSetSecurityContext() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            mockValidTokenParsing("OWNER", 5L);
            mockActiveUser();

            filter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();

            AuthPrincipal principal = (AuthPrincipal) auth.getPrincipal();
            assertThat(principal.userId()).isEqualTo(USER_ID);
            assertThat(principal.role()).isEqualTo("OWNER");
            assertThat(principal.restaurantId()).isEqualTo(5L);
            assertThat(auth.getAuthorities())
                    .extracting(Object::toString)
                    .containsExactly("ROLE_OWNER");
        }

        @Test
        @DisplayName("Should continue filter chain after successful auth")
        void shouldContinueFilterChain() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            mockValidTokenParsing("MANAGER", RESTAURANT_ID);
            mockActiveUser();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    // ==================== Invalid Token Tests ====================

    @Nested
    @DisplayName("Invalid Token")
    class InvalidTokenTests {

        @Test
        @DisplayName("Should clear context and continue chain on JwtException")
        void shouldClearContextOnJwtException() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer bad-token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(jwtService.parse("bad-token")).thenThrow(new JwtException("Invalid"));

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }
    }

    // ==================== Inactive User Tests ====================

    @Nested
    @DisplayName("Inactive User")
    class InactiveUserTests {

        @Test
        @DisplayName("Should return 401 when user is not found in DB")
        void shouldReturn401WhenUserNotFound() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            mockValidTokenParsing("MANAGER", RESTAURANT_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("Account is inactive");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("Should return 401 when user is inactive")
        void shouldReturn401WhenUserInactive() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();

            mockValidTokenParsing("MANAGER", RESTAURANT_ID);

            User inactiveUser = new User();
            inactiveUser.setId(USER_ID);
            inactiveUser.setActive(false);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(inactiveUser));

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentType()).contains("application/json");
            assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    // ==================== Async Dispatch Tests ====================

    @Nested
    @DisplayName("Async Dispatch")
    class AsyncDispatchTests {

        @Test
        @DisplayName("shouldNotFilterAsyncDispatch should return true")
        void shouldSkipAsyncDispatch() {
            assertThat(filter.shouldNotFilterAsyncDispatch()).isTrue();
        }
    }

    // ==================== Helpers ====================

    @SuppressWarnings("unchecked")
    private void mockValidTokenParsing(String role, Long restaurantId) {
        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("sub", String.valueOf(USER_ID));
        claimsMap.put("role", role);
        if (restaurantId != null) {
            claimsMap.put("restaurantId", restaurantId);
        }
        Claims claims = new DefaultClaims(claimsMap);

        Jws<Claims> jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        when(jwtService.parse(VALID_TOKEN)).thenReturn(jws);
    }

    private void mockActiveUser() {
        User activeUser = new User();
        activeUser.setId(USER_ID);
        activeUser.setActive(true);
        activeUser.setRole(Role.MANAGER);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser));
    }
}




