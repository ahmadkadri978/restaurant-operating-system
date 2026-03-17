package com.restaurantqr.order;

import com.restaurantqr.menu.MenuItem;
import com.restaurantqr.menu.MenuItemRepository;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.order.staff.StaffOrderService;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.sse.*;
import com.restaurantqr.table.PublicSessionService;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-End Integration Tests for Order SSE Events.
 * Tests the complete flow from order submission to SSE event publishing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Order SSE Integration Tests")
class OrderSseIntegrationTest {

    private OrderService orderService;
    private StaffOrderService staffOrderService;

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private CustomerOrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PublicSessionService publicSessionService;

    @Mock
    private RestaurantEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<OrderCreatedPayload> orderCreatedPayloadCaptor;

    @Captor
    private ArgumentCaptor<OrderStatusPayload> orderStatusPayloadCaptor;

    private Restaurant testRestaurant;
    private RestaurantTable testTable;
    private MenuItem testMenuItem;

    @BeforeEach
    void setUp() {
        // Setup test restaurant
        testRestaurant = new Restaurant();
        testRestaurant.setId(1L);
        testRestaurant.setName("Test Restaurant");
        testRestaurant.setActive(true);

        // Setup test table
        testTable = new RestaurantTable();
        testTable.setId(1L);
        testTable.setTableNumber(5);
        testTable.setRestaurant(testRestaurant);
        testTable.setActive(true);
        testTable.setQrToken("test-qr-token");

        // Setup test menu item
        testMenuItem = new MenuItem();
        testMenuItem.setId(1L);
        testMenuItem.setName("Test Burger");
        testMenuItem.setPrice(new BigDecimal("12.50"));
        testMenuItem.setRestaurant(testRestaurant);
        testMenuItem.setActive(true);
        testMenuItem.setAvailable(true);

        // Create OrderService
        orderService = new OrderService(
                tableRepository,
                menuItemRepository,
                orderRepository,
                orderItemRepository,
                publicSessionService,
                eventPublisher
        );

        // Create StaffOrderService
        staffOrderService = new StaffOrderService(
                orderRepository,
                orderItemRepository,
                eventPublisher
        );
    }

    // ==================== Order Created SSE Tests ====================

    @Nested
    @DisplayName("ORDER_CREATED Event Tests")
    class OrderCreatedEventTests {

        @BeforeEach
        void setupMocks() {
            // Setup common mocks for order submission
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));
            when(orderRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(false);
            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), anyLong()))
                    .thenReturn(List.of(testMenuItem));
            when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> {
                CustomerOrder order = invocation.getArgument(0);
                if (order.getId() == null) {
                    order.setId(100L);
                    order.setCreatedAt(Instant.now());
                }
                return order;
            });
            when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("Should publish ORDER_CREATED event when order is submitted successfully")
        void shouldPublishOrderCreatedEventOnSubmit() {
            // Given
            SubmitOrderRequest request = new SubmitOrderRequest(
                    List.of(new SubmitOrderItemRequest(1L, 2, "No onions")),
                    "Test order note"
            );

            // When
            SubmitOrderResponse response = orderService.submitOrder("test-qr-token", "valid-code", request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(100L);

            // Verify SSE event was published
            verify(eventPublisher).publishOrderCreated(eq(1L), orderCreatedPayloadCaptor.capture());

            OrderCreatedPayload payload = orderCreatedPayloadCaptor.getValue();
            assertThat(payload.orderId()).isEqualTo(100L);
            assertThat(payload.tableNumber()).isEqualTo(5);
            assertThat(payload.note()).isEqualTo("Test order note");
            assertThat(payload.totalAmount()).isNotNull();
            assertThat(payload.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("Should include correct total amount in ORDER_CREATED payload")
        void shouldIncludeCorrectTotalAmountInPayload() {
            // Given - 3 items at 12.50 each = 37.50
            SubmitOrderRequest request = new SubmitOrderRequest(
                    List.of(new SubmitOrderItemRequest(1L, 3, null)),
                    null
            );

            // When
            orderService.submitOrder("test-qr-token", "valid-code", request);

            // Then
            verify(eventPublisher).publishOrderCreated(eq(1L), orderCreatedPayloadCaptor.capture());

            OrderCreatedPayload payload = orderCreatedPayloadCaptor.getValue();
            assertThat(payload.totalAmount()).isEqualByComparingTo(new BigDecimal("37.50"));
        }

        @Test
        @DisplayName("Should not publish SSE event when order submission fails")
        void shouldNotPublishEventOnFailure() {
            // Given - Empty items should fail
            SubmitOrderRequest request = new SubmitOrderRequest(List.of(), null);

            // When & Then
            assertThatThrownBy(() -> orderService.submitOrder("test-qr-token", "valid-code", request))
                    .isInstanceOf(Exception.class);

            // Verify no SSE event was published
            verifyNoInteractions(eventPublisher);
        }
    }

    // ==================== Order Sent to Kitchen SSE Tests ====================

    @Nested
    @DisplayName("ORDER_SENT_TO_KITCHEN Event Tests")
    class OrderSentToKitchenEventTests {

        private CustomerOrder testOrder;

        @BeforeEach
        void setupMocks() {
            testOrder = new CustomerOrder();
            testOrder.setId(100L);
            testOrder.setRestaurant(testRestaurant);
            testOrder.setTable(testTable);
            testOrder.setStatus(OrderStatus.NEW);
            testOrder.setTotalAmount(new BigDecimal("25.00"));
            testOrder.setCreatedAt(Instant.now());
        }

        @Test
        @DisplayName("Should publish ORDER_SENT_TO_KITCHEN event when order is sent to kitchen")
        void shouldPublishOrderSentToKitchenEvent() {
            // Given
            when(orderRepository.findByIdAndRestaurantIdForUpdate(anyLong(), anyLong()))
                    .thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(CustomerOrder.class))).thenReturn(testOrder);

            // When
            staffOrderService.markSentToKitchen(1L, 100L);

            // Then
            verify(eventPublisher).publishOrderSentToKitchen(eq(1L), orderStatusPayloadCaptor.capture());

            OrderStatusPayload payload = orderStatusPayloadCaptor.getValue();
            assertThat(payload.orderId()).isEqualTo(100L);
            assertThat(payload.tableNumber()).isEqualTo(5);
            assertThat(payload.status()).isEqualTo(OrderStatus.SENT_TO_KITCHEN);
            assertThat(payload.sentToKitchenAt()).isNotNull();
            assertThat(payload.doneAt()).isNull();
            assertThat(payload.cancelledAt()).isNull();
        }

        @Test
        @DisplayName("Should not publish event when order is already SENT_TO_KITCHEN (idempotent)")
        void shouldNotPublishEventWhenAlreadySentToKitchen() {
            // Given - order already sent to kitchen
            testOrder.setStatus(OrderStatus.SENT_TO_KITCHEN);
            testOrder.setSentToKitchenAt(Instant.now());

            when(orderRepository.findByIdAndRestaurantIdForUpdate(anyLong(), anyLong()))
                    .thenReturn(Optional.of(testOrder));

            // When
            staffOrderService.markSentToKitchen(1L, 100L);

            // Then - No event should be published
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should throw exception when trying to send non-NEW order to kitchen")
        void shouldThrowWhenSendingNonNewOrderToKitchen() {
            // Given
            testOrder.setStatus(OrderStatus.DONE);

            when(orderRepository.findByIdAndRestaurantIdForUpdate(anyLong(), anyLong()))
                    .thenReturn(Optional.of(testOrder));

            // When & Then
            assertThatThrownBy(() -> staffOrderService.markSentToKitchen(1L, 100L))
                    .hasMessageContaining("Only NEW orders can be sent to kitchen");

            verifyNoInteractions(eventPublisher);
        }
    }

    // ==================== Order Done SSE Tests ====================

    @Nested
    @DisplayName("ORDER_DONE Event Tests")
    class OrderDoneEventTests {

        private CustomerOrder testOrder;

        @BeforeEach
        void setupMocks() {
            testOrder = new CustomerOrder();
            testOrder.setId(100L);
            testOrder.setRestaurant(testRestaurant);
            testOrder.setTable(testTable);
            testOrder.setStatus(OrderStatus.SENT_TO_KITCHEN);
            testOrder.setSentToKitchenAt(Instant.now().minusSeconds(300));
            testOrder.setTotalAmount(new BigDecimal("25.00"));
            testOrder.setCreatedAt(Instant.now().minusSeconds(600));
        }

        @Test
        @DisplayName("Should publish ORDER_DONE event when order is marked as done")
        void shouldPublishOrderDoneEvent() {
            // Given
            when(orderRepository.findByIdAndRestaurantIdForUpdate(anyLong(), anyLong()))
                    .thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(CustomerOrder.class))).thenReturn(testOrder);

            // When
            staffOrderService.markDone(1L, 100L);

            // Then
            verify(eventPublisher).publishOrderDone(eq(1L), orderStatusPayloadCaptor.capture());

            OrderStatusPayload payload = orderStatusPayloadCaptor.getValue();
            assertThat(payload.orderId()).isEqualTo(100L);
            assertThat(payload.tableNumber()).isEqualTo(5);
            assertThat(payload.status()).isEqualTo(OrderStatus.DONE);
            assertThat(payload.sentToKitchenAt()).isNotNull();
            assertThat(payload.doneAt()).isNotNull();
            assertThat(payload.cancelledAt()).isNull();
        }

        @Test
        @DisplayName("Should not publish event when order is already DONE (idempotent)")
        void shouldNotPublishEventWhenAlreadyDone() {
            // Given
            testOrder.setStatus(OrderStatus.DONE);
            testOrder.setDoneAt(Instant.now());

            when(orderRepository.findByIdAndRestaurantIdForUpdate(anyLong(), anyLong()))
                    .thenReturn(Optional.of(testOrder));

            // When
            staffOrderService.markDone(1L, 100L);

            // Then
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should throw exception when trying to mark NEW order as done")
        void shouldThrowWhenMarkingNewOrderAsDone() {
            // Given
            testOrder.setStatus(OrderStatus.NEW);
            testOrder.setSentToKitchenAt(null);

            when(orderRepository.findByIdAndRestaurantIdForUpdate(anyLong(), anyLong()))
                    .thenReturn(Optional.of(testOrder));

            // When & Then
            assertThatThrownBy(() -> staffOrderService.markDone(1L, 100L))
                    .hasMessageContaining("Order must be SENT_TO_KITCHEN before marking DONE");

            verifyNoInteractions(eventPublisher);
        }
    }

    // ==================== Order Cancelled SSE Tests ====================

    @Nested
    @DisplayName("ORDER_CANCELLED Event Tests")
    class OrderCancelledEventTests {

        private CustomerOrder testOrder;

        @BeforeEach
        void setupMocks() {
            testOrder = new CustomerOrder();
            testOrder.setId(100L);
            testOrder.setRestaurant(testRestaurant);
            testOrder.setTable(testTable);
            testOrder.setStatus(OrderStatus.NEW);
            testOrder.setTotalAmount(new BigDecimal("25.00"));
            testOrder.setCreatedAt(Instant.now());
        }

        @Test
        @DisplayName("Should publish ORDER_CANCELLED event when order is cancelled")
        void shouldPublishOrderCancelledEvent() {
            // Given
            when(orderRepository.findByIdAndRestaurantIdForUpdate(anyLong(), anyLong()))
                    .thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(CustomerOrder.class))).thenReturn(testOrder);

            // When
            staffOrderService.markCancelled(1L, 100L);

            // Then
            verify(eventPublisher).publishOrderCancelled(eq(1L), orderStatusPayloadCaptor.capture());

            OrderStatusPayload payload = orderStatusPayloadCaptor.getValue();
            assertThat(payload.orderId()).isEqualTo(100L);
            assertThat(payload.tableNumber()).isEqualTo(5);
            assertThat(payload.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(payload.cancelledAt()).isNotNull();
        }

        @Test
        @DisplayName("Should not publish event when order is already CANCELLED (idempotent)")
        void shouldNotPublishEventWhenAlreadyCancelled() {
            // Given
            testOrder.setStatus(OrderStatus.CANCELLED);
            testOrder.setCancelledAt(Instant.now());

            when(orderRepository.findByIdAndRestaurantIdForUpdate(anyLong(), anyLong()))
                    .thenReturn(Optional.of(testOrder));

            // When
            staffOrderService.markCancelled(1L, 100L);

            // Then
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should throw exception when trying to cancel non-NEW order")
        void shouldThrowWhenCancellingNonNewOrder() {
            // Given
            testOrder.setStatus(OrderStatus.SENT_TO_KITCHEN);

            when(orderRepository.findByIdAndRestaurantIdForUpdate(anyLong(), anyLong()))
                    .thenReturn(Optional.of(testOrder));

            // When & Then
            assertThatThrownBy(() -> staffOrderService.markCancelled(1L, 100L))
                    .hasMessageContaining("Order must be NEW before marking CANCELLED");

            verifyNoInteractions(eventPublisher);
        }
    }

    // ==================== Complete Order Lifecycle Tests ====================

    @Nested
    @DisplayName("Complete Order Lifecycle E2E Tests")
    class CompleteLifecycleTests {

        @Test
        @DisplayName("Should publish correct SSE events through complete order lifecycle")
        void shouldPublishEventsForCompleteLifecycle() {
            // === Phase 1: Order Creation ===
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));
            when(orderRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(false);
            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), anyLong()))
                    .thenReturn(List.of(testMenuItem));

            CustomerOrder savedOrder = new CustomerOrder();
            savedOrder.setId(200L);
            savedOrder.setRestaurant(testRestaurant);
            savedOrder.setTable(testTable);
            savedOrder.setStatus(OrderStatus.NEW);
            savedOrder.setTotalAmount(new BigDecimal("25.00"));
            savedOrder.setCreatedAt(Instant.now());

            when(orderRepository.save(any(CustomerOrder.class))).thenReturn(savedOrder);
            when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // Submit order
            SubmitOrderRequest request = new SubmitOrderRequest(
                    List.of(new SubmitOrderItemRequest(1L, 2, null)),
                    null
            );
            orderService.submitOrder("test-qr-token", "valid-code", request);

            // Verify ORDER_CREATED event
            verify(eventPublisher).publishOrderCreated(eq(1L), any(OrderCreatedPayload.class));

            // === Phase 2: Send to Kitchen ===
            reset(eventPublisher);
            when(orderRepository.findByIdAndRestaurantIdForUpdate(1L, 200L)).thenReturn(Optional.of(savedOrder));

            staffOrderService.markSentToKitchen(1L, 200L);

            // Verify ORDER_SENT_TO_KITCHEN event
            verify(eventPublisher).publishOrderSentToKitchen(eq(1L), orderStatusPayloadCaptor.capture());
            assertThat(orderStatusPayloadCaptor.getValue().status()).isEqualTo(OrderStatus.SENT_TO_KITCHEN);

            // === Phase 3: Mark Done ===
            reset(eventPublisher);
            savedOrder.setStatus(OrderStatus.SENT_TO_KITCHEN);
            savedOrder.setSentToKitchenAt(Instant.now());

            staffOrderService.markDone(1L, 200L);

            // Verify ORDER_DONE event
            verify(eventPublisher).publishOrderDone(eq(1L), orderStatusPayloadCaptor.capture());
            assertThat(orderStatusPayloadCaptor.getValue().status()).isEqualTo(OrderStatus.DONE);
        }

        @Test
        @DisplayName("Should publish ORDER_CANCELLED event for cancelled order lifecycle")
        void shouldPublishEventForCancelledLifecycle() {
            // Setup
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));
            when(orderRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(false);
            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), anyLong()))
                    .thenReturn(List.of(testMenuItem));

            CustomerOrder savedOrder = new CustomerOrder();
            savedOrder.setId(300L);
            savedOrder.setRestaurant(testRestaurant);
            savedOrder.setTable(testTable);
            savedOrder.setStatus(OrderStatus.NEW);
            savedOrder.setTotalAmount(new BigDecimal("12.50"));
            savedOrder.setCreatedAt(Instant.now());

            when(orderRepository.save(any(CustomerOrder.class))).thenReturn(savedOrder);
            when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // Submit order
            orderService.submitOrder("test-qr-token", "valid-code",
                    new SubmitOrderRequest(List.of(new SubmitOrderItemRequest(1L, 1, null)), null));

            // Verify ORDER_CREATED
            verify(eventPublisher).publishOrderCreated(eq(1L), any(OrderCreatedPayload.class));

            // Cancel order
            reset(eventPublisher);
            when(orderRepository.findByIdAndRestaurantIdForUpdate(1L, 300L)).thenReturn(Optional.of(savedOrder));

            staffOrderService.markCancelled(1L, 300L);

            // Verify ORDER_CANCELLED event
            verify(eventPublisher).publishOrderCancelled(eq(1L), orderStatusPayloadCaptor.capture());
            assertThat(orderStatusPayloadCaptor.getValue().status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(orderStatusPayloadCaptor.getValue().orderId()).isEqualTo(300L);
        }
    }

    // ==================== Restaurant Scope Tests ====================

    @Nested
    @DisplayName("Restaurant Scope Tests")
    class RestaurantScopeTests {

        @Test
        @DisplayName("Should publish events with correct restaurant ID")
        void shouldPublishEventsWithCorrectRestaurantId() {
            // Given
            Long restaurantId = 42L;
            testRestaurant.setId(restaurantId);

            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));
            when(orderRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(false);
            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), anyLong()))
                    .thenReturn(List.of(testMenuItem));

            CustomerOrder savedOrder = new CustomerOrder();
            savedOrder.setId(400L);
            savedOrder.setRestaurant(testRestaurant);
            savedOrder.setTable(testTable);
            savedOrder.setStatus(OrderStatus.NEW);
            savedOrder.setCreatedAt(Instant.now());
            savedOrder.setTotalAmount(BigDecimal.TEN);

            when(orderRepository.save(any())).thenReturn(savedOrder);
            when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // When
            orderService.submitOrder("test-qr-token", "valid-code",
                    new SubmitOrderRequest(List.of(new SubmitOrderItemRequest(1L, 1, null)), null));

            // Then - Event should be published with restaurant ID 42
            verify(eventPublisher).publishOrderCreated(eq(42L), any(OrderCreatedPayload.class));
        }
    }
}


