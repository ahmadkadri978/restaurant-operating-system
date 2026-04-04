package com.restaurantqr.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long-for-hmac";
    private static final String ISSUER = "restaurant-qr";
    private static final long EXPIRATION_MINUTES = 60;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(SECRET, ISSUER, EXPIRATION_MINUTES);
        jwtService = new JwtService(props);
    }

    // ==================== Constructor Tests ====================

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw when secret is null")
        void shouldThrowWhenSecretNull() {
            JwtProperties props = new JwtProperties(null, ISSUER, EXPIRATION_MINUTES);
            assertThatThrownBy(() -> new JwtService(props))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT secret is not configured");
        }

        @Test
        @DisplayName("Should throw when secret is blank")
        void shouldThrowWhenSecretBlank() {
            JwtProperties props = new JwtProperties("   ", ISSUER, EXPIRATION_MINUTES);
            assertThatThrownBy(() -> new JwtService(props))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT secret is not configured");
        }
    }

    // ==================== Token Generation Tests ====================

    @Nested
    @DisplayName("Generate Token")
    class GenerateTokenTests {

        @Test
        @DisplayName("Should generate valid token with all claims")
        void shouldGenerateTokenWithAllClaims() {
            String token = jwtService.generateToken(1L, "MANAGER", 10L);

            assertThat(token).isNotNull().isNotBlank();

            Jws<Claims> parsed = jwtService.parse(token);
            Claims claims = parsed.getPayload();

            assertThat(claims.getSubject()).isEqualTo("1");
            assertThat(claims.get("role", String.class)).isEqualTo("MANAGER");
            assertThat(claims.get("restaurantId", Long.class)).isEqualTo(10L);
            assertThat(claims.getIssuer()).isEqualTo(ISSUER);
            assertThat(claims.getIssuedAt()).isNotNull();
            assertThat(claims.getExpiration()).isNotNull();
            assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        }

        @Test
        @DisplayName("Should generate token without restaurantId for ADMIN users")
        void shouldGenerateTokenWithoutRestaurantIdForAdmin() {
            String token = jwtService.generateToken(99L, "ADMIN", null);

            Jws<Claims> parsed = jwtService.parse(token);
            Claims claims = parsed.getPayload();

            assertThat(claims.getSubject()).isEqualTo("99");
            assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
            assertThat(claims.get("restaurantId", Long.class)).isNull();
        }

        @Test
        @DisplayName("Should set correct expiration based on properties")
        void shouldSetCorrectExpiration() {
            String token = jwtService.generateToken(1L, "STAFF", 5L);

            Jws<Claims> parsed = jwtService.parse(token);
            Claims claims = parsed.getPayload();

            long diffMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
            long expectedMillis = EXPIRATION_MINUTES * 60 * 1000;

            // Allow 2-second tolerance for test execution time
            assertThat(diffMillis).isBetween(expectedMillis - 2000, expectedMillis + 2000);
        }

        @Test
        @DisplayName("Should generate unique tokens for different calls")
        void shouldGenerateUniqueTokens() {
            String token1 = jwtService.generateToken(1L, "MANAGER", 10L);
            String token2 = jwtService.generateToken(2L, "STAFF", 10L);

            assertThat(token1).isNotEqualTo(token2);
        }
    }

    // ==================== Token Parsing Tests ====================

    @Nested
    @DisplayName("Parse Token")
    class ParseTokenTests {

        @Test
        @DisplayName("Should parse valid token successfully")
        void shouldParseValidToken() {
            String token = jwtService.generateToken(42L, "OWNER", 7L);

            Jws<Claims> result = jwtService.parse(token);

            assertThat(result).isNotNull();
            assertThat(result.getPayload().getSubject()).isEqualTo("42");
        }

        @Test
        @DisplayName("Should throw JwtException for malformed token")
        void shouldThrowForMalformedToken() {
            assertThatThrownBy(() -> jwtService.parse("not.a.valid.jwt"))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Should throw JwtException for token signed with wrong key")
        void shouldThrowForWrongKey() {
            JwtProperties otherProps = new JwtProperties(
                    "another-secret-that-is-at-least-32-bytes-long!!", ISSUER, EXPIRATION_MINUTES);
            JwtService otherService = new JwtService(otherProps);

            String tokenFromOther = otherService.generateToken(1L, "MANAGER", 10L);

            assertThatThrownBy(() -> jwtService.parse(tokenFromOther))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Should throw JwtException for expired token")
        void shouldThrowForExpiredToken() {
            // Create a service with 0-minute expiration (immediately expired)
            JwtProperties expiredProps = new JwtProperties(SECRET, ISSUER, 0);
            JwtService expiredService = new JwtService(expiredProps);

            String token = expiredService.generateToken(1L, "MANAGER", 10L);

            assertThatThrownBy(() -> jwtService.parse(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("Should throw JwtException for wrong issuer")
        void shouldThrowForWrongIssuer() {
            JwtProperties otherProps = new JwtProperties(SECRET, "wrong-issuer", EXPIRATION_MINUTES);
            JwtService otherService = new JwtService(otherProps);

            String token = otherService.generateToken(1L, "MANAGER", 10L);

            assertThatThrownBy(() -> jwtService.parse(token))
                    .isInstanceOf(JwtException.class);
        }
    }
}

