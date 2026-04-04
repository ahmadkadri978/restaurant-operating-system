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
@DisplayName("KitchenOrderController Unit Tests")
class KitchenOrderControllerTest {

    @Mock private StaffOrderService staffOrderService;
    @Mock private Authentication authentication;

    private KitchenOrderController controller;

    private static final Long RESTAURANT_ID = 1L;
    private static final Long ORDER_ID = 100L;

    @BeforeEach
    void setUp() {
        controller = new KitchenOrderController(staffOrderService);
        AuthPrincipal principal = new AuthPrincipal(20L, "KITCHEN", RESTAURANT_ID);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    // ==================== Mark Done ====================

    @Nested
    @DisplayName("Mark Done")
    class MarkDoneTests {

        @Test
        @DisplayName("Should delegate to service and return DONE status")
        void shouldDelegateAndReturn() {
            OrderStatusResponse expected = new OrderStatusResponse(ORDER_ID, 1,
                    OrderStatus.DONE, Instant.now().minusSeconds(60), Instant.now(), null);
            when(staffOrderService.markDone(RESTAURANT_ID, ORDER_ID)).thenReturn(expected);

            OrderStatusResponse result = controller.markDone(authentication, ORDER_ID);

            assertThat(result.status()).isEqualTo(OrderStatus.DONE);
            assertThat(result.doneAt()).isNotNull();
            verify(staffOrderService).markDone(RESTAURANT_ID, ORDER_ID);
        }

        @Test
        @DisplayName("Should extract restaurantId from JWT principal")
        void shouldExtractRestaurantId() {
            OrderStatusResponse expected = new OrderStatusResponse(ORDER_ID, 1,
                    OrderStatus.DONE, null, Instant.now(), null);
            when(staffOrderService.markDone(any(), any())).thenReturn(expected);

            controller.markDone(authentication, ORDER_ID);

            verify(staffOrderService).markDone(RESTAURANT_ID, ORDER_ID);
        }
    }

    // ==================== Get Order Details ====================

    @Nested
    @DisplayName("Get Order Details")
    class GetOrderDetailsTests {

        @Test
        @DisplayName("Should return order details")
        void shouldReturnOrderDetails() {
            OrderDetailsDto expected = new OrderDetailsDto(ORDER_ID, 5, 2, Instant.now(),
                    new BigDecimal("35.00"), null, List.of(), OrderStatus.SENT_TO_KITCHEN);
            when(staffOrderService.getOrderDetails(RESTAURANT_ID, ORDER_ID)).thenReturn(expected);

            OrderDetailsDto result = controller.getOrderDetails(authentication, ORDER_ID);

            assertThat(result.orderId()).isEqualTo(ORDER_ID);
            assertThat(result.tableNumber()).isEqualTo(2);
            verify(staffOrderService).getOrderDetails(RESTAURANT_ID, ORDER_ID);
        }
    }

    // ==================== Get Sent To Kitchen Orders ====================

    @Nested
    @DisplayName("Get Sent To Kitchen Orders")
    class GetSentToKitchenOrdersTests {

        @Test
        @DisplayName("Should return paged SENT_TO_KITCHEN orders")
        void shouldReturnPagedOrders() {
            OrderSummaryDto dto = new OrderSummaryDto(1L, 1, 3, Instant.now(),
                    new BigDecimal("20.00"), OrderStatus.SENT_TO_KITCHEN);
            when(staffOrderService.getOrdersByStatus(eq(RESTAURANT_ID), eq(OrderStatus.SENT_TO_KITCHEN),
                    any(Pageable.class), isNull()))
                    .thenReturn(new PageImpl<>(List.of(dto)));

            Page<OrderSummaryDto> result = controller.getSentToKitchenOrders(authentication, 0, 20, null);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).status()).isEqualTo(OrderStatus.SENT_TO_KITCHEN);
        }

        @Test
        @DisplayName("Should pass since parameter for polling")
        void shouldPassSinceParam() {
            Instant since = Instant.now().minusSeconds(10);
            when(staffOrderService.getOrdersByStatus(eq(RESTAURANT_ID), eq(OrderStatus.SENT_TO_KITCHEN),
                    any(), eq(since)))
                    .thenReturn(new PageImpl<>(List.of()));

            controller.getSentToKitchenOrders(authentication, 0, 20, since);

            verify(staffOrderService).getOrdersByStatus(eq(RESTAURANT_ID),
                    eq(OrderStatus.SENT_TO_KITCHEN), any(), eq(since));
        }
    }
}

