package com.restaurantqr.owner;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-End Integration Tests for Owner Package.
 * Tests complete dashboard data flow through controller layer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Owner Package E2E Integration Tests")
class OwnerE2EIntegrationTest {

    private OwnerController controller;

    @Mock
    private OwnerDashboardService dashboardService;

    private Long restaurantId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        restaurantId = 1L;

        // Create controller with mocked service
        controller = new OwnerController(dashboardService);

        // Setup authentication
        AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_OWNER", restaurantId);
        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    // ==================== Security E2E Tests ====================

    @Nested
    @DisplayName("Security E2E Tests")
    class SecurityE2ETests {

        @Test
        @DisplayName("E2E: Should extract restaurantId from JWT principal only")
        void shouldExtractRestaurantIdFromJwtOnly() {
            // Given
            OwnerDashboardResponse mockResponse = createMockDashboardResponse();
            when(dashboardService.getDashboard(eq(restaurantId), any(), any(), any())).thenReturn(mockResponse);

            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            assertThat(response.restaurantId()).isEqualTo(restaurantId);

            // Verify service was called with correct restaurantId
            verify(dashboardService).getDashboard(eq(restaurantId), any(), any(), any());
        }

        @Test
        @DisplayName("E2E: Should throw when authentication is null")
        void shouldThrowWhenAuthenticationIsNull() {
            // When & Then
            assertThatThrownBy(() -> controller.getDashboard(null, DashboardPeriod.DAY, null, "Europe/Berlin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Authentication principal is required");
        }

        @Test
        @DisplayName("E2E: Should throw when principal is null")
        void shouldThrowWhenPrincipalIsNull() {
            // Given - authentication with null principal
            Authentication nullPrincipalAuth = mock(Authentication.class);
            when(nullPrincipalAuth.getPrincipal()).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> controller.getDashboard(nullPrincipalAuth, DashboardPeriod.DAY, null, "Europe/Berlin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Authentication principal is required");
        }

        @Test
        @DisplayName("E2E: Should throw when principal has no restaurantId")
        void shouldThrowWhenPrincipalHasNoRestaurantId() {
            // Given - principal without restaurantId
            AuthPrincipal noRestaurantPrincipal = new AuthPrincipal(1L, "ROLE_OWNER", null);
            Authentication noRestaurantAuth = mock(Authentication.class);
            when(noRestaurantAuth.getPrincipal()).thenReturn(noRestaurantPrincipal);

            // When & Then
            assertThatThrownBy(() -> controller.getDashboard(noRestaurantAuth, DashboardPeriod.DAY, null, "Europe/Berlin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Restaurant ID not found");
        }
    }

    // ==================== Dashboard Period E2E Tests ====================

    @Nested
    @DisplayName("Dashboard Period E2E Tests")
    class DashboardPeriodE2ETests {

        @Test
        @DisplayName("E2E: Should pass DAY period to service")
        void shouldPassDayPeriodToService() {
            // Given
            when(dashboardService.getDashboard(anyLong(), any(), any(), any()))
                    .thenReturn(createMockDashboardResponse());

            // When
            controller.getDashboard(authentication, DashboardPeriod.DAY, null, "Europe/Berlin");

            // Then
            verify(dashboardService).getDashboard(eq(restaurantId), eq(DashboardPeriod.DAY), isNull(), eq("Europe/Berlin"));
        }

        @Test
        @DisplayName("E2E: Should pass WEEK period to service")
        void shouldPassWeekPeriodToService() {
            // Given
            when(dashboardService.getDashboard(anyLong(), any(), any(), any()))
                    .thenReturn(createMockDashboardResponse());

            // When
            controller.getDashboard(authentication, DashboardPeriod.WEEK, null, "Europe/Berlin");

            // Then
            verify(dashboardService).getDashboard(eq(restaurantId), eq(DashboardPeriod.WEEK), isNull(), any());
        }

        @Test
        @DisplayName("E2E: Should pass MONTH period to service")
        void shouldPassMonthPeriodToService() {
            // Given
            when(dashboardService.getDashboard(anyLong(), any(), any(), any()))
                    .thenReturn(createMockDashboardResponse());

            // When
            controller.getDashboard(authentication, DashboardPeriod.MONTH, null, "Europe/Berlin");

            // Then
            verify(dashboardService).getDashboard(eq(restaurantId), eq(DashboardPeriod.MONTH), isNull(), any());
        }

        @Test
        @DisplayName("E2E: Should pass YEAR period to service")
        void shouldPassYearPeriodToService() {
            // Given
            when(dashboardService.getDashboard(anyLong(), any(), any(), any()))
                    .thenReturn(createMockDashboardResponse());

            // When
            controller.getDashboard(authentication, DashboardPeriod.YEAR, null, "Europe/Berlin");

            // Then
            verify(dashboardService).getDashboard(eq(restaurantId), eq(DashboardPeriod.YEAR), isNull(), any());
        }

        @Test
        @DisplayName("E2E: Should handle anchorDate parameter")
        void shouldHandleAnchorDateParameter() {
            // Given
            LocalDate anchorDate = LocalDate.of(2026, 3, 15);
            when(dashboardService.getDashboard(anyLong(), any(), any(), any()))
                    .thenReturn(createMockDashboardResponse());

            // When
            controller.getDashboard(authentication, DashboardPeriod.DAY, anchorDate, "Europe/Berlin");

            // Then
            verify(dashboardService).getDashboard(eq(restaurantId), any(), eq(anchorDate), any());
        }
    }

    // ==================== Timezone E2E Tests ====================

    @Nested
    @DisplayName("Timezone E2E Tests")
    class TimezoneE2ETests {

        @Test
        @DisplayName("E2E: Should pass timezone to service")
        void shouldPassTimezoneToService() {
            // Given
            when(dashboardService.getDashboard(anyLong(), any(), any(), any()))
                    .thenReturn(createMockDashboardResponse());

            // When
            controller.getDashboard(authentication, DashboardPeriod.DAY, null, "America/New_York");

            // Then
            verify(dashboardService).getDashboard(anyLong(), any(), any(), eq("America/New_York"));
        }

        @Test
        @DisplayName("E2E: Should propagate invalid timezone exception")
        void shouldPropagateInvalidTimezoneException() {
            // Given
            when(dashboardService.getDashboard(anyLong(), any(), any(), eq("Invalid/Timezone")))
                    .thenThrow(new BusinessRuleException("Invalid timezone 'Invalid/Timezone'"));

            // When & Then
            assertThatThrownBy(() -> controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Invalid/Timezone"
            ))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid timezone");
        }

        @Test
        @DisplayName("E2E: Should accept various valid timezones")
        void shouldAcceptVariousValidTimezones() {
            // Given
            when(dashboardService.getDashboard(anyLong(), any(), any(), any()))
                    .thenReturn(createMockDashboardResponse());

            List<String> validTimezones = List.of(
                    "Europe/Berlin",
                    "America/New_York",
                    "Asia/Tokyo",
                    "Asia/Dubai"
            );

            // When & Then - should not throw
            for (String zone : validTimezones) {
                assertThatCode(() -> controller.getDashboard(
                        authentication, DashboardPeriod.DAY, null, zone
                )).doesNotThrowAnyException();
            }
        }
    }

    // ==================== Response Structure E2E Tests ====================

    @Nested
    @DisplayName("Response Structure E2E Tests")
    class ResponseStructureE2ETests {

        @Test
        @DisplayName("E2E: Should return complete dashboard response structure")
        void shouldReturnCompleteDashboardStructure() {
            // Given
            OwnerDashboardResponse mockResponse = createMockDashboardResponse();
            when(dashboardService.getDashboard(anyLong(), any(), any(), any())).thenReturn(mockResponse);

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
            assertThat(response.overview()).isNotNull();
            assertThat(response.revenueTrend()).isNotNull();
            assertThat(response.ordersByStatus()).isNotNull();
            assertThat(response.topItemsByQuantity()).isNotNull();
            assertThat(response.topItemsByRevenue()).isNotNull();
            assertThat(response.topTables()).isNotNull();
            assertThat(response.serviceCallsByType()).isNotNull();
            assertThat(response.peakHours()).isNotNull();
            assertThat(response.sessionAnalytics()).isNotNull();
        }

        @Test
        @DisplayName("E2E: Should return correct restaurant info")
        void shouldReturnCorrectRestaurantInfo() {
            // Given
            OwnerDashboardResponse mockResponse = createMockDashboardResponse();
            when(dashboardService.getDashboard(anyLong(), any(), any(), any())).thenReturn(mockResponse);

            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            assertThat(response.restaurantId()).isEqualTo(1L);
            assertThat(response.restaurantName()).isEqualTo("Test Restaurant");
        }

        @Test
        @DisplayName("E2E: Should return correct period in response")
        void shouldReturnCorrectPeriod() {
            // Given
            OwnerDashboardResponse mockResponse = createMockDashboardResponse();
            when(dashboardService.getDashboard(anyLong(), any(), any(), any())).thenReturn(mockResponse);

            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            assertThat(response.period()).isEqualTo(DashboardPeriod.DAY);
        }

        @Test
        @DisplayName("E2E: Should return date range in response")
        void shouldReturnDateRangeInResponse() {
            // Given
            OwnerDashboardResponse mockResponse = createMockDashboardResponse();
            when(dashboardService.getDashboard(anyLong(), any(), any(), any())).thenReturn(mockResponse);

            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            assertThat(response.dateRange()).isNotNull();
            assertThat(response.dateRange().startDate()).isNotNull();
            assertThat(response.dateRange().endDate()).isNotNull();
            assertThat(response.dateRange().startDate()).isBefore(response.dateRange().endDate());
        }
    }

    // ==================== Overview Statistics E2E Tests ====================

    @Nested
    @DisplayName("Overview Statistics E2E Tests")
    class OverviewStatisticsE2ETests {

        @Test
        @DisplayName("E2E: Should return overview with all metrics")
        void shouldReturnOverviewWithAllMetrics() {
            // Given
            OwnerDashboardResponse mockResponse = createMockDashboardResponse();
            when(dashboardService.getDashboard(anyLong(), any(), any(), any())).thenReturn(mockResponse);

            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            OverviewDto overview = response.overview();
            assertThat(overview).isNotNull();
            assertThat(overview.totalOrders()).isEqualTo(150L);
            assertThat(overview.newOrders()).isNotNull();
            assertThat(overview.doneOrders()).isNotNull();
            assertThat(overview.grossRevenue()).isNotNull();
            assertThat(overview.averageOrderValue()).isNotNull();
        }
    }

    // ==================== Service Call E2E Tests ====================

    @Nested
    @DisplayName("Service Call E2E Tests")
    class ServiceCallE2ETests {

        @Test
        @DisplayName("E2E: Should return service calls by type")
        void shouldReturnServiceCallsByType() {
            // Given
            OwnerDashboardResponse mockResponse = createMockDashboardResponse();
            when(dashboardService.getDashboard(anyLong(), any(), any(), any())).thenReturn(mockResponse);

            // When
            OwnerDashboardResponse response = controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            );

            // Then
            assertThat(response.serviceCallsByType()).isNotNull();
            assertThat(response.serviceCallsByType()).hasSize(3);
        }
    }

    // ==================== Exception Handling E2E Tests ====================

    @Nested
    @DisplayName("Exception Handling E2E Tests")
    class ExceptionHandlingE2ETests {

        @Test
        @DisplayName("E2E: Should propagate BusinessRuleException from service")
        void shouldPropagateBusinessRuleException() {
            // Given
            when(dashboardService.getDashboard(anyLong(), any(), any(), any()))
                    .thenThrow(new BusinessRuleException("Restaurant not found: 1"));

            // When & Then
            assertThatThrownBy(() -> controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            ))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Restaurant not found");
        }

        @Test
        @DisplayName("E2E: Should propagate RuntimeException from service")
        void shouldPropagateRuntimeException() {
            // Given
            when(dashboardService.getDashboard(anyLong(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            assertThatThrownBy(() -> controller.getDashboard(
                    authentication, DashboardPeriod.DAY, null, "Europe/Berlin"
            ))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Database connection failed");
        }
    }

    // ==================== Multi Restaurant E2E Tests ====================

    @Nested
    @DisplayName("Multi Restaurant E2E Tests")
    class MultiRestaurantE2ETests {

        @Test
        @DisplayName("E2E: Should use restaurantId from different JWT principals")
        void shouldUseDifferentRestaurantIds() {
            // Given - two different owners
            Long restaurant1Id = 10L;
            Long restaurant2Id = 20L;

            AuthPrincipal principal1 = new AuthPrincipal(1L, "ROLE_OWNER", restaurant1Id);
            AuthPrincipal principal2 = new AuthPrincipal(2L, "ROLE_OWNER", restaurant2Id);

            Authentication auth1 = mock(Authentication.class);
            Authentication auth2 = mock(Authentication.class);
            when(auth1.getPrincipal()).thenReturn(principal1);
            when(auth2.getPrincipal()).thenReturn(principal2);

            OwnerDashboardResponse response1 = createMockDashboardResponseWithRestaurant(restaurant1Id, "Restaurant 1");
            OwnerDashboardResponse response2 = createMockDashboardResponseWithRestaurant(restaurant2Id, "Restaurant 2");

            when(dashboardService.getDashboard(eq(restaurant1Id), any(), any(), any())).thenReturn(response1);
            when(dashboardService.getDashboard(eq(restaurant2Id), any(), any(), any())).thenReturn(response2);

            // When
            OwnerDashboardResponse result1 = controller.getDashboard(auth1, DashboardPeriod.DAY, null, "Europe/Berlin");
            OwnerDashboardResponse result2 = controller.getDashboard(auth2, DashboardPeriod.DAY, null, "Europe/Berlin");

            // Then
            assertThat(result1.restaurantId()).isEqualTo(restaurant1Id);
            assertThat(result2.restaurantId()).isEqualTo(restaurant2Id);
            assertThat(result1.restaurantName()).isEqualTo("Restaurant 1");
            assertThat(result2.restaurantName()).isEqualTo("Restaurant 2");
        }
    }

    // ==================== Helper Methods ====================

    private OwnerDashboardResponse createMockDashboardResponse() {
        return createMockDashboardResponseWithRestaurant(restaurantId, "Test Restaurant");
    }

    private OwnerDashboardResponse createMockDashboardResponseWithRestaurant(Long id, String name) {
        Instant now = Instant.now();
        Instant startOfDay = now.minusSeconds(3600);

        return new OwnerDashboardResponse(
                id,
                name,
                DashboardPeriod.DAY,
                new DateRangeDto(startOfDay, now, DashboardPeriod.DAY),
                new OverviewDto(
                        150L, 10L, 15L, 120L, 5L,
                        new BigDecimal("96.00"), new BigDecimal("3.33"),
                        new BigDecimal("5000.00"), new BigDecimal("41.67"),
                        20L, 15L, 45L,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
                ),
                List.of(new RevenuePointDto(startOfDay, new BigDecimal("5000.00"), 150L)),
                List.of(
                        new StatusCountDto(com.restaurantqr.order.staff.OrderStatus.NEW, 10L),
                        new StatusCountDto(com.restaurantqr.order.staff.OrderStatus.DONE, 120L)
                ),
                List.of(new TopItemDto(1L, "Burger", 50L, new BigDecimal("625.00"))),
                List.of(new TopItemDto(1L, "Burger", 50L, new BigDecimal("625.00"))),
                List.of(new TopTableDto(1L, 5, 30L, new BigDecimal("375.00"))),
                List.of(
                        new ServiceCallTypeCountDto(com.restaurantqr.servicecall.CallType.WAITER, 25L),
                        new ServiceCallTypeCountDto(com.restaurantqr.servicecall.CallType.BILL, 15L),
                        new ServiceCallTypeCountDto(com.restaurantqr.servicecall.CallType.NARA, 5L)
                ),
                List.of(new PeakHourDto(12, 25L, new BigDecimal("300.00"))),
                new SessionAnalyticsDto(
                        85L, 3L, 82L, 47, 180, 5, 3854L,
                        List.of(), List.of(), List.of()
                ),
                List.of()
        );
    }
}

