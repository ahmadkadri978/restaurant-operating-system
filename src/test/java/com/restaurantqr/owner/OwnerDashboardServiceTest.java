package com.restaurantqr.owner;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.order.CustomerOrderRepository;
import com.restaurantqr.order.OrderItemRepository;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import com.restaurantqr.servicecall.ServiceCallRepository;
import com.restaurantqr.table.RestaurantTableRepository;
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

    // ==================== Helper Methods ====================

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
    }
}




