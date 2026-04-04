package com.restaurantqr.order;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.RateLimitException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.menu.MenuItem;
import com.restaurantqr.menu.MenuItemRepository;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.sse.SseOutboundEvent;
import com.restaurantqr.table.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock private RestaurantTableRepository tableRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private CustomerOrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private RestaurantOrderCounterRepository orderCounterRepository;
    @Mock private PublicSessionService publicSessionService;
    @Mock private TableSessionRepository tableSessionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OrderService service;

    private Restaurant testRestaurant;
    private RestaurantTable testTable;
    private static final Long RESTAURANT_ID = 1L;
    private static final Long TABLE_ID = 5L;
    private static final String QR_TOKEN = "QR-TOKEN-1";
    private static final String SESSION_CODE = "1234";

    @BeforeEach
    void setUp() {
        service = new OrderService(
                tableRepository, menuItemRepository, orderRepository, orderItemRepository,
                orderCounterRepository, publicSessionService, tableSessionRepository, eventPublisher
        );

        testRestaurant = new Restaurant();
        testRestaurant.setId(RESTAURANT_ID);
        testRestaurant.setName("Test Restaurant");
        testRestaurant.setActive(true);

        testTable = new RestaurantTable();
        testTable.setId(TABLE_ID);
        testTable.setRestaurant(testRestaurant);
        testTable.setTableNumber(1);
        testTable.setQrToken(QR_TOKEN);
        testTable.setActive(true);
    }

    // ==================== Input Validation Tests ====================

    @Nested
    @DisplayName("Submit Order — Input Validation")
    class InputValidationTests {

        @Test
        @DisplayName("Should throw when qrToken is null")
        void shouldThrowWhenQrTokenNull() {
            assertThatThrownBy(() -> service.submitOrder(null, SESSION_CODE, buildRequest(1)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("qrToken is required");
        }

        @Test
        @DisplayName("Should throw when qrToken is blank")
        void shouldThrowWhenQrTokenBlank() {
            assertThatThrownBy(() -> service.submitOrder("  ", SESSION_CODE, buildRequest(1)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("qrToken is required");
        }

        @Test
        @DisplayName("Should throw when code is null")
        void shouldThrowWhenCodeNull() {
            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, null, buildRequest(1)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Session code is required");
        }

        @Test
        @DisplayName("Should throw when code is blank")
        void shouldThrowWhenCodeBlank() {
            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, "", buildRequest(1)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Session code is required");
        }

        @Test
        @DisplayName("Should throw when request is null")
        void shouldThrowWhenRequestNull() {
            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Request body is required");
        }

        @Test
        @DisplayName("Should throw when items list is null")
        void shouldThrowWhenItemsNull() {
            SubmitOrderRequest req = new SubmitOrderRequest(null, null);
            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Order items must not be empty");
        }

        @Test
        @DisplayName("Should throw when items list is empty")
        void shouldThrowWhenItemsEmpty() {
            SubmitOrderRequest req = new SubmitOrderRequest(List.of(), null);
            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Order items must not be empty");
        }

        @Test
        @DisplayName("Should throw when items exceed MAX_ITEMS_PER_ORDER (200)")
        void shouldThrowWhenTooManyItems() {
            List<SubmitOrderItemRequest> items = new ArrayList<>();
            for (int i = 0; i < 201; i++) {
                items.add(new SubmitOrderItemRequest((long) i, 1, null));
            }
            SubmitOrderRequest req = new SubmitOrderRequest(items, null);

            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Too many items");
        }
    }

    // ==================== Item Validation Tests ====================

    @Nested
    @DisplayName("Submit Order — Item Validation")
    class ItemValidationTests {

        @Test
        @DisplayName("Should throw when item has null menuItemId")
        void shouldThrowWhenMenuItemIdNull() {
            SubmitOrderItemRequest item = new SubmitOrderItemRequest(null, 1, null);
            SubmitOrderRequest req = new SubmitOrderRequest(List.of(item), null);

            setupValidSession();

            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("menuItemId is required");
        }

        @Test
        @DisplayName("Should throw when quantity is zero")
        void shouldThrowWhenQuantityZero() {
            SubmitOrderItemRequest item = new SubmitOrderItemRequest(1L, 0, null);
            SubmitOrderRequest req = new SubmitOrderRequest(List.of(item), null);

            setupValidSession();

            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("quantity must be greater than 0");
        }

        @Test
        @DisplayName("Should throw when quantity is negative")
        void shouldThrowWhenQuantityNegative() {
            SubmitOrderItemRequest item = new SubmitOrderItemRequest(1L, -1, null);
            SubmitOrderRequest req = new SubmitOrderRequest(List.of(item), null);

            setupValidSession();

            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("quantity must be greater than 0");
        }

        @Test
        @DisplayName("Should throw when quantity exceeds MAX_QTY_PER_ITEM (50)")
        void shouldThrowWhenQuantityTooLarge() {
            SubmitOrderItemRequest item = new SubmitOrderItemRequest(1L, 51, null);
            SubmitOrderRequest req = new SubmitOrderRequest(List.of(item), null);

            setupValidSession();

            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("quantity too large");
        }

        @Test
        @DisplayName("Should throw when menu item not found")
        void shouldThrowWhenMenuItemNotFound() {
            setupValidSession();
            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), eq(RESTAURANT_ID)))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, buildRequest(1)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Menu item not found");
        }

        @Test
        @DisplayName("Should throw when menu item is not available")
        void shouldThrowWhenMenuItemUnavailable() {
            setupValidSession();

            MenuItem unavailable = buildMenuItem(1L, "Item 1", new BigDecimal("10.00"), true, false);
            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), eq(RESTAURANT_ID)))
                    .thenReturn(List.of(unavailable));

            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, buildRequest(1)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("not available");
        }
    }

    // ==================== Rate Limit Tests ====================

    @Nested
    @DisplayName("Submit Order — Rate Limit")
    class RateLimitTests {

        @Test
        @DisplayName("Should throw RateLimitException when order exists within 60 seconds")
        void shouldThrowWhenRateLimited() {
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            when(tableRepository.findActiveByQrTokenForUpdate(QR_TOKEN)).thenReturn(Optional.of(testTable));
            when(orderRepository.existsByTableIdAndCreatedAtAfter(eq(TABLE_ID), any(Instant.class)))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, buildRequest(1)))
                    .isInstanceOf(RateLimitException.class);
        }

        @Test
        @DisplayName("Should allow order when no recent order exists")
        void shouldAllowWhenNoRecentOrder() {
            setupFullValidOrder();

            SubmitOrderResponse result = service.submitOrder(QR_TOKEN, SESSION_CODE, buildRequest(1));

            assertThat(result).isNotNull();
            assertThat(result.orderId()).isNotNull();
        }
    }

    // ==================== Restaurant Validation Tests ====================

    @Nested
    @DisplayName("Submit Order — Restaurant Validation")
    class RestaurantValidationTests {

        @Test
        @DisplayName("Should throw when QR token not found")
        void shouldThrowWhenQrTokenNotFound() {
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            when(tableRepository.findActiveByQrTokenForUpdate(QR_TOKEN)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, buildRequest(1)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Invalid or inactive QR token");
        }

        @Test
        @DisplayName("Should throw when restaurant is inactive")
        void shouldThrowWhenRestaurantInactive() {
            testRestaurant.setActive(false);
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            when(tableRepository.findActiveByQrTokenForUpdate(QR_TOKEN)).thenReturn(Optional.of(testTable));
            when(orderRepository.existsByTableIdAndCreatedAtAfter(eq(TABLE_ID), any())).thenReturn(false);

            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, buildRequest(1)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Restaurant is inactive");
        }

        @Test
        @DisplayName("Should throw when table has no restaurant")
        void shouldThrowWhenTableHasNoRestaurant() {
            testTable.setRestaurant(null);
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            when(tableRepository.findActiveByQrTokenForUpdate(QR_TOKEN)).thenReturn(Optional.of(testTable));
            when(orderRepository.existsByTableIdAndCreatedAtAfter(eq(TABLE_ID), any())).thenReturn(false);

            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, buildRequest(1)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Table has no restaurant");
        }
    }

    // ==================== Server-Side Price Calculation ====================

    @Nested
    @DisplayName("Submit Order — Price Calculation")
    class PriceCalculationTests {

        @Test
        @DisplayName("Should compute total amount from DB prices, not client input")
        void shouldComputeTotalFromDbPrices() {
            setupFullValidOrder();

            SubmitOrderResponse result = service.submitOrder(QR_TOKEN, SESSION_CODE, buildRequest(1));

            // Order is saved twice: initial save with ZERO, then update with total
            ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
            verify(orderRepository, times(2)).save(captor.capture());

            CustomerOrder finalOrder = captor.getAllValues().get(1);
            // 1 item * quantity 2 * price 10.00 = 20.00
            assertThat(finalOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        }

        @Test
        @DisplayName("Should calculate multi-item total correctly")
        void shouldCalculateMultiItemTotal() {
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            when(tableRepository.findActiveByQrTokenForUpdate(QR_TOKEN)).thenReturn(Optional.of(testTable));
            when(orderRepository.existsByTableIdAndCreatedAtAfter(eq(TABLE_ID), any())).thenReturn(false);

            MenuItem item1 = buildMenuItem(1L, "Item A", new BigDecimal("15.50"), true, true);
            MenuItem item2 = buildMenuItem(2L, "Item B", new BigDecimal("8.00"), true, true);
            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), eq(RESTAURANT_ID)))
                    .thenReturn(List.of(item1, item2));

            RestaurantOrderCounter counter = new RestaurantOrderCounter(RESTAURANT_ID, 0);
            when(orderCounterRepository.findByIdForUpdate(RESTAURANT_ID)).thenReturn(Optional.of(counter));
            when(tableSessionRepository.findOpenSessionByTableId(TABLE_ID)).thenReturn(Optional.empty());
            when(orderRepository.save(any())).thenAnswer(inv -> {
                CustomerOrder o = inv.getArgument(0);
                if (o.getId() == null) o.setId(100L);
                return o;
            });

            List<SubmitOrderItemRequest> items = List.of(
                    new SubmitOrderItemRequest(1L, 3, null),   // 15.50 * 3 = 46.50
                    new SubmitOrderItemRequest(2L, 1, null)    // 8.00 * 1 = 8.00
            );
            SubmitOrderRequest req = new SubmitOrderRequest(items, "Multi-item order");

            SubmitOrderResponse result = service.submitOrder(QR_TOKEN, SESSION_CODE, req);

            ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
            verify(orderRepository, times(2)).save(captor.capture());
            CustomerOrder finalOrder = captor.getAllValues().get(1);
            assertThat(finalOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("54.50"));
        }
    }

    // ==================== Order Counter Tests ====================

    @Nested
    @DisplayName("Submit Order — Order Counter")
    class OrderCounterTests {

        @Test
        @DisplayName("Should increment order counter atomically")
        void shouldIncrementOrderCounter() {
            setupFullValidOrder();

            service.submitOrder(QR_TOKEN, SESSION_CODE, buildRequest(1));

            ArgumentCaptor<RestaurantOrderCounter> captor =
                    ArgumentCaptor.forClass(RestaurantOrderCounter.class);
            verify(orderCounterRepository).save(captor.capture());
            assertThat(captor.getValue().getLastOrderNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should throw when order counter not found")
        void shouldThrowWhenCounterNotFound() {
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            when(tableRepository.findActiveByQrTokenForUpdate(QR_TOKEN)).thenReturn(Optional.of(testTable));
            when(orderRepository.existsByTableIdAndCreatedAtAfter(eq(TABLE_ID), any())).thenReturn(false);

            MenuItem item = buildMenuItem(1L, "Item", new BigDecimal("10.00"), true, true);
            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), eq(RESTAURANT_ID)))
                    .thenReturn(List.of(item));
            when(orderRepository.save(any())).thenAnswer(inv -> {
                CustomerOrder o = inv.getArgument(0);
                o.setId(100L);
                return o;
            });
            when(orderCounterRepository.findByIdForUpdate(RESTAURANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitOrder(QR_TOKEN, SESSION_CODE, buildRequest(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Order counter not found");
        }
    }

    // ==================== SSE Event Publishing Tests ====================

    @Nested
    @DisplayName("Submit Order — Event Publishing")
    class EventPublishingTests {

        @Test
        @DisplayName("Should publish SSE event after order creation")
        void shouldPublishSseEvent() {
            setupFullValidOrder();

            service.submitOrder(QR_TOKEN, SESSION_CODE, buildRequest(1));

            verify(eventPublisher).publishEvent(any(SseOutboundEvent.class));
        }
    }

    // ==================== Get Session Orders Tests ====================

    @Nested
    @DisplayName("Get Session Orders")
    class GetSessionOrdersTests {

        @Test
        @DisplayName("Should throw when qrToken is null")
        void shouldThrowWhenQrTokenNull() {
            assertThatThrownBy(() -> service.getSessionOrders(null, SESSION_CODE))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("qrToken is required");
        }

        @Test
        @DisplayName("Should throw when code is blank")
        void shouldThrowWhenCodeBlank() {
            assertThatThrownBy(() -> service.getSessionOrders(QR_TOKEN, ""))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Session code is required");
        }

        @Test
        @DisplayName("Should return empty list when no session is open")
        void shouldReturnEmptyWhenNoSessionOpen() {
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            testTable.setSessionOpenedAt(null);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.of(testTable));

            SessionOrdersResponse result = service.getSessionOrders(QR_TOKEN, SESSION_CODE);

            assertThat(result.orders()).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when no orders exist for session")
        void shouldReturnEmptyWhenNoOrders() {
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            testTable.setSessionOpenedAt(Instant.now().minusSeconds(3600));
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.of(testTable));
            when(orderRepository.findByTableIdAndCreatedAtSince(eq(TABLE_ID), any()))
                    .thenReturn(List.of());

            SessionOrdersResponse result = service.getSessionOrders(QR_TOKEN, SESSION_CODE);

            assertThat(result.orders()).isEmpty();
        }

        @Test
        @DisplayName("Should return orders with items for active session")
        void shouldReturnOrdersWithItems() {
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            Instant sessionStart = Instant.now().minusSeconds(3600);
            testTable.setSessionOpenedAt(sessionStart);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.of(testTable));

            CustomerOrder order = new CustomerOrder();
            order.setId(1L);
            order.setRestaurantOrderNumber(1);
            order.setStatus(OrderStatus.NEW);
            order.setTotalAmount(new BigDecimal("20.00"));
            when(orderRepository.findByTableIdAndCreatedAtSince(eq(TABLE_ID), eq(sessionStart)))
                    .thenReturn(List.of(order));

            MenuItem menuItem = buildMenuItem(10L, "Shawarma", new BigDecimal("10.00"), true, true);
            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setMenuItem(menuItem);
            oi.setQuantity(2);
            oi.setUnitPrice(new BigDecimal("10.00"));
            oi.setTotalPrice(new BigDecimal("20.00"));

            when(orderItemRepository.findByOrderIdInWithMenuItem(List.of(1L)))
                    .thenReturn(List.of(oi));

            SessionOrdersResponse result = service.getSessionOrders(QR_TOKEN, SESSION_CODE);

            assertThat(result.orders()).hasSize(1);
            assertThat(result.orders().get(0).items()).hasSize(1);
            assertThat(result.orders().get(0).items().get(0).itemName()).isEqualTo("Shawarma");
        }
    }

    // ==================== Helpers ====================

    private SubmitOrderRequest buildRequest(int itemCount) {
        List<SubmitOrderItemRequest> items = new ArrayList<>();
        for (int i = 1; i <= itemCount; i++) {
            items.add(new SubmitOrderItemRequest((long) i, 2, null));
        }
        return new SubmitOrderRequest(items, null);
    }

    private MenuItem buildMenuItem(Long id, String name, BigDecimal price,
                                   boolean active, boolean available) {
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setName(name);
        item.setPrice(price);
        item.setActive(active);
        item.setAvailable(available);
        return item;
    }

    private void setupValidSession() {
        doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
        when(tableRepository.findActiveByQrTokenForUpdate(QR_TOKEN)).thenReturn(Optional.of(testTable));
        when(orderRepository.existsByTableIdAndCreatedAtAfter(eq(TABLE_ID), any())).thenReturn(false);
    }

    private void setupFullValidOrder() {
        setupValidSession();

        MenuItem item = buildMenuItem(1L, "Test Item", new BigDecimal("10.00"), true, true);
        when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), eq(RESTAURANT_ID)))
                .thenReturn(List.of(item));

        RestaurantOrderCounter counter = new RestaurantOrderCounter(RESTAURANT_ID, 0);
        when(orderCounterRepository.findByIdForUpdate(RESTAURANT_ID)).thenReturn(Optional.of(counter));
        when(tableSessionRepository.findOpenSessionByTableId(TABLE_ID)).thenReturn(Optional.empty());
        when(orderRepository.save(any())).thenAnswer(inv -> {
            CustomerOrder o = inv.getArgument(0);
            if (o.getId() == null) o.setId(100L);
            return o;
        });
    }
}


