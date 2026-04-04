package com.restaurantqr.owner;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("OwnerDashboardService Unit Tests")
class OwnerDashboardServiceTest {

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

    @InjectMocks
    private OwnerDashboardServiceImpl dashboardService;

    private Restaurant testRestaurant;
    private Long restaurantId;
    private LocalDate anchorDate;
    private String validZone;

    @BeforeEach
    void setUp() {
        restaurantId = 1L;
        anchorDate = LocalDate.of(2026, 3, 12);
        validZone = "Europe/Berlin";

        testRestaurant = new Restaurant();
        testRestaurant.setId(restaurantId);
        testRestaurant.setName("Test Restaurant");
    }

    // ==================== Parameter Validation Tests ====================

    @Test
    @DisplayName("Should throw exception when restaurantId is null")
    void shouldThrowExceptionWhenRestaurantIdIsNull() {
        // When & Then
        assertThatThrownBy(() ->
                dashboardService.getDashboard(null, DashboardPeriod.DAY, anchorDate, validZone)
        )
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("restaurantId is required");
    }

    @Test
    @DisplayName("Should throw exception when period is null")
    void shouldThrowExceptionWhenPeriodIsNull() {
        // When & Then
        assertThatThrownBy(() ->
                dashboardService.getDashboard(restaurantId, null, anchorDate, validZone)
        )
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("period is required");
    }

    @Test
    @DisplayName("Should throw exception when zone is null")
    void shouldThrowExceptionWhenZoneIsNull() {
        // When & Then
        assertThatThrownBy(() ->
                dashboardService.getDashboard(restaurantId, DashboardPeriod.DAY, anchorDate, null)
        )
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("zone is required");
    }

    @Test
    @DisplayName("Should throw exception when zone is blank")
    void shouldThrowExceptionWhenZoneIsBlank() {
        // When & Then
        assertThatThrownBy(() ->
                dashboardService.getDashboard(restaurantId, DashboardPeriod.DAY, anchorDate, "   ")
        )
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("zone is required");
    }

    @Test
    @DisplayName("Should throw exception when zone is invalid")
    void shouldThrowExceptionWhenZoneIsInvalid() {
        // When & Then
        assertThatThrownBy(() ->
                dashboardService.getDashboard(restaurantId, DashboardPeriod.DAY, anchorDate, "Invalid/Zone")
        )
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid timezone")
                .hasMessageContaining("IANA timezone IDs");
    }

    @Test
    @DisplayName("Should throw exception when restaurant not found")
    void shouldThrowExceptionWhenRestaurantNotFound() {
        // Given
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() ->
                dashboardService.getDashboard(restaurantId, DashboardPeriod.DAY, anchorDate, validZone)
        )
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Restaurant not found");
    }

    // ==================== Period Handling Tests ====================

    @Test
    @DisplayName("Should handle DAY period correctly")
    void shouldHandleDayPeriodCorrectly() {
        // Given
        setupMocksForSuccessfulDashboard();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.period()).isEqualTo(DashboardPeriod.DAY);
        assertThat(response.restaurantId()).isEqualTo(restaurantId);
        assertThat(response.restaurantName()).isEqualTo("Test Restaurant");

        // Verify hourly revenue trend query was called
        verify(orderRepository).findRevenueTrendByHour(eq(restaurantId), anyLong(), anyLong());
        verify(orderRepository, never()).findRevenueTrendByDay(any(), anyLong(), anyLong());
        verify(orderRepository, never()).findRevenueTrendByMonth(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should handle WEEK period correctly")
    void shouldHandleWeekPeriodCorrectly() {
        // Given
        setupMocksForSuccessfulDashboard();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.WEEK, anchorDate, validZone
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.period()).isEqualTo(DashboardPeriod.WEEK);

        // Verify daily revenue trend query was called
        verify(orderRepository).findRevenueTrendByDay(eq(restaurantId), anyLong(), anyLong());
        verify(orderRepository, never()).findRevenueTrendByHour(any(), anyLong(), anyLong());
        verify(orderRepository, never()).findRevenueTrendByMonth(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should handle MONTH period correctly")
    void shouldHandleMonthPeriodCorrectly() {
        // Given
        setupMocksForSuccessfulDashboard();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.MONTH, anchorDate, validZone
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.period()).isEqualTo(DashboardPeriod.MONTH);

        // Verify daily revenue trend query was called (same as WEEK)
        verify(orderRepository).findRevenueTrendByDay(eq(restaurantId), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should handle YEAR period correctly")
    void shouldHandleYearPeriodCorrectly() {
        // Given
        setupMocksForSuccessfulDashboard();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.YEAR, anchorDate, validZone
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.period()).isEqualTo(DashboardPeriod.YEAR);

        // Verify monthly revenue trend query was called
        verify(orderRepository).findRevenueTrendByMonth(eq(restaurantId), anyLong(), anyLong());
        verify(orderRepository, never()).findRevenueTrendByHour(any(), anyLong(), anyLong());
        verify(orderRepository, never()).findRevenueTrendByDay(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should use current date when anchorDate is null")
    void shouldUseCurrentDateWhenAnchorDateIsNull() {
        // Given
        setupMocksForSuccessfulDashboard();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, null, validZone
        );

        // Then
        assertThat(response).isNotNull();
        // The service should use LocalDate.now(ZoneId) internally
        assertThat(response.dateRange()).isNotNull();
    }

    // ==================== Null/Empty Repository Result Tests ====================

    @Test
    @DisplayName("Should handle null count results from repository")
    void shouldHandleNullCountResults() {
        // Given
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(testRestaurant));

        // Return nulls for all counts
        when(orderRepository.countOrdersByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(any(), any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(orderRepository.sumRevenueByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(tableRepository.countActiveTablesByRestaurant(any())).thenReturn(null);
        when(orderRepository.countDistinctTablesWithOrders(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(serviceCallRepository.countServiceCallsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);

        // Empty lists for other queries
        when(orderRepository.countOrdersGroupedByStatus(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByQuantity(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByRevenue(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderRepository.findTopTablesByOrderCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(serviceCallRepository.countServiceCallsGroupedByType(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        when(orderRepository.findRevenueTrendByHour(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(orderRepository.findPeakHoursByOrderCount(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());

        // Session analytics: null counts
        when(sessionRepository.countSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.countOpenSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.countClosedSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.avgSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.maxSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.minSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.sumSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.findTopTablesBySessionCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(sessionRepository.findPeakSessionHours(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(sessionRepository.findCurrentlyOpenSessions(any())).thenReturn(new ArrayList<>());

        // Bill + payment repositories
        when(billRepository.sumGrandTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(billRepository.sumChargesTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(billRepository.countFinalizedBillsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(billRepository.findChargesBreakdownByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        when(paymentRepository.sumTipByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(billRepository.sumItemsSubtotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then - Should not throw NPE, should handle nulls gracefully
        assertThat(response).isNotNull();
        assertThat(response.overview().totalOrders()).isEqualTo(0L);
        assertThat(response.overview().newOrders()).isEqualTo(0L);
        assertThat(response.overview().doneOrders()).isEqualTo(0L);
        assertThat(response.overview().grossRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.overview().averageOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.overview().activeTablesCount()).isEqualTo(0L);
        assertThat(response.overview().serviceCallsCount()).isEqualTo(0L);

        // Session analytics null-safe
        assertThat(response.sessionAnalytics()).isNotNull();
        assertThat(response.sessionAnalytics().totalSessions()).isEqualTo(0L);
        assertThat(response.sessionAnalytics().openSessionsCount()).isEqualTo(0L);
        assertThat(response.sessionAnalytics().closedSessionsCount()).isEqualTo(0L);
        assertThat(response.sessionAnalytics().avgSessionDurationMinutes()).isNull();
        assertThat(response.sessionAnalytics().totalOccupiedMinutes()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should handle empty list results from repository")
    void shouldHandleEmptyListResults() {
        // Given
        setupMocksWithEmptyLists();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then - Should return empty lists, not null
        assertThat(response).isNotNull();
        assertThat(response.revenueTrend()).isEmpty();
        assertThat(response.ordersByStatus()).isEmpty();
        assertThat(response.topItemsByQuantity()).isEmpty();
        assertThat(response.topItemsByRevenue()).isEmpty();
        assertThat(response.topTables()).isEmpty();
        assertThat(response.serviceCallsByType()).isEmpty();
        assertThat(response.peakHours()).isEmpty();

        // Session analytics
        assertThat(response.sessionAnalytics()).isNotNull();
        assertThat(response.sessionAnalytics().totalSessions()).isEqualTo(0L);
        assertThat(response.sessionAnalytics().topTablesBySessions()).isEmpty();
        assertThat(response.sessionAnalytics().peakSessionHours()).isEmpty();
        assertThat(response.sessionAnalytics().currentOpenTables()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null list results from repository")
    void shouldHandleNullListResults() {
        // Given
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(testRestaurant));

        // Mock counts
        when(orderRepository.countOrdersByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(any(), any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(orderRepository.sumRevenueByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(tableRepository.countActiveTablesByRestaurant(any())).thenReturn(0L);
        when(orderRepository.countDistinctTablesWithOrders(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(serviceCallRepository.countServiceCallsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);

        // Return null for lists
        when(orderRepository.countOrdersGroupedByStatus(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(orderItemRepository.findTopItemsByQuantity(any(), any(Instant.class), any(Instant.class), any())).thenReturn(null);
        when(orderItemRepository.findTopItemsByRevenue(any(), any(Instant.class), any(Instant.class), any())).thenReturn(null);
        when(orderRepository.findTopTablesByOrderCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(null);
        when(serviceCallRepository.countServiceCallsGroupedByType(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(orderRepository.findRevenueTrendByHour(any(), anyLong(), anyLong())).thenReturn(null);
        when(orderRepository.findPeakHoursByOrderCount(any(), anyLong(), anyLong())).thenReturn(null);

        // Session analytics: null lists
        when(sessionRepository.countSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.countOpenSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.countClosedSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.avgSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.maxSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.minSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.sumSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.findTopTablesBySessionCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(null);
        when(sessionRepository.findPeakSessionHours(any(), anyLong(), anyLong())).thenReturn(null);
        when(sessionRepository.findCurrentlyOpenSessions(any())).thenReturn(null);

        // Bill + payment repositories
        when(billRepository.sumGrandTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.sumChargesTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.countFinalizedBillsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(billRepository.findChargesBreakdownByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        when(paymentRepository.sumTipByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.sumItemsSubtotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then - Should convert nulls to empty lists
        assertThat(response).isNotNull();
        assertThat(response.revenueTrend()).isNotNull().isEmpty();
        assertThat(response.ordersByStatus()).isNotNull().isEmpty();
        assertThat(response.topItemsByQuantity()).isNotNull().isEmpty();
        assertThat(response.topItemsByRevenue()).isNotNull().isEmpty();
        assertThat(response.topTables()).isNotNull().isEmpty();
        assertThat(response.serviceCallsByType()).isNotNull().isEmpty();
        assertThat(response.peakHours()).isNotNull().isEmpty();

        // Session analytics null-safe
        assertThat(response.sessionAnalytics()).isNotNull();
        assertThat(response.sessionAnalytics().topTablesBySessions()).isNotNull().isEmpty();
        assertThat(response.sessionAnalytics().peakSessionHours()).isNotNull().isEmpty();
        assertThat(response.sessionAnalytics().currentOpenTables()).isNotNull().isEmpty();
    }

    // ==================== Restaurant Scoping Tests ====================

    @Test
    @DisplayName("Should pass restaurantId to all repository queries")
    void shouldPassRestaurantIdToAllRepositoryQueries() {
        // Given
        setupMocksForSuccessfulDashboard();

        // When
        dashboardService.getDashboard(restaurantId, DashboardPeriod.DAY, anchorDate, validZone);

        // Then - Verify restaurantId is passed to every query
        verify(restaurantRepository).findById(restaurantId);
        verify(orderRepository, atLeastOnce()).countOrdersByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(orderRepository, atLeastOnce()).countOrdersByRestaurantAndStatusAndDateRange(eq(restaurantId), any(), any(Instant.class), any(Instant.class));
        verify(orderRepository).sumRevenueByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(orderRepository).countOrdersGroupedByStatus(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(orderRepository).countDistinctTablesWithOrders(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(orderRepository).findTopTablesByOrderCount(eq(restaurantId), any(Instant.class), any(Instant.class), any());
        verify(orderRepository).findRevenueTrendByHour(eq(restaurantId), anyLong(), anyLong());
        verify(orderRepository).findPeakHoursByOrderCount(eq(restaurantId), anyLong(), anyLong());
        verify(orderItemRepository).findTopItemsByQuantity(eq(restaurantId), any(Instant.class), any(Instant.class), any());
        verify(orderItemRepository).findTopItemsByRevenue(eq(restaurantId), any(Instant.class), any(Instant.class), any());
        verify(serviceCallRepository).countServiceCallsByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(serviceCallRepository).countServiceCallsGroupedByType(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(tableRepository).countActiveTablesByRestaurant(eq(restaurantId));

        // Verify session analytics queries scoped to restaurantId
        verify(sessionRepository).countSessionsByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(sessionRepository).countOpenSessionsByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(sessionRepository).countClosedSessionsByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(sessionRepository).avgSessionDurationByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(sessionRepository).maxSessionDurationByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(sessionRepository).minSessionDurationByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(sessionRepository).sumSessionDurationByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class));
        verify(sessionRepository).findTopTablesBySessionCount(eq(restaurantId), any(Instant.class), any(Instant.class), any());
        verify(sessionRepository).findPeakSessionHours(eq(restaurantId), anyLong(), anyLong());
        verify(sessionRepository).findCurrentlyOpenSessions(eq(restaurantId));
    }

    // ==================== Date Range Calculation Tests ====================

    @Test
    @DisplayName("Should calculate correct date range for DAY period")
    void shouldCalculateCorrectDateRangeForDayPeriod() {
        // Given
        setupMocksForSuccessfulDashboard();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then
        DateRangeDto dateRange = response.dateRange();
        assertThat(dateRange.period()).isEqualTo(DashboardPeriod.DAY);
        assertThat(dateRange.startDate()).isNotNull();
        assertThat(dateRange.endDate()).isNotNull();
        assertThat(dateRange.endDate()).isAfter(dateRange.startDate());

        // Should be 24 hours apart
        Duration duration = Duration.between(dateRange.startDate(), dateRange.endDate());
        assertThat(duration.toHours()).isEqualTo(24);
    }

    @Test
    @DisplayName("Should calculate correct date range for WEEK period")
    void shouldCalculateCorrectDateRangeForWeekPeriod() {
        // Given
        setupMocksForSuccessfulDashboard();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.WEEK, anchorDate, validZone
        );

        // Then
        DateRangeDto dateRange = response.dateRange();
        assertThat(dateRange.period()).isEqualTo(DashboardPeriod.WEEK);

        // Should be 7 days apart
        Duration duration = Duration.between(dateRange.startDate(), dateRange.endDate());
        assertThat(duration.toDays()).isEqualTo(7);
    }

    // ==================== Session Analytics Tests ====================

    @Test
    @DisplayName("Should return session analytics with correct values")
    void shouldReturnSessionAnalyticsWithCorrectValues() {
        // Given
        setupMocksForSuccessfulDashboard();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then
        assertThat(response.sessionAnalytics()).isNotNull();
        SessionAnalyticsDto sa = response.sessionAnalytics();
        assertThat(sa.totalSessions()).isEqualTo(25L);
        assertThat(sa.openSessionsCount()).isEqualTo(3L);
        assertThat(sa.closedSessionsCount()).isEqualTo(22L);
        assertThat(sa.avgSessionDurationMinutes()).isEqualTo(46); // Math.round(45.5)
        assertThat(sa.longestSessionMinutes()).isEqualTo(180);
        assertThat(sa.shortestSessionMinutes()).isEqualTo(5);
        assertThat(sa.totalOccupiedMinutes()).isEqualTo(1001L);
        assertThat(sa.topTablesBySessions()).isNotNull();
        assertThat(sa.peakSessionHours()).isNotNull();
        assertThat(sa.currentOpenTables()).isNotNull();
    }

    // ==================== Helper Methods ====================

    // ==================== Data Aggregation Correctness Tests (Batch 3) ====================

    @Test
    @DisplayName("Should compute averageOrderValue = grossRevenue / nonCancelledOrders")
    void shouldComputeAverageOrderValueCorrectly() {
        // Given: 150 total, 5 cancelled → 145 non-cancelled; grossRevenue = 1234.56 → avg ≈ 8.51
        setupMocksForSuccessfulDashboard();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then
        OverviewDto overview = response.overview();
        assertThat(overview.averageOrderValue())
                .isEqualByComparingTo(new BigDecimal("8.51"));
    }

    @Test
    @DisplayName("Should compute averageOrderValue = 0 when doneOrders = 0")
    void shouldComputeAverageOrderValueZeroWhenNoDoneOrders() {
        // Given
        setupMocksWithEmptyLists();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then
        assertThat(response.overview().averageOrderValue())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should compute completionRate = DONE / (DONE + CANCELLED) * 100")
    void shouldComputeCompletionRateCorrectly() {
        // Given: 120 done, 5 cancelled → 120/(120+5)*100 = 96.00
        setupMocksForSuccessfulDashboard();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then
        assertThat(response.overview().completionRate())
                .isEqualByComparingTo(new BigDecimal("96.00"));
    }

    @Test
    @DisplayName("Should compute cancellationRate = CANCELLED / total * 100")
    void shouldComputeCancellationRateCorrectly() {
        // Given: 5 cancelled out of 150 total → 5/150*100 = 3.33
        setupMocksForSuccessfulDashboard();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then
        assertThat(response.overview().cancellationRate())
                .isEqualByComparingTo(new BigDecimal("3.33"));
    }

    @Test
    @DisplayName("Should compute completionRate = 0 when no done or cancelled orders")
    void shouldComputeCompletionRateZeroWhenNoOrders() {
        // Given
        setupMocksWithEmptyLists();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then
        assertThat(response.overview().completionRate())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.overview().cancellationRate())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should compute totalCollected = totalBilledRevenue + totalTipRevenue")
    void shouldComputeTotalCollectedCorrectly() {
        // Given
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(testRestaurant));

        // Basic counts (need non-null)
        when(orderRepository.countOrdersByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(10L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(any(), any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(orderRepository.sumRevenueByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(tableRepository.countActiveTablesByRestaurant(any())).thenReturn(0L);
        when(orderRepository.countDistinctTablesWithOrders(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(serviceCallRepository.countServiceCallsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);

        // Empty lists
        when(orderRepository.countOrdersGroupedByStatus(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByQuantity(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByRevenue(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderRepository.findTopTablesByOrderCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(serviceCallRepository.countServiceCallsGroupedByType(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByHour(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByDay(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByMonth(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(orderRepository.findPeakHoursByOrderCount(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());

        // Session analytics
        when(sessionRepository.countSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.countOpenSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.countClosedSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.avgSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.maxSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.minSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.sumSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.findTopTablesBySessionCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(sessionRepository.findPeakSessionHours(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(sessionRepository.findCurrentlyOpenSessions(any())).thenReturn(new ArrayList<>());

        // BILL: billed revenue = 50000.00, charges = 10000.00
        when(billRepository.sumGrandTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("50000.00"));
        when(billRepository.sumChargesTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("10000.00"));
        when(billRepository.countFinalizedBillsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(5L);
        when(billRepository.findChargesBreakdownByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(new ArrayList<>());
        when(billRepository.sumItemsSubtotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("40000.00"));

        // TIP: 7500.00
        when(paymentRepository.sumTipByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("7500.00"));

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then: totalCollected = 50000 + 7500 = 57500
        OverviewDto overview = response.overview();
        assertThat(overview.totalBilledRevenue()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(overview.totalChargesRevenue()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(overview.totalTipRevenue()).isEqualByComparingTo(new BigDecimal("7500.00"));
        assertThat(overview.billedItemsRevenue()).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(overview.totalCollected()).isEqualByComparingTo(new BigDecimal("57500.00"));
    }

    @Test
    @DisplayName("Should compute averageBillValue = totalBilledRevenue / finalizedBillsCount")
    void shouldComputeAverageBillValueCorrectly() {
        // Given
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(testRestaurant));

        // Zero-value order counts (not relevant for this test)
        when(orderRepository.countOrdersByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(any(), any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(orderRepository.sumRevenueByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(tableRepository.countActiveTablesByRestaurant(any())).thenReturn(0L);
        when(orderRepository.countDistinctTablesWithOrders(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(serviceCallRepository.countServiceCallsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);

        // Empty lists
        when(orderRepository.countOrdersGroupedByStatus(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByQuantity(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByRevenue(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderRepository.findTopTablesByOrderCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(serviceCallRepository.countServiceCallsGroupedByType(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        // Use lenient() for period-dependent methods since not all are called in each test
        // Changed from Instant to Long (epoch seconds)
        lenient().when(orderRepository.findRevenueTrendByHour(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByDay(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByMonth(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(orderRepository.findPeakHoursByOrderCount(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());

        // Session analytics
        when(sessionRepository.countSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.countOpenSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.countClosedSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.avgSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.maxSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.minSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.sumSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.findTopTablesBySessionCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(sessionRepository.findPeakSessionHours(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(sessionRepository.findCurrentlyOpenSessions(any())).thenReturn(new ArrayList<>());

        // BILL: 4 bills totaling 100000 → avg = 25000
        when(billRepository.sumGrandTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("100000.00"));
        when(billRepository.sumChargesTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
        when(billRepository.countFinalizedBillsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(4L);
        when(billRepository.findChargesBreakdownByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(new ArrayList<>());
        when(billRepository.sumItemsSubtotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("100000.00"));
        when(paymentRepository.sumTipByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then: averageBillValue = 100000 / 4 = 25000
        assertThat(response.overview().averageBillValue())
                .isEqualByComparingTo(new BigDecimal("25000.00"));
        assertThat(response.overview().finalizedBillsCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("Should return overview with all counts populated from mocks")
    void shouldReturnOverviewWithAllCountsPopulated() {
        // Given
        setupMocksForSuccessfulDashboard();

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then: verify all overview fields match mocked data
        OverviewDto overview = response.overview();
        assertThat(overview.totalOrders()).isEqualTo(150L);
        assertThat(overview.newOrders()).isEqualTo(10L);
        assertThat(overview.sentToKitchenOrders()).isEqualTo(15L);
        assertThat(overview.doneOrders()).isEqualTo(120L);
        assertThat(overview.cancelledOrders()).isEqualTo(5L);
        assertThat(overview.grossRevenue()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(overview.activeTablesCount()).isEqualTo(20L);
        assertThat(overview.tablesWithOrdersCount()).isEqualTo(15L);
        assertThat(overview.serviceCallsCount()).isEqualTo(23L);
    }

    // ==================== End Data Aggregation Correctness Tests ====================

    private void setupMocksForSuccessfulDashboard() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(testRestaurant));

        // Mock counts
        when(orderRepository.countOrdersByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(150L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(eq(restaurantId), eq(OrderStatus.NEW), any(Instant.class), any(Instant.class))).thenReturn(10L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(eq(restaurantId), eq(OrderStatus.SENT_TO_KITCHEN), any(Instant.class), any(Instant.class))).thenReturn(15L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(eq(restaurantId), eq(OrderStatus.DONE), any(Instant.class), any(Instant.class))).thenReturn(120L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(eq(restaurantId), eq(OrderStatus.CANCELLED), any(Instant.class), any(Instant.class))).thenReturn(5L);
        when(orderRepository.sumRevenueByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(new BigDecimal("1234.56"));
        when(tableRepository.countActiveTablesByRestaurant(any())).thenReturn(20L);
        when(orderRepository.countDistinctTablesWithOrders(any(), any(Instant.class), any(Instant.class))).thenReturn(15L);
        when(serviceCallRepository.countServiceCallsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(23L);

        // Mock lists (empty is fine for these tests)
        when(orderRepository.countOrdersGroupedByStatus(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByQuantity(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByRevenue(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderRepository.findTopTablesByOrderCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(serviceCallRepository.countServiceCallsGroupedByType(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        // Use lenient() for period-dependent methods since not all are called in each test
        // Changed from Instant to Long (epoch seconds)
        lenient().when(orderRepository.findRevenueTrendByHour(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByDay(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByMonth(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(orderRepository.findPeakHoursByOrderCount(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());

        // Mock session analytics
        when(sessionRepository.countSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(25L);
        when(sessionRepository.countOpenSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(3L);
        when(sessionRepository.countClosedSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(22L);
        when(sessionRepository.avgSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(45.5);
        when(sessionRepository.maxSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(180);
        when(sessionRepository.minSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(5);
        when(sessionRepository.sumSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(1001L);
        when(sessionRepository.findTopTablesBySessionCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(sessionRepository.findPeakSessionHours(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(sessionRepository.findCurrentlyOpenSessions(any())).thenReturn(new ArrayList<>());

        // Mock bill repository
        when(billRepository.sumGrandTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.sumChargesTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.countFinalizedBillsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(billRepository.findChargesBreakdownByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());

        // Mock payment repository (tip + items subtotal)
        when(paymentRepository.sumTipByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.sumItemsSubtotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
    }

    private void setupMocksWithEmptyLists() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(testRestaurant));

        // Mock counts with zeros
        when(orderRepository.countOrdersByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(any(), any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(orderRepository.sumRevenueByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(tableRepository.countActiveTablesByRestaurant(any())).thenReturn(0L);
        when(orderRepository.countDistinctTablesWithOrders(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(serviceCallRepository.countServiceCallsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);

        // Mock empty lists - use lenient() for period-dependent methods
        when(orderRepository.countOrdersGroupedByStatus(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByQuantity(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByRevenue(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderRepository.findTopTablesByOrderCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(serviceCallRepository.countServiceCallsGroupedByType(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByHour(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByDay(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByMonth(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(orderRepository.findPeakHoursByOrderCount(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());

        // Mock session analytics (empty)
        when(sessionRepository.countSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.countOpenSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.countClosedSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.avgSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.maxSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.minSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.sumSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.findTopTablesBySessionCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(sessionRepository.findPeakSessionHours(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(sessionRepository.findCurrentlyOpenSessions(any())).thenReturn(new ArrayList<>());

        // Mock bill repository (empty)
        when(billRepository.sumGrandTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.sumChargesTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.countFinalizedBillsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(billRepository.findChargesBreakdownByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());

        // Mock payment repository (empty)
        when(paymentRepository.sumTipByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.sumItemsSubtotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Billing metrics should use bill finalizedAt as time boundary")
    void billingMetricsShouldUseBillFinalizedAtAsTimeBoundary() {
        // Given
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(testRestaurant));

        // Stub minimal order/session/service data so overview/session parts don't NPE
        when(orderRepository.countOrdersByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(any(), any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(orderRepository.sumRevenueByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(tableRepository.countActiveTablesByRestaurant(any())).thenReturn(0L);
        when(orderRepository.countDistinctTablesWithOrders(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(serviceCallRepository.countServiceCallsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);

        when(orderRepository.countOrdersGroupedByStatus(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByQuantity(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByRevenue(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderRepository.findTopTablesByOrderCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(serviceCallRepository.countServiceCallsGroupedByType(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByHour(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByDay(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByMonth(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(orderRepository.findPeakHoursByOrderCount(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());

        when(sessionRepository.countSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.countOpenSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.countClosedSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.avgSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.maxSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.minSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.sumSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.findTopTablesBySessionCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(sessionRepository.findPeakSessionHours(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(sessionRepository.findCurrentlyOpenSessions(any())).thenReturn(new ArrayList<>());

        // For this test we don't care about tips or items subtotal
        when(paymentRepository.sumTipByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.sumItemsSubtotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.findChargesBreakdownByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());

        // Capture the start/end instants used for billing queries
        final Instant[] capturedStart = new Instant[1];
        final Instant[] capturedEnd = new Instant[1];

        when(billRepository.sumGrandTotalByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    capturedStart[0] = invocation.getArgument(1, Instant.class);
                    capturedEnd[0] = invocation.getArgument(2, Instant.class);

                    // Simulate two bills:
                    // Bill A: finalized exactly at capturedStart + 12h (inside window) → 100
                    // Bill B: finalized exactly at capturedEnd (exclusive) → 200 (outside window)
                    Instant within = capturedStart[0].plus(Duration.ofHours(12));
                    Instant boundary = capturedEnd[0];

                    BigDecimal total = BigDecimal.ZERO;
                    if (!within.isBefore(capturedStart[0]) && within.isBefore(capturedEnd[0])) {
                        total = total.add(new BigDecimal("100.00"));
                    }
                    if (!boundary.isBefore(capturedStart[0]) && boundary.isBefore(capturedEnd[0])) {
                        total = total.add(new BigDecimal("200.00"));
                    }
                    return total;
                });

        when(billRepository.sumChargesTotalByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
        when(billRepository.countFinalizedBillsByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class)))
                .thenReturn(1L);

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then
        assertThat(capturedStart[0]).isNotNull();
        assertThat(capturedEnd[0]).isNotNull();
        // Only the in-window bill (100) should be counted
        assertThat(response.overview().totalBilledRevenue())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Tip metrics should use payment paidAt as time boundary")
    void tipMetricsShouldUsePaymentPaidAtAsTimeBoundary() {
        // Given
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(testRestaurant));

        // Minimal order/bill/session data
        when(orderRepository.countOrdersByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(orderRepository.countOrdersByRestaurantAndStatusAndDateRange(any(), any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(orderRepository.sumRevenueByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(tableRepository.countActiveTablesByRestaurant(any())).thenReturn(0L);
        when(orderRepository.countDistinctTablesWithOrders(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(serviceCallRepository.countServiceCallsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);

        when(orderRepository.countOrdersGroupedByStatus(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByQuantity(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderItemRepository.findTopItemsByRevenue(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(orderRepository.findTopTablesByOrderCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(serviceCallRepository.countServiceCallsGroupedByType(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByHour(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByDay(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        lenient().when(orderRepository.findRevenueTrendByMonth(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(orderRepository.findPeakHoursByOrderCount(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());

        when(sessionRepository.countSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.countOpenSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.countClosedSessionsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.avgSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.maxSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.minSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(null);
        when(sessionRepository.sumSessionDurationByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(sessionRepository.findTopTablesBySessionCount(any(), any(Instant.class), any(Instant.class), any())).thenReturn(new ArrayList<>());
        when(sessionRepository.findPeakSessionHours(any(), anyLong(), anyLong())).thenReturn(new ArrayList<>());
        when(sessionRepository.findCurrentlyOpenSessions(any())).thenReturn(new ArrayList<>());

        when(billRepository.sumGrandTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.sumChargesTotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);
        when(billRepository.countFinalizedBillsByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(0L);
        when(billRepository.findChargesBreakdownByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(new ArrayList<>());
        when(billRepository.sumItemsSubtotalByRestaurantAndDateRange(any(), any(Instant.class), any(Instant.class))).thenReturn(BigDecimal.ZERO);

        final Instant[] capturedStart = new Instant[1];
        final Instant[] capturedEnd = new Instant[1];

        when(paymentRepository.sumTipByRestaurantAndDateRange(eq(restaurantId), any(Instant.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    capturedStart[0] = invocation.getArgument(1, Instant.class);
                    capturedEnd[0] = invocation.getArgument(2, Instant.class);

                    // Payment A: paid at middle of window → 30
                    // Payment B: paid exactly at end (exclusive) → 70 (outside)
                    Instant inWindow = capturedStart[0].plus(Duration.ofHours(8));
                    Instant boundary = capturedEnd[0];

                    BigDecimal total = BigDecimal.ZERO;
                    if (!inWindow.isBefore(capturedStart[0]) && inWindow.isBefore(capturedEnd[0])) {
                        total = total.add(new BigDecimal("30.00"));
                    }
                    if (!boundary.isBefore(capturedStart[0]) && boundary.isBefore(capturedEnd[0])) {
                        total = total.add(new BigDecimal("70.00"));
                    }
                    return total;
                });

        // When
        OwnerDashboardResponse response = dashboardService.getDashboard(
                restaurantId, DashboardPeriod.DAY, anchorDate, validZone
        );

        // Then
        assertThat(capturedStart[0]).isNotNull();
        assertThat(capturedEnd[0]).isNotNull();
        // Only the in-window payment (30) should be counted
        assertThat(response.overview().totalTipRevenue())
                .isEqualByComparingTo(new BigDecimal("30.00"));
    }
}
