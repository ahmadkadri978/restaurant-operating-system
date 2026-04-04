package com.restaurantqr.servicecall;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.RateLimitException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.sse.SseOutboundEvent;
import com.restaurantqr.table.PublicSessionService;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceCallService Unit Tests")
class ServiceCallServiceTest {

    @Mock private RestaurantTableRepository tableRepository;
    @Mock private ServiceCallRepository serviceCallRepository;
    @Mock private FcmPushService fcmPushService;
    @Mock private PublicSessionService publicSessionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ServiceCallService service;

    private Restaurant testRestaurant;
    private RestaurantTable testTable;

    private static final String QR_TOKEN = "QR-TOKEN-1";
    private static final String SESSION_CODE = "1234";
    private static final Long RESTAURANT_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new ServiceCallService(
                tableRepository, serviceCallRepository, fcmPushService,
                publicSessionService, eventPublisher
        );

        testRestaurant = new Restaurant();
        testRestaurant.setId(RESTAURANT_ID);
        testRestaurant.setName("Test Restaurant");
        testRestaurant.setActive(true);

        testTable = new RestaurantTable();
        testTable.setId(5L);
        testTable.setTableNumber(5);
        testTable.setRestaurant(testRestaurant);
        testTable.setActive(true);
    }

    // ==================== createCall() Tests ====================

    @Nested
    @DisplayName("createCall()")
    class CreateCallTests {

        @Test
        @DisplayName("Should throw when qrToken is null")
        void shouldThrowWhenQrTokenNull() {
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);
            assertThatThrownBy(() -> service.createCall(null, SESSION_CODE, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("qrToken");
        }

        @Test
        @DisplayName("Should throw when qrToken is blank")
        void shouldThrowWhenQrTokenBlank() {
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);
            assertThatThrownBy(() -> service.createCall("   ", SESSION_CODE, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("qrToken");
        }

        @Test
        @DisplayName("Should throw when session code is null")
        void shouldThrowWhenCodeNull() {
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);
            assertThatThrownBy(() -> service.createCall(QR_TOKEN, null, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("code");
        }

        @Test
        @DisplayName("Should throw when table not found for qrToken")
        void shouldThrowWhenTableNotFound() {
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);
            when(tableRepository.findActiveByQrTokenForUpdate(QR_TOKEN)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createCall(QR_TOKEN, SESSION_CODE, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("QR token");
        }

        @Test
        @DisplayName("Should throw when restaurant is inactive")
        void shouldThrowWhenRestaurantInactive() {
            testRestaurant.setActive(false);
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);
            when(tableRepository.findActiveByQrTokenForUpdate(QR_TOKEN)).thenReturn(Optional.of(testTable));

            assertThatThrownBy(() -> service.createCall(QR_TOKEN, SESSION_CODE, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("inactive");
        }

        @Test
        @DisplayName("Should throw RateLimitException when cooldown active (spam)")
        void shouldBlockSpam() {
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);
            when(tableRepository.findActiveByQrTokenForUpdate(QR_TOKEN)).thenReturn(Optional.of(testTable));
            when(serviceCallRepository.existsByTableIdAndCreatedAtAfter(eq(testTable.getId()), any(Instant.class)))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.createCall(QR_TOKEN, SESSION_CODE, request))
                    .isInstanceOf(RateLimitException.class);
        }

        @Test
        @DisplayName("Should create call successfully and return response")
        void shouldCreateCallSuccessfully() {
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);
            when(tableRepository.findActiveByQrTokenForUpdate(QR_TOKEN)).thenReturn(Optional.of(testTable));
            when(serviceCallRepository.existsByTableIdAndCreatedAtAfter(eq(testTable.getId()), any(Instant.class)))
                    .thenReturn(false);

            ServiceCall savedCall = new ServiceCall();
            savedCall.setId(10L);
            savedCall.setRestaurant(testRestaurant);
            savedCall.setTable(testTable);
            savedCall.setCallType(CallType.WAITER);
            savedCall.setCreatedAt(Instant.now());

            when(serviceCallRepository.save(any(ServiceCall.class))).thenReturn(savedCall);

            ServiceCallResponse response = service.createCall(QR_TOKEN, SESSION_CODE, request);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.tableNumber()).isEqualTo(5);
            assertThat(response.callType()).isEqualTo(CallType.WAITER);
            assertThat(response.createdAt()).isNotNull();

            verify(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            verify(fcmPushService).notifyServiceCall(RESTAURANT_ID, 10L, 5, "نادل");
            verify(eventPublisher).publishEvent(any(SseOutboundEvent.class));
        }

        @Test
        @DisplayName("Should publish SSE event for BILL call type")
        void shouldPublishSseForBillCallType() {
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.BILL);
            when(tableRepository.findActiveByQrTokenForUpdate(QR_TOKEN)).thenReturn(Optional.of(testTable));
            when(serviceCallRepository.existsByTableIdAndCreatedAtAfter(eq(testTable.getId()), any(Instant.class)))
                    .thenReturn(false);

            ServiceCall savedCall = new ServiceCall();
            savedCall.setId(11L);
            savedCall.setRestaurant(testRestaurant);
            savedCall.setTable(testTable);
            savedCall.setCallType(CallType.BILL);
            savedCall.setCreatedAt(Instant.now());
            when(serviceCallRepository.save(any(ServiceCall.class))).thenReturn(savedCall);

            ServiceCallResponse response = service.createCall(QR_TOKEN, SESSION_CODE, request);

            assertThat(response.callType()).isEqualTo(CallType.BILL);
            verify(fcmPushService).notifyServiceCall(RESTAURANT_ID, 11L, 5, "حساب");
        }
    }

    // ==================== getActiveCalls() Tests ====================

    @Nested
    @DisplayName("getActiveCalls()")
    class GetActiveCallsTests {

        @Test
        @DisplayName("Should return all active calls on initial load (since=null)")
        void shouldReturnAllActiveCallsOnInitialLoad() {
            ServiceCall call = new ServiceCall();
            call.setId(1L);
            call.setTable(testTable);
            call.setCallType(CallType.WAITER);
            call.setCreatedAt(Instant.now());

            when(serviceCallRepository.findByRestaurantIdAndCreatedAtAfterOrderByCreatedAtDesc(
                    eq(RESTAURANT_ID), any(Instant.class))).thenReturn(List.of(call));

            List<ServiceCallResponse> result = service.getActiveCalls(RESTAURANT_ID, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(1L);
            assertThat(result.get(0).tableNumber()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should return only new calls when polling (since!=null)")
        void shouldReturnOnlyNewCallsWhenPolling() {
            Instant since = Instant.now().minusSeconds(10);
            ServiceCall newCall = new ServiceCall();
            newCall.setId(2L);
            newCall.setTable(testTable);
            newCall.setCallType(CallType.NARA);
            newCall.setCreatedAt(Instant.now());

            when(serviceCallRepository.findNewActiveCalls(eq(RESTAURANT_ID), eq(since), any(Instant.class)))
                    .thenReturn(List.of(newCall));

            List<ServiceCallResponse> result = service.getActiveCalls(RESTAURANT_ID, since);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).callType()).isEqualTo(CallType.NARA);
        }

        @Test
        @DisplayName("Should return empty list when no active calls")
        void shouldReturnEmptyWhenNoCalls() {
            when(serviceCallRepository.findByRestaurantIdAndCreatedAtAfterOrderByCreatedAtDesc(
                    eq(RESTAURANT_ID), any(Instant.class))).thenReturn(List.of());

            List<ServiceCallResponse> result = service.getActiveCalls(RESTAURANT_ID, null);

            assertThat(result).isEmpty();
        }
    }

    // ==================== getCooldownStatus() Tests ====================

    @Nested
    @DisplayName("getCooldownStatus()")
    class GetCooldownStatusTests {

        @Test
        @DisplayName("Should throw when qrToken is null")
        void shouldThrowWhenQrTokenNull() {
            assertThatThrownBy(() -> service.getCooldownStatus(null, SESSION_CODE))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("qrToken");
        }

        @Test
        @DisplayName("Should throw when code is blank")
        void shouldThrowWhenCodeBlank() {
            assertThatThrownBy(() -> service.getCooldownStatus(QR_TOKEN, "  "))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("code");
        }

        @Test
        @DisplayName("Should return active=true with remaining seconds when cooldown is active")
        void shouldReturnActiveWhenCooldownActive() {
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.of(testTable));

            ServiceCall recentCall = new ServiceCall();
            recentCall.setId(5L);
            recentCall.setCallType(CallType.WAITER);
            recentCall.setCreatedAt(Instant.now().minusSeconds(60)); // 60s ago → 120s remaining

            when(serviceCallRepository.findTopByTableIdAndCreatedAtAfterOrderByCreatedAtDesc(
                    eq(testTable.getId()), any(Instant.class))).thenReturn(Optional.of(recentCall));

            CooldownStatusResponse status = service.getCooldownStatus(QR_TOKEN, SESSION_CODE);

            assertThat(status.active()).isTrue();
            assertThat(status.cooldownRemainingSeconds()).isGreaterThan(0);
            assertThat(status.cooldownRemainingSeconds()).isLessThanOrEqualTo(180);
            assertThat(status.callType()).isEqualTo(CallType.WAITER);
            assertThat(status.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("Should return active=false when no recent call")
        void shouldReturnInactiveWhenNoCooldown() {
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.of(testTable));
            when(serviceCallRepository.findTopByTableIdAndCreatedAtAfterOrderByCreatedAtDesc(
                    eq(testTable.getId()), any(Instant.class))).thenReturn(Optional.empty());

            CooldownStatusResponse status = service.getCooldownStatus(QR_TOKEN, SESSION_CODE);

            assertThat(status.active()).isFalse();
            assertThat(status.cooldownRemainingSeconds()).isZero();
            assertThat(status.callType()).isNull();
            assertThat(status.createdAt()).isNull();
        }

        @Test
        @DisplayName("Should throw when table not found")
        void shouldThrowWhenTableNotFound() {
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCooldownStatus(QR_TOKEN, SESSION_CODE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}

