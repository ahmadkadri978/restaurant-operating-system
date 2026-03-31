package com.restaurantqr.servicecall;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PushTokenService Unit Tests")
class PushTokenServiceTest {

    @Mock private StaffPushTokenRepository repo;

    private PushTokenService service;

    private static final Long RESTAURANT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final String TOKEN = "fcm-token-abc123";

    @BeforeEach
    void setUp() {
        service = new PushTokenService(repo);
    }

    // ==================== saveToken ====================

    @Nested
    @DisplayName("saveToken()")
    class SaveTokenTests {

        @Test
        @DisplayName("Should save new token when not already exists")
        void shouldSaveNewToken() {
            when(repo.existsByUserIdAndToken(USER_ID, TOKEN)).thenReturn(false);

            service.saveToken(RESTAURANT_ID, USER_ID, TOKEN);

            ArgumentCaptor<StaffPushToken> captor = ArgumentCaptor.forClass(StaffPushToken.class);
            verify(repo).save(captor.capture());

            StaffPushToken saved = captor.getValue();
            assertThat(saved.getRestaurantId()).isEqualTo(RESTAURANT_ID);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getToken()).isEqualTo(TOKEN);
        }

        @Test
        @DisplayName("Should skip save when token already exists (idempotent)")
        void shouldSkipWhenAlreadyExists() {
            when(repo.existsByUserIdAndToken(USER_ID, TOKEN)).thenReturn(true);

            service.saveToken(RESTAURANT_ID, USER_ID, TOKEN);

            verify(repo, never()).save(any());
        }
    }

    // ==================== removeToken ====================

    @Nested
    @DisplayName("removeToken()")
    class RemoveTokenTests {

        @Test
        @DisplayName("Should delete token by userId and token")
        void shouldDeleteToken() {
            service.removeToken(USER_ID, TOKEN);

            verify(repo).deleteByUserIdAndToken(USER_ID, TOKEN);
        }
    }

    // ==================== tokensForRestaurant ====================

    @Nested
    @DisplayName("tokensForRestaurant()")
    class TokensForRestaurantTests {

        @Test
        @DisplayName("Should return tokens from repository")
        void shouldReturnTokens() {
            List<String> expected = List.of("token1", "token2", "token3");
            when(repo.findTokensByRestaurantId(RESTAURANT_ID)).thenReturn(expected);

            List<String> result = service.tokensForRestaurant(RESTAURANT_ID);

            assertThat(result).hasSize(3);
            assertThat(result).containsExactly("token1", "token2", "token3");
        }

        @Test
        @DisplayName("Should return empty list when no tokens")
        void shouldReturnEmptyList() {
            when(repo.findTokensByRestaurantId(RESTAURANT_ID)).thenReturn(List.of());

            List<String> result = service.tokensForRestaurant(RESTAURANT_ID);

            assertThat(result).isEmpty();
        }
    }
}

