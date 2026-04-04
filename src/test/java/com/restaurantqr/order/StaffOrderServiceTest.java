package com.restaurantqr.order;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.order.staff.*;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.sse.SseOutboundEvent;
import com.restaurantqr.table.RestaurantTable;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StaffOrderService Unit Tests")
class StaffOrderServiceTest {

    @Mock private CustomerOrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private StaffOrderService service;

    private Restaurant testRestaurant;
    private RestaurantTable testTable;
    private static final Long RESTAURANT_ID = 1L;
    private static final Long ORDER_ID = 100L;

    @BeforeEach
    void setUp() {
        service = new StaffOrderService(orderRepository, orderItemRepository, eventPublisher);

        testRestaurant = new Restaurant();
        testRestaurant.setId(RESTAURANT_ID);
        testRestaurant.setName("Test Restaurant");

        testTable = new RestaurantTable();
        testTable.setId(5L);
        testTable.setTableNumber(3);
        testTable.setRestaurant(testRestaurant);
    }

    // ==================== Mark Sent To Kitchen ====================

    @Nested
    @DisplayName("Mark Sent To Kitchen")
    class MarkSentToKitchenTests {

        @Test
        @DisplayName("Should transition NEW → SENT_TO_KITCHEN")
        void shouldTransitionNewToSentToKitchen() {
            CustomerOrder order = buildOrder(OrderStatus.NEW);
            when(orderRepository.findByIdAndRestaurantIdForUpdate(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.of(order));

            OrderStatusResponse result = service.markSentToKitchen(RESTAURANT_ID, ORDER_ID);

            assertThat(result.status()).isEqualTo(OrderStatus.SENT_TO_KITCHEN);
            assertThat(result.sentToKitchenAt()).isNotNull();
            verify(eventPublisher).publishEvent(any(SseOutboundEvent.class));
        }

        @Test
        @DisplayName("Should be idempotent for SENT_TO_KITCHEN orders")
        void shouldBeIdempotentForSentToKitchen() {
            CustomerOrder order = buildOrder(OrderStatus.SENT_TO_KITCHEN);
            order.setSentToKitchenAt(Instant.now());
            when(orderRepository.findByIdAndRestaurantIdForUpdate(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.of(order));

            OrderStatusResponse result = service.markSentToKitchen(RESTAURANT_ID, ORDER_ID);

            assertThat(result.status()).isEqualTo(OrderStatus.SENT_TO_KITCHEN);
            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any(SseOutboundEvent.class));
        }

        @Test
        @DisplayName("Should throw when order is DONE")
        void shouldThrowWhenOrderDone() {
            CustomerOrder order = buildOrder(OrderStatus.DONE);
            when(orderRepository.findByIdAndRestaurantIdForUpdate(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.markSentToKitchen(RESTAURANT_ID, ORDER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Only NEW orders can be sent to kitchen");
        }

        @Test
        @DisplayName("Should throw when order not found")
        void shouldThrowWhenNotFound() {
            when(orderRepository.findByIdAndRestaurantIdForUpdate(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markSentToKitchen(RESTAURANT_ID, ORDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order not found");
        }

        @Test
        @DisplayName("Should throw when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            assertThatThrownBy(() -> service.markSentToKitchen(null, ORDER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("required");
        }
    }

    // ==================== Mark Done ====================

    @Nested
    @DisplayName("Mark Done")
    class MarkDoneTests {

        @Test
        @DisplayName("Should transition SENT_TO_KITCHEN → DONE")
        void shouldTransitionSentToKitchenToDone() {
            CustomerOrder order = buildOrder(OrderStatus.SENT_TO_KITCHEN);
            order.setSentToKitchenAt(Instant.now().minusSeconds(60));
            when(orderRepository.findByIdAndRestaurantIdForUpdate(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.of(order));

            OrderStatusResponse result = service.markDone(RESTAURANT_ID, ORDER_ID);

            assertThat(result.status()).isEqualTo(OrderStatus.DONE);
            assertThat(result.doneAt()).isNotNull();
            verify(eventPublisher).publishEvent(any(SseOutboundEvent.class));
        }

        @Test
        @DisplayName("Should be idempotent for DONE orders")
        void shouldBeIdempotentForDone() {
            CustomerOrder order = buildOrder(OrderStatus.DONE);
            order.setDoneAt(Instant.now());
            when(orderRepository.findByIdAndRestaurantIdForUpdate(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.of(order));

            OrderStatusResponse result = service.markDone(RESTAURANT_ID, ORDER_ID);

            assertThat(result.status()).isEqualTo(OrderStatus.DONE);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when order is NEW (not SENT_TO_KITCHEN)")
        void shouldThrowWhenOrderNew() {
            CustomerOrder order = buildOrder(OrderStatus.NEW);
            when(orderRepository.findByIdAndRestaurantIdForUpdate(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.markDone(RESTAURANT_ID, ORDER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("must be SENT_TO_KITCHEN");
        }

        @Test
        @DisplayName("Should throw when order is CANCELLED")
        void shouldThrowWhenOrderCancelled() {
            CustomerOrder order = buildOrder(OrderStatus.CANCELLED);
            when(orderRepository.findByIdAndRestaurantIdForUpdate(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.markDone(RESTAURANT_ID, ORDER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("must be SENT_TO_KITCHEN");
        }
    }

    // ==================== Mark Cancelled ====================

    @Nested
    @DisplayName("Mark Cancelled")
    class MarkCancelledTests {

        @Test
        @DisplayName("Should transition NEW → CANCELLED")
        void shouldTransitionNewToCancelled() {
            CustomerOrder order = buildOrder(OrderStatus.NEW);
            when(orderRepository.findByIdAndRestaurantIdForUpdate(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.of(order));

            OrderStatusResponse result = service.markCancelled(RESTAURANT_ID, ORDER_ID);

            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(result.cancelledAt()).isNotNull();
            verify(eventPublisher).publishEvent(any(SseOutboundEvent.class));
        }

        @Test
        @DisplayName("Should be idempotent for CANCELLED orders")
        void shouldBeIdempotentForCancelled() {
            CustomerOrder order = buildOrder(OrderStatus.CANCELLED);
            order.setCancelledAt(Instant.now());
            when(orderRepository.findByIdAndRestaurantIdForUpdate(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.of(order));

            OrderStatusResponse result = service.markCancelled(RESTAURANT_ID, ORDER_ID);

            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when order is SENT_TO_KITCHEN")
        void shouldThrowWhenOrderSentToKitchen() {
            CustomerOrder order = buildOrder(OrderStatus.SENT_TO_KITCHEN);
            when(orderRepository.findByIdAndRestaurantIdForUpdate(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.markCancelled(RESTAURANT_ID, ORDER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("must be NEW");
        }

        @Test
        @DisplayName("Should throw when order is DONE")
        void shouldThrowWhenOrderIsDone() {
            CustomerOrder order = buildOrder(OrderStatus.DONE);
            when(orderRepository.findByIdAndRestaurantIdForUpdate(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.markCancelled(RESTAURANT_ID, ORDER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("must be NEW");
        }
    }

    // ==================== Get Order Details ====================

    @Nested
    @DisplayName("Get Order Details")
    class GetOrderDetailsTests {

        @Test
        @DisplayName("Should return order details with items")
        void shouldReturnOrderDetailsWithItems() {
            CustomerOrder order = buildOrder(OrderStatus.NEW);
            when(orderRepository.findByIdAndRestaurantIdFetchTable(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.of(order));

            com.restaurantqr.menu.MenuItem menuItem = new com.restaurantqr.menu.MenuItem();
            menuItem.setId(10L);
            menuItem.setName("Falafel");

            OrderItem oi = new OrderItem();
            oi.setMenuItem(menuItem);
            oi.setQuantity(2);
            oi.setUnitPrice(new BigDecimal("5.00"));
            oi.setTotalPrice(new BigDecimal("10.00"));
            oi.setNote("Extra tahini");

            when(orderItemRepository.findByOrderIdWithMenuItem(ORDER_ID)).thenReturn(List.of(oi));

            OrderDetailsDto result = service.getOrderDetails(RESTAURANT_ID, ORDER_ID);

            assertThat(result.orderId()).isEqualTo(ORDER_ID);
            assertThat(result.tableNumber()).isEqualTo(3);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).itemName()).isEqualTo("Falafel");
            assertThat(result.items().get(0).quantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should throw when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            assertThatThrownBy(() -> service.getOrderDetails(null, ORDER_ID))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should throw when order not found")
        void shouldThrowWhenOrderNotFound() {
            when(orderRepository.findByIdAndRestaurantIdFetchTable(RESTAURANT_ID, ORDER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getOrderDetails(RESTAURANT_ID, ORDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Get Orders By Status ====================

    @Nested
    @DisplayName("Get Orders By Status")
    class GetOrdersByStatusTests {

        @Test
        @DisplayName("Should return paged orders without since filter")
        void shouldReturnPagedOrders() {
            Pageable pageable = PageRequest.of(0, 20);
            CustomerOrder order = buildOrder(OrderStatus.NEW);

            when(orderRepository.findPageByRestaurantAndStatusFetchTable(RESTAURANT_ID, OrderStatus.NEW, pageable))
                    .thenReturn(new PageImpl<>(List.of(order)));

            Page<OrderSummaryDto> result = service.getOrdersByStatus(RESTAURANT_ID, OrderStatus.NEW, pageable, null);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).status()).isEqualTo(OrderStatus.NEW);
        }

        @Test
        @DisplayName("Should filter with since parameter")
        void shouldFilterWithSince() {
            Pageable pageable = PageRequest.of(0, 20);
            Instant since = Instant.now().minusSeconds(30);

            when(orderRepository.findPageByRestaurantAndStatusAndCreatedAtAfterFetchTable(
                    RESTAURANT_ID, OrderStatus.NEW, since, pageable))
                    .thenReturn(new PageImpl<>(List.of()));

            Page<OrderSummaryDto> result = service.getOrdersByStatus(RESTAURANT_ID, OrderStatus.NEW, pageable, since);

            assertThat(result.getContent()).isEmpty();
            verify(orderRepository).findPageByRestaurantAndStatusAndCreatedAtAfterFetchTable(
                    RESTAURANT_ID, OrderStatus.NEW, since, pageable);
        }

        @Test
        @DisplayName("Should throw when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            assertThatThrownBy(() -> service.getOrdersByStatus(null, OrderStatus.NEW, PageRequest.of(0, 20), null))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should throw when status is null")
        void shouldThrowWhenStatusNull() {
            assertThatThrownBy(() -> service.getOrdersByStatus(RESTAURANT_ID, null, PageRequest.of(0, 20), null))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    // ==================== Helpers ====================

    private CustomerOrder buildOrder(OrderStatus status) {
        CustomerOrder order = new CustomerOrder();
        order.setId(ORDER_ID);
        order.setRestaurant(testRestaurant);
        order.setTable(testTable);
        order.setStatus(status);
        order.setRestaurantOrderNumber(1);
        order.setTotalAmount(new BigDecimal("20.00"));
        order.setCreatedAt(Instant.now());
        return order;
    }
}


