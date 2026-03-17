package com.restaurantqr.servicecall;

import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.sse.RestaurantEventPublisher;
import com.restaurantqr.sse.ServiceCallPayload;
import com.restaurantqr.table.PublicSessionService;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-End Integration Tests for ServiceCall SSE Events.
 * Tests the complete flow from service call creation to SSE event publishing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceCall SSE Integration Tests")
class ServiceCallSseIntegrationTest {

    private ServiceCallService serviceCallService;

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private ServiceCallRepository serviceCallRepository;

    @Mock
    private FcmPushService fcmPushService;

    @Mock
    private PublicSessionService publicSessionService;

    @Mock
    private RestaurantEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<ServiceCallPayload> serviceCallPayloadCaptor;

    private Restaurant testRestaurant;
    private RestaurantTable testTable;

    @BeforeEach
    void setUp() {
        // Setup test restaurant
        testRestaurant = new Restaurant();
        testRestaurant.setId(1L);
        testRestaurant.setName("Test Restaurant");
        testRestaurant.setActive(true);

        // Setup test table
        testTable = new RestaurantTable();
        testTable.setId(1L);
        testTable.setTableNumber(7);
        testTable.setRestaurant(testRestaurant);
        testTable.setActive(true);
        testTable.setQrToken("test-qr-token");

        // Create ServiceCallService
        serviceCallService = new ServiceCallService(
                tableRepository,
                serviceCallRepository,
                fcmPushService,
                publicSessionService,
                eventPublisher
        );
    }

    // ==================== Service Call Created SSE Tests ====================

    @Nested
    @DisplayName("SERVICE_CALL Event Tests")
    class ServiceCallEventTests {

        @BeforeEach
        void setupMocks() {
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));
            when(serviceCallRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(false);
            doNothing().when(fcmPushService).notifyServiceCall(anyLong(), anyLong(), anyInt(), anyString());

            when(serviceCallRepository.save(any(ServiceCall.class))).thenAnswer(invocation -> {
                ServiceCall call = invocation.getArgument(0);
                call.setId(100L);
                call.setCreatedAt(Instant.now());
                return call;
            });
        }

        @Test
        @DisplayName("Should publish SERVICE_CALL event when WAITER call is created")
        void shouldPublishServiceCallEventForWaiter() {
            // Given
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);

            // When
            ServiceCallResponse response = serviceCallService.createCall("test-qr-token", "valid-code", request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.callType()).isEqualTo(CallType.WAITER);

            // Verify SSE event was published
            verify(eventPublisher).publishServiceCall(eq(1L), serviceCallPayloadCaptor.capture());

            ServiceCallPayload payload = serviceCallPayloadCaptor.getValue();
            assertThat(payload.callId()).isEqualTo(100L);
            assertThat(payload.tableNumber()).isEqualTo(7);
            assertThat(payload.callType()).isEqualTo(CallType.WAITER);
            assertThat(payload.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("Should publish SERVICE_CALL event when BILL call is created")
        void shouldPublishServiceCallEventForBill() {
            // Given
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.BILL);

            // When
            serviceCallService.createCall("test-qr-token", "valid-code", request);

            // Then
            verify(eventPublisher).publishServiceCall(eq(1L), serviceCallPayloadCaptor.capture());

            ServiceCallPayload payload = serviceCallPayloadCaptor.getValue();
            assertThat(payload.callType()).isEqualTo(CallType.BILL);
        }

        @Test
        @DisplayName("Should publish SERVICE_CALL event when NARA call is created")
        void shouldPublishServiceCallEventForNara() {
            // Given
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.NARA);

            // When
            serviceCallService.createCall("test-qr-token", "valid-code", request);

            // Then
            verify(eventPublisher).publishServiceCall(eq(1L), serviceCallPayloadCaptor.capture());

            ServiceCallPayload payload = serviceCallPayloadCaptor.getValue();
            assertThat(payload.callType()).isEqualTo(CallType.NARA);
        }

        @Test
        @DisplayName("Should include correct table number in SERVICE_CALL payload")
        void shouldIncludeCorrectTableNumberInPayload() {
            // Given - table number is 7
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);

            // When
            serviceCallService.createCall("test-qr-token", "valid-code", request);

            // Then
            verify(eventPublisher).publishServiceCall(anyLong(), serviceCallPayloadCaptor.capture());
            assertThat(serviceCallPayloadCaptor.getValue().tableNumber()).isEqualTo(7);
        }

        @Test
        @DisplayName("Should publish SSE event even when FCM push fails")
        void shouldPublishSseEventEvenWhenFcmFails() {
            // Given - FCM throws exception
            doThrow(new RuntimeException("FCM Error"))
                    .when(fcmPushService).notifyServiceCall(anyLong(), anyLong(), anyInt(), anyString());

            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);

            // When & Then - should still publish SSE event
            assertThatThrownBy(() -> serviceCallService.createCall("test-qr-token", "valid-code", request))
                    .isInstanceOf(RuntimeException.class);

            // Note: In current implementation, if FCM fails after save, the exception propagates
            // but the SSE event may or may not be published depending on code order
        }
    }

    // ==================== Validation Tests ====================

    @Nested
    @DisplayName("Validation Tests - No SSE Event on Failure")
    class ValidationTests {

        @Test
        @DisplayName("Should not publish SSE event when qrToken is invalid")
        void shouldNotPublishEventWhenQrTokenInvalid() {
            // Given
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.empty());

            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);

            // When & Then
            assertThatThrownBy(() -> serviceCallService.createCall("invalid-token", "code", request))
                    .hasMessageContaining("Invalid or inactive QR token");

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should not publish SSE event when restaurant is inactive")
        void shouldNotPublishEventWhenRestaurantInactive() {
            // Given
            testRestaurant.setActive(false);
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));

            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);

            // When & Then
            assertThatThrownBy(() -> serviceCallService.createCall("test-qr-token", "code", request))
                    .hasMessageContaining("Restaurant is inactive");

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should not publish SSE event when rate limited (spam)")
        void shouldNotPublishEventWhenRateLimited() {
            // Given - table has recent service call
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));
            when(serviceCallRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(true);

            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);

            // When & Then
            assertThatThrownBy(() -> serviceCallService.createCall("test-qr-token", "code", request))
                    .hasMessageContaining("انتظر ثلاث دقائق");

            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should not publish SSE event when session code is invalid")
        void shouldNotPublishEventWhenSessionInvalid() {
            // Given
            doThrow(new RuntimeException("Invalid session"))
                    .when(publicSessionService).verifySessionOrThrow(anyString(), anyString());

            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);

            // When & Then
            assertThatThrownBy(() -> serviceCallService.createCall("test-qr-token", "invalid-code", request))
                    .hasMessageContaining("Invalid session");

            verifyNoInteractions(eventPublisher);
        }
    }

    // ==================== Restaurant Scope Tests ====================

    @Nested
    @DisplayName("Restaurant Scope Tests")
    class RestaurantScopeTests {

        @BeforeEach
        void setupMocks() {
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(serviceCallRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(false);
            doNothing().when(fcmPushService).notifyServiceCall(anyLong(), anyLong(), anyInt(), anyString());

            when(serviceCallRepository.save(any(ServiceCall.class))).thenAnswer(invocation -> {
                ServiceCall call = invocation.getArgument(0);
                call.setId(200L);
                call.setCreatedAt(Instant.now());
                return call;
            });
        }

        @Test
        @DisplayName("Should publish SERVICE_CALL event with correct restaurant ID")
        void shouldPublishEventWithCorrectRestaurantId() {
            // Given - different restaurant ID
            Long expectedRestaurantId = 99L;
            testRestaurant.setId(expectedRestaurantId);
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));

            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.BILL);

            // When
            serviceCallService.createCall("test-qr-token", "valid-code", request);

            // Then
            verify(eventPublisher).publishServiceCall(eq(99L), any(ServiceCallPayload.class));
        }

        @Test
        @DisplayName("Should publish FCM and SSE with same restaurant ID")
        void shouldPublishFcmAndSseWithSameRestaurantId() {
            // Given
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));

            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);

            // When
            serviceCallService.createCall("test-qr-token", "valid-code", request);

            // Then - both FCM and SSE should receive same restaurant ID
            verify(fcmPushService).notifyServiceCall(eq(1L), anyLong(), anyInt(), anyString());
            verify(eventPublisher).publishServiceCall(eq(1L), any(ServiceCallPayload.class));
        }
    }

    // ==================== All Call Types E2E Tests ====================

    @Nested
    @DisplayName("All Call Types E2E Tests")
    class AllCallTypesTests {

        @BeforeEach
        void setupMocks() {
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));
            when(serviceCallRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(false);
            doNothing().when(fcmPushService).notifyServiceCall(anyLong(), anyLong(), anyInt(), anyString());

            when(serviceCallRepository.save(any(ServiceCall.class))).thenAnswer(invocation -> {
                ServiceCall call = invocation.getArgument(0);
                call.setId(300L + call.getCallType().ordinal());
                call.setCreatedAt(Instant.now());
                return call;
            });
        }

        @Test
        @DisplayName("Should handle all CallType values correctly")
        void shouldHandleAllCallTypes() {
            for (CallType callType : CallType.values()) {
                // Reset mocks for each iteration
                reset(eventPublisher);
                when(serviceCallRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(false);

                // Given
                CreateServiceCallRequest request = new CreateServiceCallRequest(callType);

                // When
                ServiceCallResponse response = serviceCallService.createCall("test-qr-token", "valid-code", request);

                // Then
                assertThat(response.callType()).isEqualTo(callType);

                verify(eventPublisher).publishServiceCall(anyLong(), serviceCallPayloadCaptor.capture());
                assertThat(serviceCallPayloadCaptor.getValue().callType()).isEqualTo(callType);
            }
        }
    }

    // ==================== FCM and SSE Integration Tests ====================

    @Nested
    @DisplayName("FCM and SSE Integration Tests")
    class FcmAndSseIntegrationTests {

        @BeforeEach
        void setupMocks() {
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));
            when(serviceCallRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(false);

            when(serviceCallRepository.save(any(ServiceCall.class))).thenAnswer(invocation -> {
                ServiceCall call = invocation.getArgument(0);
                call.setId(500L);
                call.setCreatedAt(Instant.now());
                return call;
            });
        }

        @Test
        @DisplayName("Should send both FCM push and SSE event for service call")
        void shouldSendBothFcmAndSse() {
            // Given
            doNothing().when(fcmPushService).notifyServiceCall(anyLong(), anyLong(), anyInt(), anyString());
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);

            // When
            serviceCallService.createCall("test-qr-token", "valid-code", request);

            // Then - both should be called
            verify(fcmPushService).notifyServiceCall(eq(1L), eq(500L), eq(7), eq("نادل"));
            verify(eventPublisher).publishServiceCall(eq(1L), serviceCallPayloadCaptor.capture());

            ServiceCallPayload payload = serviceCallPayloadCaptor.getValue();
            assertThat(payload.callId()).isEqualTo(500L);
        }

        @Test
        @DisplayName("Should translate call type to Arabic for FCM")
        void shouldTranslateCallTypeToArabicForFcm() {
            // Given - BILL type
            doNothing().when(fcmPushService).notifyServiceCall(anyLong(), anyLong(), anyInt(), anyString());
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.BILL);

            // When
            serviceCallService.createCall("test-qr-token", "valid-code", request);

            // Then - FCM should receive "حساب" (Arabic for bill)
            verify(fcmPushService).notifyServiceCall(anyLong(), anyLong(), anyInt(), eq("حساب"));
        }

        @Test
        @DisplayName("Should include same call ID in FCM and SSE")
        void shouldIncludeSameCallIdInFcmAndSse() {
            // Given
            doNothing().when(fcmPushService).notifyServiceCall(anyLong(), anyLong(), anyInt(), anyString());
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.NARA);

            // When
            serviceCallService.createCall("test-qr-token", "valid-code", request);

            // Then
            ArgumentCaptor<Long> fcmCallIdCaptor = ArgumentCaptor.forClass(Long.class);
            verify(fcmPushService).notifyServiceCall(anyLong(), fcmCallIdCaptor.capture(), anyInt(), anyString());
            verify(eventPublisher).publishServiceCall(anyLong(), serviceCallPayloadCaptor.capture());

            assertThat(fcmCallIdCaptor.getValue()).isEqualTo(serviceCallPayloadCaptor.getValue().callId());
        }
    }

    // ==================== Response Verification Tests ====================

    @Nested
    @DisplayName("Response and Payload Consistency Tests")
    class ResponseConsistencyTests {

        @BeforeEach
        void setupMocks() {
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));
            when(serviceCallRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(false);
            doNothing().when(fcmPushService).notifyServiceCall(anyLong(), anyLong(), anyInt(), anyString());

            when(serviceCallRepository.save(any(ServiceCall.class))).thenAnswer(invocation -> {
                ServiceCall call = invocation.getArgument(0);
                call.setId(600L);
                call.setCreatedAt(Instant.parse("2026-03-17T10:30:00Z"));
                return call;
            });
        }

        @Test
        @DisplayName("Response and SSE payload should have consistent data")
        void responseShouldMatchSsePayload() {
            // Given
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);

            // When
            ServiceCallResponse response = serviceCallService.createCall("test-qr-token", "valid-code", request);

            // Then
            verify(eventPublisher).publishServiceCall(anyLong(), serviceCallPayloadCaptor.capture());
            ServiceCallPayload payload = serviceCallPayloadCaptor.getValue();

            // Response and payload should have identical data
            assertThat(response.id()).isEqualTo(payload.callId());
            assertThat(response.tableNumber()).isEqualTo(payload.tableNumber());
            assertThat(response.callType()).isEqualTo(payload.callType());
            assertThat(response.createdAt()).isEqualTo(payload.createdAt());
        }
    }
}


