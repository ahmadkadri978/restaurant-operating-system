package com.restaurantqr.order;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.order.staff.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StaffOrderController Unit Tests")
class StaffOrderControllerTest {

    @Mock private StaffOrderService staffOrderService;
    @Mock private Authentication authentication;

    private StaffOrderController controller;

    private static final Long RESTAURANT_ID = 1L;
    private static final Long ORDER_ID = 100L;

    @BeforeEach
    void setUp() {
        controller = new StaffOrderController(staffOrderService);
        AuthPrincipal principal = new AuthPrincipal(10L, "MANAGER", RESTAURANT_ID);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    // ==================== Get New Orders ====================

    @Nested
    @DisplayName("Get New Orders")
    class GetNewOrdersTests {

        @Test
        @DisplayName("Should return paged NEW orders")
        void shouldReturnPagedNewOrders() {
            OrderSummaryDto dto = new OrderSummaryDto(1L, 1, 3, Instant.now(),
                    new BigDecimal("20.00"), OrderStatus.NEW);
            when(staffOrderService.getOrdersByStatus(eq(RESTAURANT_ID), eq(OrderStatus.NEW), any(Pageable.class), isNull()))
                    .thenReturn(new PageImpl<>(List.of(dto)));

            Page<OrderSummaryDto> result = controller.getNewOrders(authentication, 0, 20, null);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).status()).isEqualTo(OrderStatus.NEW);
        }

        @Test
        @DisplayName("Should pass since parameter to service")
        void shouldPassSinceParam() {
            Instant since = Instant.now().minusSeconds(30);
            when(staffOrderService.getOrdersByStatus(eq(RESTAURANT_ID), eq(OrderStatus.NEW), any(), eq(since)))
                    .thenReturn(new PageImpl<>(List.of()));

            controller.getNewOrders(authentication, 0, 20, since);

            verify(staffOrderService).getOrdersByStatus(eq(RESTAURANT_ID), eq(OrderStatus.NEW), any(), eq(since));
        }

        @Test
        @DisplayName("Should extract restaurantId from auth principal")
        void shouldExtractRestaurantId() {
            when(staffOrderService.getOrdersByStatus(any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            controller.getNewOrders(authentication, 0, 10, null);

            verify(staffOrderService).getOrdersByStatus(eq(RESTAURANT_ID), any(), any(), any());
        }
    }

    // ==================== Get Order Details ====================

    @Nested
    @DisplayName("Get Order Details")
    class GetOrderDetailsTests {

        @Test
        @DisplayName("Should delegate to service with correct params")
        void shouldDelegateToService() {
            OrderDetailsDto expected = new OrderDetailsDto(ORDER_ID, 1, 3, Instant.now(),
                    new BigDecimal("20.00"), null, List.of(), OrderStatus.NEW);
            when(staffOrderService.getOrderDetails(RESTAURANT_ID, ORDER_ID)).thenReturn(expected);

            OrderDetailsDto result = controller.getOrderDetails(authentication, ORDER_ID);

            assertThat(result.orderId()).isEqualTo(ORDER_ID);
            verify(staffOrderService).getOrderDetails(RESTAURANT_ID, ORDER_ID);
        }
    }

    // ==================== Mark Sent To Kitchen ====================

    @Nested
    @DisplayName("Mark Sent To Kitchen")
    class MarkSentToKitchenTests {

        @Test
        @DisplayName("Should delegate to service and return status response")
        void shouldDelegateAndReturn() {
            OrderStatusResponse expected = new OrderStatusResponse(ORDER_ID, 1,
                    OrderStatus.SENT_TO_KITCHEN, Instant.now(), null, null);
            when(staffOrderService.markSentToKitchen(RESTAURANT_ID, ORDER_ID)).thenReturn(expected);

            OrderStatusResponse result = controller.markSentToKitchen(authentication, ORDER_ID);

            assertThat(result.status()).isEqualTo(OrderStatus.SENT_TO_KITCHEN);
            verify(staffOrderService).markSentToKitchen(RESTAURANT_ID, ORDER_ID);
        }
    }

    // ==================== Cancel Order ====================

    @Nested
    @DisplayName("Cancel Order")
    class CancelOrderTests {

        @Test
        @DisplayName("Should delegate to service and return cancelled status")
        void shouldDelegateAndReturn() {
            OrderStatusResponse expected = new OrderStatusResponse(ORDER_ID, 1,
                    OrderStatus.CANCELLED, null, null, Instant.now());
            when(staffOrderService.markCancelled(RESTAURANT_ID, ORDER_ID)).thenReturn(expected);

            OrderStatusResponse result = controller.cancelOrder(authentication, ORDER_ID);

            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
            verify(staffOrderService).markCancelled(RESTAURANT_ID, ORDER_ID);
        }
    }
}

