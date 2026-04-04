package com.restaurantqr.servicecall;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FcmPushService Unit Tests")
class FcmPushServiceTest {

    @Mock private PushTokenService tokenService;

    private FcmPushService fcmPushService;

    private static final Long RESTAURANT_ID = 1L;
    private static final Long CALL_ID = 10L;
    private static final int TABLE_NUMBER = 5;
    private static final String CALL_TYPE_AR = "نادل";

    @BeforeEach
    void setUp() {
        fcmPushService = new FcmPushService(tokenService);
    }

    // ==================== notifyServiceCall ====================

    @Nested
    @DisplayName("notifyServiceCall()")
    class NotifyServiceCallTests {

        @Test
        @DisplayName("Should return early when no push tokens exist")
        void shouldReturnEarlyWhenNoTokens() {
            when(tokenService.tokensForRestaurant(RESTAURANT_ID)).thenReturn(List.of());

            // Should NOT throw, should NOT call FirebaseMessaging
            fcmPushService.notifyServiceCall(RESTAURANT_ID, CALL_ID, TABLE_NUMBER, CALL_TYPE_AR);

            verify(tokenService).tokensForRestaurant(RESTAURANT_ID);
            // No exception = success (FirebaseMessaging.getInstance() never called)
        }

        @Test
        @DisplayName("Should send message for each token via FirebaseMessaging")
        void shouldSendMessageForEachToken() {
            when(tokenService.tokensForRestaurant(RESTAURANT_ID))
                    .thenReturn(List.of("token1", "token2"));

            FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);

            try (MockedStatic<FirebaseMessaging> firebaseMock = mockStatic(FirebaseMessaging.class)) {
                firebaseMock.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);
                when(mockMessaging.send(any(Message.class))).thenReturn("msg-id-1");

                fcmPushService.notifyServiceCall(RESTAURANT_ID, CALL_ID, TABLE_NUMBER, CALL_TYPE_AR);

                // send() called once per token for the sync send, plus sendAsync() once per token
                verify(mockMessaging, times(2)).send(any(Message.class));
                verify(mockMessaging, times(2)).sendAsync(any(Message.class));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("Should continue sending to remaining tokens when one fails")
        void shouldContinueOnFailure() {
            when(tokenService.tokensForRestaurant(RESTAURANT_ID))
                    .thenReturn(List.of("bad-token", "good-token"));

            FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);

            try (MockedStatic<FirebaseMessaging> firebaseMock = mockStatic(FirebaseMessaging.class)) {
                firebaseMock.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);

                when(mockMessaging.send(any(Message.class)))
                        .thenThrow(new RuntimeException("Invalid token"))
                        .thenReturn("msg-id-2");

                // Should NOT throw despite first token failing
                fcmPushService.notifyServiceCall(RESTAURANT_ID, CALL_ID, TABLE_NUMBER, CALL_TYPE_AR);

                verify(mockMessaging, times(2)).send(any(Message.class));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

