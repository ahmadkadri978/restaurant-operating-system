package com.restaurantqr.owner;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for OwnerController.
 * Tests all aspects: security, parameters, responses, errors, edge cases.
 */
@DisplayName("OwnerController - Complete Test Suite")
class OwnerControllerCompleteTest {

    private OwnerController controller;
    private OwnerDashboardService dashboardService;
    private Authentication authentication;
    private Long restaurantId;
    private OwnerDashboardResponse mockResponse;

    @BeforeEach
    void setUp() {
        dashboardService = mock(OwnerDashboardService.class);
        controller = new OwnerController(dashboardService);

        restaurantId = 1L;
        AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_OWNER", restaurantId);
        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);

        // Create mock response
        DateRangeDto dateRange = new DateRangeDto(
                Instant.parse("2026-03-11T23:00:00Z"),
                Instant.parse("2026-03-12T23:00:00Z"),
                DashboardPeriod.DAY
        );

        OverviewDto overview = new OverviewDto(
                150L, 10L, 15L, 120L, 5L,
                new BigDecimal("96.00"),
                new BigDecimal("3.33"),
                new BigDecimal("1234.56"),
                new BigDecimal("10.29"),
                20L, 15L, 23L
        );

        mockResponse = new OwnerDashboardResponse(
                restaurantId,
                "Test Restaurant",
                DashboardPeriod.DAY,
                dateRange,
                overview,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    // ==================== Security & Authentication Tests ====================

    @Nested
    @DisplayName("Security & Authentication Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should extract restaurantId from JWT principal")
        void shouldExtractRestaurantIdFromJwtPrincipal() {
            // Given
            Long expectedRestaurantId = 42L;
            AuthPrincipal customPrincipal = new AuthPrincipal(1L, "ROLE_OWNER", expectedRestaurantId);
            when(authentication.getPrincipal()).thenReturn(customPrincipal);
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            // When
            controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Europe/Berlin");

            // Then
            verify(dashboardService).getDashboard(
                    eq(expectedRestaurantId),
                    eq(DashboardPeriod.DAY),
                    isNull(),
                    eq("Europe/Berlin")
            );
        }

        @Test
        @DisplayName("Should throw exception when authentication is null")
        void shouldThrowExceptionWhenAuthenticationIsNull() {
            assertThatThrownBy(() ->
                    controller.getDashboard(null, DashboardPeriod.DAY, null, "Europe/Berlin")
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Authentication principal is required");
        }

        @Test
        @DisplayName("Should throw exception when principal is null")
        void shouldThrowExceptionWhenPrincipalIsNull() {
            when(authentication.getPrincipal()).thenReturn(null);

            assertThatThrownBy(() ->
                    controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Europe/Berlin")
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Authentication principal is required");
        }

        @Test
        @DisplayName("Should throw exception when restaurantId in principal is null")
        void shouldThrowExceptionWhenRestaurantIdIsNull() {
            AuthPrincipal principalWithoutRestaurant = new AuthPrincipal(1L, "ROLE_OWNER", null);
            when(authentication.getPrincipal()).thenReturn(principalWithoutRestaurant);

            assertThatThrownBy(() ->
                    controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Europe/Berlin")
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Restaurant ID not found");
        }

        @Test
        @DisplayName("Should always use restaurantId from principal, never from request")
        void shouldAlwaysUseRestaurantIdFromPrincipal() {
            Long restaurantIdFromJWT = 42L;
            AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_OWNER", restaurantIdFromJWT);
            when(authentication.getPrincipal()).thenReturn(principal);
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Europe/Berlin");

            verify(dashboardService).getDashboard(eq(restaurantIdFromJWT), any(), any(), any());
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 10L, 100L, 999L, Long.MAX_VALUE})
        @DisplayName("Should handle various restaurantId values")
        void shouldHandleVariousRestaurantIds(Long testRestaurantId) {
            AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_OWNER", testRestaurantId);
            when(authentication.getPrincipal()).thenReturn(principal);
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Europe/Berlin");

            verify(dashboardService).getDashboard(eq(testRestaurantId), any(), any(), any());
        }
    }

    // ==================== Period Parameter Tests ====================

    @Nested
    @DisplayName("Period Parameter Tests")
    class PeriodTests {

        @Test
        @DisplayName("Should handle DAY period")
        void shouldHandleDayPeriod() {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            assertThat(response).isNotNull();
            verify(dashboardService).getDashboard(any(), eq(DashboardPeriod.DAY), any(), any());
        }

        @Test
        @DisplayName("Should handle WEEK period")
        void shouldHandleWeekPeriod() {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, DashboardPeriod.WEEK, null, "Europe/Berlin");

            verify(dashboardService).getDashboard(any(), eq(DashboardPeriod.WEEK), any(), any());
        }

        @Test
        @DisplayName("Should handle MONTH period")
        void shouldHandleMonthPeriod() {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, DashboardPeriod.MONTH, null, "Europe/Berlin");

            verify(dashboardService).getDashboard(any(), eq(DashboardPeriod.MONTH), any(), any());
        }

        @Test
        @DisplayName("Should handle YEAR period")
        void shouldHandleYearPeriod() {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, DashboardPeriod.YEAR, null, "Europe/Berlin");

            verify(dashboardService).getDashboard(any(), eq(DashboardPeriod.YEAR), any(), any());
        }

        @ParameterizedTest
        @EnumSource(DashboardPeriod.class)
        @DisplayName("Should handle all DashboardPeriod enum values")
        void shouldHandleAllDashboardPeriods(DashboardPeriod period) {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, period, null, "Europe/Berlin");

            verify(dashboardService).getDashboard(any(), eq(period), any(), any());
        }
    }

    // ==================== Optional Parameter Tests ====================

    @Nested
    @DisplayName("Optional Parameter Tests")
    class OptionalParameterTests {

        @Test
        @DisplayName("Should handle anchorDate parameter")
        void shouldHandleAnchorDateParameter() {
            LocalDate anchorDate = LocalDate.of(2026, 3, 12);
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, DashboardPeriod.DAY, anchorDate, "Europe/Berlin");

            verify(dashboardService).getDashboard(any(), any(), eq(anchorDate), any());
        }

        @Test
        @DisplayName("Should pass null anchorDate when not provided")
        void shouldPassNullAnchorDateWhenNotProvided() {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Europe/Berlin");

            verify(dashboardService).getDashboard(any(), any(), isNull(), any());
        }

        @Test
        @DisplayName("Should handle zone parameter")
        void shouldHandleZoneParameter() {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, DashboardPeriod.DAY, null, "America/New_York");

            verify(dashboardService).getDashboard(any(), any(), any(), eq("America/New_York"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Europe/Berlin",
                "America/New_York",
                "Asia/Tokyo",
                "UTC",
                "Europe/London"
        })
        @DisplayName("Should handle various timezones")
        void shouldHandleVariousTimezones(String timezone) {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, DashboardPeriod.DAY, null, timezone);

            verify(dashboardService).getDashboard(any(), any(), any(), eq(timezone));
        }

        @ParameterizedTest
        @CsvSource({
                "2020, 1, 1",
                "2024, 2, 29",
                "2026, 3, 12",
                "2030, 12, 31"
        })
        @DisplayName("Should handle various anchor dates")
        void shouldHandleVariousAnchorDates(int year, int month, int day) {
            LocalDate date = LocalDate.of(year, month, day);
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, DashboardPeriod.DAY, date, "Europe/Berlin");

            verify(dashboardService).getDashboard(any(), any(), eq(date), any());
        }
    }

    // ==================== Response Tests ====================

    @Nested
    @DisplayName("Response Tests")
    class ResponseTests {

        @Test
        @DisplayName("Should return complete dashboard response")
        void shouldReturnCompleteDashboardResponse() {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            assertThat(response).isNotNull();
            assertThat(response.restaurantId()).isEqualTo(restaurantId);
            assertThat(response.restaurantName()).isEqualTo("Test Restaurant");
            assertThat(response.period()).isEqualTo(DashboardPeriod.DAY);
            assertThat(response.dateRange()).isNotNull();
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
        @DisplayName("Should return response with correct overview values")
        void shouldReturnResponseWithCorrectOverviewValues() {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            OverviewDto overview = response.overview();
            assertThat(overview.totalOrders()).isEqualTo(150L);
            assertThat(overview.newOrders()).isEqualTo(10L);
            assertThat(overview.sentToKitchenOrders()).isEqualTo(15L);
            assertThat(overview.doneOrders()).isEqualTo(120L);
            assertThat(overview.cancelledOrders()).isEqualTo(5L);
            assertThat(overview.completionRate()).isEqualByComparingTo(new BigDecimal("96.00"));
            assertThat(overview.cancellationRate()).isEqualByComparingTo(new BigDecimal("3.33"));
            assertThat(overview.grossRevenue()).isEqualByComparingTo(new BigDecimal("1234.56"));
            assertThat(overview.averageOrderValue()).isEqualByComparingTo(new BigDecimal("10.29"));
            assertThat(overview.activeTablesCount()).isEqualTo(20L);
            assertThat(overview.tablesWithOrdersCount()).isEqualTo(15L);
            assertThat(overview.serviceCallsCount()).isEqualTo(23L);
        }

        @Test
        @DisplayName("Should not modify service response")
        void shouldNotModifyServiceResponse() {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            assertThat(response).isSameAs(mockResponse);
        }

        @Test
        @DisplayName("Should handle empty lists in response")
        void shouldHandleEmptyListsInResponse() {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            assertThat(response.revenueTrend()).isEmpty();
            assertThat(response.ordersByStatus()).isEmpty();
            assertThat(response.topItemsByQuantity()).isEmpty();
            assertThat(response.topItemsByRevenue()).isEmpty();
            assertThat(response.topTables()).isEmpty();
            assertThat(response.serviceCallsByType()).isEmpty();
            assertThat(response.peakHours()).isEmpty();
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should propagate BusinessRuleException from service")
        void shouldPropagateBusinessRuleExceptionFromService() {
            when(dashboardService.getDashboard(any(), any(), any(), any()))
                    .thenThrow(new BusinessRuleException("Invalid timezone"));

            assertThatThrownBy(() ->
                    controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Invalid/Zone")
            )
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid timezone");
        }

        @Test
        @DisplayName("Should propagate RuntimeException from service")
        void shouldPropagateRuntimeExceptionFromService() {
            when(dashboardService.getDashboard(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Database error"));

            assertThatThrownBy(() ->
                    controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Europe/Berlin")
            )
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Database error");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Invalid timezone",
                "Restaurant not found",
                "Permission denied"
        })
        @DisplayName("Should propagate various BusinessRuleExceptions")
        void shouldPropagateVariousBusinessRuleExceptions(String errorMessage) {
            when(dashboardService.getDashboard(any(), any(), any(), any()))
                    .thenThrow(new BusinessRuleException(errorMessage));

            assertThatThrownBy(() ->
                    controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Europe/Berlin")
            )
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining(errorMessage);
        }
    }

    // ==================== Service Interaction Tests ====================

    @Nested
    @DisplayName("Service Interaction Tests")
    class ServiceInteractionTests {

        @Test
        @DisplayName("Should call service exactly once per request")
        void shouldCallServiceExactlyOnce() {
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Europe/Berlin");

            verify(dashboardService, times(1)).getDashboard(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should pass all parameters correctly to service")
        void shouldPassAllParametersCorrectlyToService() {
            Long expectedRestaurantId = 123L;
            DashboardPeriod expectedPeriod = DashboardPeriod.MONTH;
            LocalDate expectedDate = LocalDate.of(2026, 6, 15);
            String expectedZone = "Asia/Tokyo";

            AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_OWNER", expectedRestaurantId);
            when(authentication.getPrincipal()).thenReturn(principal);
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, expectedPeriod, expectedDate, expectedZone);

            verify(dashboardService).getDashboard(
                    eq(expectedRestaurantId),
                    eq(expectedPeriod),
                    eq(expectedDate),
                    eq(expectedZone)
            );
        }

        @ParameterizedTest
        @CsvSource({
                "DAY, Europe/Berlin, 2026, 3, 12",
                "WEEK, America/New_York, 2026, 6, 15",
                "MONTH, Asia/Tokyo, 2026, 1, 1",
                "YEAR, UTC, 2025, 12, 31"
        })
        @DisplayName("Should handle various parameter combinations")
        void shouldHandleVariousParameterCombinations(
                DashboardPeriod period,
                String timezone,
                int year,
                int month,
                int day
        ) {
            LocalDate date = LocalDate.of(year, month, day);
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, period, date, timezone);

            verify(dashboardService).getDashboard(any(), eq(period), eq(date), eq(timezone));
        }
    }

    // ==================== Constructor Tests ====================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create controller with valid service")
        void shouldCreateControllerWithValidService() {
            OwnerDashboardService service = mock(OwnerDashboardService.class);

            OwnerController newController = new OwnerController(service);

            assertThat(newController).isNotNull();
        }
    }

    // ==================== Multi-Tenant Tests ====================

    @Nested
    @DisplayName("Multi-Tenant Isolation Tests")
    class MultiTenantTests {

        @Test
        @DisplayName("Should handle different restaurantIds from different principals")
        void shouldHandleDifferentRestaurantIds() {
            Long restaurant1 = 1L;
            Long restaurant2 = 999L;

            AuthPrincipal principal1 = new AuthPrincipal(1L, "ROLE_OWNER", restaurant1);
            AuthPrincipal principal2 = new AuthPrincipal(2L, "ROLE_OWNER", restaurant2);

            Authentication auth1 = mock(Authentication.class);
            Authentication auth2 = mock(Authentication.class);

            when(auth1.getPrincipal()).thenReturn(principal1);
            when(auth2.getPrincipal()).thenReturn(principal2);
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(auth1, DashboardPeriod.DAY, null, "Europe/Berlin");
            controller.getDashboard(auth2, DashboardPeriod.DAY, null, "Europe/Berlin");

            verify(dashboardService).getDashboard(eq(restaurant1), any(), any(), any());
            verify(dashboardService).getDashboard(eq(restaurant2), any(), any(), any());
        }
    }

    // ==================== Edge Case Tests ====================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle very long restaurant name")
        void shouldHandleVeryLongRestaurantName() {
            String longName = "A".repeat(500);
            OwnerDashboardResponse longNameResponse = new OwnerDashboardResponse(
                    restaurantId, longName, DashboardPeriod.DAY,
                    mockResponse.dateRange(), mockResponse.overview(),
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()
            );
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(longNameResponse);

            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            assertThat(response.restaurantName()).hasSize(500);
        }

        @Test
        @DisplayName("Should handle very large restaurantId")
        void shouldHandleVeryLargeRestaurantId() {
            Long largeId = Long.MAX_VALUE;
            AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_OWNER", largeId);
            when(authentication.getPrincipal()).thenReturn(principal);
            when(dashboardService.getDashboard(any(), any(), any(), any())).thenReturn(mockResponse);

            controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Europe/Berlin");

            verify(dashboardService).getDashboard(eq(largeId), any(), any(), any());
        }
    }
}


