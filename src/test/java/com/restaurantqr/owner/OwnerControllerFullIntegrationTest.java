package com.restaurantqr.owner;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.finance.SessionBillRepository;
import com.restaurantqr.finance.SessionPaymentRepository;
import com.restaurantqr.order.CustomerOrderRepository;
import com.restaurantqr.order.OrderItemRepository;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import com.restaurantqr.servicecall.ServiceCallRepository;
import com.restaurantqr.table.RestaurantTableRepository;
import com.restaurantqr.table.TableSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Integration Tests for OwnerController.
 * Tests controller behavior with mocked service layer dependencies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OwnerController Full Integration Tests")
class OwnerControllerFullIntegrationTest {

    private OwnerController controller;
    
    private OwnerDashboardService dashboardService;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private CustomerOrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ServiceCallRepository serviceCallRepository;

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private TableSessionRepository sessionRepository;

    @Mock
    private SessionBillRepository billRepository;

    @Mock
    private SessionPaymentRepository paymentRepository;

    private Restaurant testRestaurant;
    private Long restaurantId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        restaurantId = 1L;
        testRestaurant = new Restaurant();
        testRestaurant.setId(restaurantId);
        testRestaurant.setName("Test Restaurant Integration");

        // Create service with mocked repositories
        dashboardService = new OwnerDashboardServiceImpl(
                restaurantRepository,
                orderRepository,
                orderItemRepository,
                serviceCallRepository,
                tableRepository,
                sessionRepository,
                billRepository,
                paymentRepository
        );
        
        // Create controller
        controller = new OwnerController(dashboardService);
        
        // Setup authentication
        AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_OWNER", restaurantId);
        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);

        setupDefaultMocks();
    }

    // ==================== Security Tests ====================

    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should throw exception when no authentication provided")
        void shouldThrowExceptionWithoutAuthentication() {
            // When & Then
            assertThatThrownBy(() ->
                    controller.getDashboard(null, DashboardPeriod.DAY, null, "Europe/Berlin")
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Authentication principal is required");
        }

        @Test
        @DisplayName("Should throw exception when principal is null")
        void shouldThrowExceptionWhenPrincipalIsNull() {
            // Given - Authentication with null principal
            Authentication nullPrincipalAuth = mock(Authentication.class);
            when(nullPrincipalAuth.getPrincipal()).thenReturn(null);

            // When & Then
            assertThatThrownBy(() ->
                    controller.getDashboard(nullPrincipalAuth, DashboardPeriod.DAY, null, "Europe/Berlin")
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Authentication principal is required");
        }

        @Test
        @DisplayName("Should return dashboard when user has OWNER role")
        void shouldReturnDashboardWithOwnerRole() {
            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            assertThat(response).isNotNull();
            assertThat(response.restaurantId()).isEqualTo(restaurantId);
            assertThat(response.restaurantName()).isEqualTo("Test Restaurant Integration");
        }

        @Test
        @DisplayName("Should extract restaurantId from JWT principal only")
        void shouldExtractRestaurantIdFromJwtOnly() {
            // Given - RestaurantId in JWT
            Long jwtRestaurantId = 42L;
            Restaurant customRestaurant = new Restaurant();
            customRestaurant.setId(jwtRestaurantId);
            customRestaurant.setName("Custom Restaurant");

            when(restaurantRepository.findById(jwtRestaurantId)).thenReturn(Optional.of(customRestaurant));

            AuthPrincipal customPrincipal = new AuthPrincipal(1L, "ROLE_OWNER", jwtRestaurantId);
            Authentication customAuth = mock(Authentication.class);
            when(customAuth.getPrincipal()).thenReturn(customPrincipal);

            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    customAuth, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then - Should use restaurantId from JWT
            assertThat(response.restaurantId()).isEqualTo(jwtRestaurantId);
            assertThat(response.restaurantName()).isEqualTo("Custom Restaurant");
        }
    }

    // ==================== Parameter Tests ====================

    @Nested
    @DisplayName("Parameter Tests")
    class ParameterTests {

        @Test
        @DisplayName("Should handle DAY period parameter")
        void shouldHandleDayPeriod() {
            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            assertThat(response).isNotNull();
            assertThat(response.period()).isEqualTo(DashboardPeriod.DAY);
        }

        @Test
        @DisplayName("Should handle WEEK period parameter")
        void shouldHandleWeekPeriod() {
            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.WEEK, null, "Europe/Berlin"
            );

            // Then
            assertThat(response).isNotNull();
            assertThat(response.period()).isEqualTo(DashboardPeriod.WEEK);
        }

        @Test
        @DisplayName("Should handle MONTH period parameter")
        void shouldHandleMonthPeriod() {
            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.MONTH, null, "Europe/Berlin"
            );

            // Then
            assertThat(response).isNotNull();
            assertThat(response.period()).isEqualTo(DashboardPeriod.MONTH);
        }

        @Test
        @DisplayName("Should handle YEAR period parameter")
        void shouldHandleYearPeriod() {
            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.YEAR, null, "Europe/Berlin"
            );

            // Then
            assertThat(response).isNotNull();
            assertThat(response.period()).isEqualTo(DashboardPeriod.YEAR);
        }

        @Test
        @DisplayName("Should handle anchorDate parameter")
        void shouldHandleAnchorDateParameter() {
            // Given
            LocalDate anchorDate = LocalDate.of(2026, 3, 12);

            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, anchorDate, "Europe/Berlin"
            );

            // Then
            assertThat(response).isNotNull();
            assertThat(response.dateRange()).isNotNull();
        }

        @Test
        @DisplayName("Should handle zone parameter")
        void shouldHandleZoneParameter() {
            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "America/New_York"
            );

            // Then
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Should throw exception with invalid timezone")
        void shouldThrowExceptionWithInvalidTimezone() {
            // When & Then
            assertThatThrownBy(() ->
                    controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Invalid/Timezone")
            )
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid timezone");
        }
    }

    // ==================== Response Structure Tests ====================

    @Nested
    @DisplayName("Response Structure Tests")
    class ResponseStructureTests {

        @Test
        @DisplayName("Should return complete dashboard response structure")
        void shouldReturnCompleteDashboardStructure() {
            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            assertThat(response).isNotNull();
            assertThat(response.restaurantId()).isNotNull();
            assertThat(response.restaurantName()).isNotNull();
            assertThat(response.period()).isNotNull();
            assertThat(response.dateRange()).isNotNull();
            assertThat(response.dateRange().startDate()).isNotNull();
            assertThat(response.dateRange().endDate()).isNotNull();
            assertThat(response.overview()).isNotNull();
            assertThat(response.revenueTrend()).isNotNull();
            assertThat(response.ordersByStatus()).isNotNull();
            assertThat(response.topItemsByQuantity()).isNotNull();
            assertThat(response.topItemsByRevenue()).isNotNull();
            assertThat(response.topTables()).isNotNull();
            assertThat(response.serviceCallsByType()).isNotNull();
            assertThat(response.peakHours()).isNotNull();
        }

        @Test
        @DisplayName("Should return overview with all metrics")
        void shouldReturnOverviewWithAllMetrics() {
            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            assertThat(response.overview()).isNotNull();
            assertThat(response.overview().totalOrders()).isEqualTo(150L);
            assertThat(response.overview().newOrders()).isEqualTo(10L);
            assertThat(response.overview().sentToKitchenOrders()).isEqualTo(15L);
            assertThat(response.overview().doneOrders()).isEqualTo(120L);
            assertThat(response.overview().cancelledOrders()).isEqualTo(5L);
            assertThat(response.overview().completionRate()).isEqualByComparingTo(new BigDecimal("96.00"));
            assertThat(response.overview().cancellationRate()).isEqualByComparingTo(new BigDecimal("3.33"));
            assertThat(response.overview().grossRevenue()).isEqualByComparingTo(new BigDecimal("1234.56"));
            assertThat(response.overview().averageOrderValue()).isEqualByComparingTo(new BigDecimal("8.51"));
            assertThat(response.overview().activeTablesCount()).isEqualTo(20L);
            assertThat(response.overview().tablesWithOrdersCount()).isEqualTo(15L);
            assertThat(response.overview().serviceCallsCount()).isEqualTo(23L);
        }

        @Test
        @DisplayName("Should return correct restaurant information")
        void shouldReturnCorrectRestaurantInfo() {
            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            assertThat(response.restaurantId()).isEqualTo(restaurantId);
            assertThat(response.restaurantName()).isEqualTo("Test Restaurant Integration");
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw exception when restaurant not found")
        void shouldThrowExceptionWhenRestaurantNotFound() {
            // Given - Restaurant doesn't exist
            when(restaurantRepository.findById(999L)).thenReturn(Optional.empty());

            AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_OWNER", 999L);
            Authentication customAuth = mock(Authentication.class);
            when(customAuth.getPrincipal()).thenReturn(principal);

            // When & Then
            assertThatThrownBy(() ->
                    controller.getDashboard(customAuth, DashboardPeriod.DAY, null, "Europe/Berlin")
            )
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Restaurant not found");
        }

        @Test
        @DisplayName("Should throw exception when principal restaurantId is null")
        void shouldThrowExceptionWhenPrincipalRestaurantIdIsNull() {
            // Given - Principal with null restaurantId
            AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_OWNER", null);
            Authentication customAuth = mock(Authentication.class);
            when(customAuth.getPrincipal()).thenReturn(principal);

            // When & Then
            assertThatThrownBy(() ->
                    controller.getDashboard(customAuth, DashboardPeriod.DAY, null, "Europe/Berlin")
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Restaurant ID not found");
        }
    }

    // ==================== Multi-Tenant Isolation Tests ====================

    @Nested
    @DisplayName("Multi-Tenant Isolation Tests")
    class MultiTenantIsolationTests {

        @Test
        @DisplayName("Should isolate data by restaurantId from JWT")
        void shouldIsolateDataByRestaurantId() {
            // Given - Two different restaurants
            Long restaurant1Id = 1L;
            Long restaurant2Id = 2L;

            Restaurant restaurant1 = new Restaurant();
            restaurant1.setId(restaurant1Id);
            restaurant1.setName("Restaurant 1");

            Restaurant restaurant2 = new Restaurant();
            restaurant2.setId(restaurant2Id);
            restaurant2.setName("Restaurant 2");

            when(restaurantRepository.findById(restaurant1Id)).thenReturn(Optional.of(restaurant1));
            when(restaurantRepository.findById(restaurant2Id)).thenReturn(Optional.of(restaurant2));

            AuthPrincipal principal1 = new AuthPrincipal(1L, "ROLE_OWNER", restaurant1Id);
            Authentication auth1 = mock(Authentication.class);
            when(auth1.getPrincipal()).thenReturn(principal1);

            AuthPrincipal principal2 = new AuthPrincipal(2L, "ROLE_OWNER", restaurant2Id);
            Authentication auth2 = mock(Authentication.class);
            when(auth2.getPrincipal()).thenReturn(principal2);

            // When - Request for Restaurant 1
            OwnerDashboardResponse response1 = controller.getDashboard(
                    auth1, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            assertThat(response1.restaurantId()).isEqualTo(restaurant1Id);
            assertThat(response1.restaurantName()).isEqualTo("Restaurant 1");

            // When - Request for Restaurant 2
            OwnerDashboardResponse response2 = controller.getDashboard(
                    auth2, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            assertThat(response2.restaurantId()).isEqualTo(restaurant2Id);
            assertThat(response2.restaurantName()).isEqualTo("Restaurant 2");
        }
    }

    // ==================== All Period Combinations Tests ====================

    @Nested
    @DisplayName("All Period Combinations Tests")
    class AllPeriodCombinationsTests {

        @Test
        @DisplayName("Should handle DAY period with anchorDate and zone")
        void shouldHandleDayPeriodWithAllParameters() {
            // Given
            LocalDate anchorDate = LocalDate.of(2026, 3, 12);

            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, anchorDate, "Europe/Berlin"
            );

            // Then
            assertThat(response).isNotNull();
            assertThat(response.period()).isEqualTo(DashboardPeriod.DAY);
        }

        @Test
        @DisplayName("Should handle WEEK period with anchorDate and different zone")
        void shouldHandleWeekPeriodWithAllParameters() {
            // Given
            LocalDate anchorDate = LocalDate.of(2026, 6, 15);

            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.WEEK, anchorDate, "America/New_York"
            );

            // Then
            assertThat(response).isNotNull();
            assertThat(response.period()).isEqualTo(DashboardPeriod.WEEK);
        }

        @Test
        @DisplayName("Should handle MONTH period with anchorDate and zone")
        void shouldHandleMonthPeriodWithAllParameters() {
            // Given
            LocalDate anchorDate = LocalDate.of(2026, 1, 1);

            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.MONTH, anchorDate, "Asia/Tokyo"
            );

            // Then
            assertThat(response).isNotNull();
            assertThat(response.period()).isEqualTo(DashboardPeriod.MONTH);
        }

        @Test
        @DisplayName("Should handle YEAR period with anchorDate and zone")
        void shouldHandleYearPeriodWithAllParameters() {
            // Given
            LocalDate anchorDate = LocalDate.of(2025, 12, 31);

            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.YEAR, anchorDate, "UTC"
            );

            // Then
            assertThat(response).isNotNull();
            assertThat(response.period()).isEqualTo(DashboardPeriod.YEAR);
        }
    }

    // ==================== Helper Methods ====================

    private void setupDefaultMocks() {
        when(restaurantRepository.findById(anyLong())).thenReturn(Optional.of(testRestaurant));

        // Mock counts
        when(orderRepository.countOrdersByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(150L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(anyLong(), eq(OrderStatus.NEW), any(Instant.class), any(Instant.class)))
                .thenReturn(10L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(anyLong(), eq(OrderStatus.SENT_TO_KITCHEN), any(Instant.class), any(Instant.class)))
                .thenReturn(15L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(anyLong(), eq(OrderStatus.DONE), any(Instant.class), any(Instant.class)))
                .thenReturn(120L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(anyLong(), eq(OrderStatus.CANCELLED), any(Instant.class), any(Instant.class)))
                .thenReturn(5L);
        when(orderRepository.sumRevenueByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("1234.56"));
        when(tableRepository.countActiveTablesByRestaurant(anyLong()))
                .thenReturn(20L);
        when(orderRepository.countDistinctTablesWithOrders(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(15L);
        when(serviceCallRepository.countServiceCallsByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(23L);

        // Mock lists (empty for simplicity)
        when(orderRepository.countOrdersGroupedByStatus(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByQuantity(anyLong(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByRevenue(anyLong(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(new ArrayList<>());
        when(orderRepository.findTopTablesByOrderCount(anyLong(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(new ArrayList<>());
        when(serviceCallRepository.countServiceCallsGroupedByType(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(new ArrayList<>());
        when(orderRepository.findRevenueTrendByHour(anyLong(), anyLong(), anyLong()))
                .thenReturn(new ArrayList<>());
        when(orderRepository.findRevenueTrendByDay(anyLong(), anyLong(), anyLong()))
                .thenReturn(new ArrayList<>());
        when(orderRepository.findRevenueTrendByMonth(anyLong(), anyLong(), anyLong()))
                .thenReturn(new ArrayList<>());
        when(orderRepository.findPeakHoursByOrderCount(anyLong(), anyLong(), anyLong()))
                .thenReturn(new ArrayList<>());

        // Session analytics mocks
        when(sessionRepository.countSessionsByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(0L);
        when(sessionRepository.countOpenSessionsByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(0L);
        when(sessionRepository.countClosedSessionsByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(0L);
        when(sessionRepository.avgSessionDurationByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(null);
        when(sessionRepository.maxSessionDurationByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(null);
        when(sessionRepository.minSessionDurationByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(null);
        when(sessionRepository.sumSessionDurationByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(0L);
        when(sessionRepository.findTopTablesBySessionCount(anyLong(), any(Instant.class), any(Instant.class), any()))
                .thenReturn(new ArrayList<>());
        when(sessionRepository.findPeakSessionHours(anyLong(), anyLong(), anyLong()))
                .thenReturn(new ArrayList<>());
        when(sessionRepository.findCurrentlyOpenSessions(anyLong()))
                .thenReturn(new ArrayList<>());

        // Mock bill repository
        when(billRepository.sumGrandTotalByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
        when(billRepository.sumChargesTotalByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
        when(billRepository.countFinalizedBillsByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(0L);
        when(billRepository.findChargesBreakdownByRestaurantAndDateRange(anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(new ArrayList<>());
    }
}












