package com.restaurantqr.stress;

import com.restaurantqr.exception.RateLimitException;
import com.restaurantqr.menu.MenuItem;
import com.restaurantqr.menu.MenuItemRepository;
import com.restaurantqr.order.*;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.PublicSessionService;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Concurrency and stress tests for order submission.
 * Tests race conditions, pessimistic locking, and rate limiting under load.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Order Concurrency & Stress Tests")
class OrderConcurrencyTest {

    @Mock
    private RestaurantTableRepository tableRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private RestaurantOrderCounterRepository orderCounterRepository;
    @Mock
    private PublicSessionService publicSessionService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderService orderService;

    private Restaurant testRestaurant;
    private RestaurantTable testTable;
    private MenuItem testMenuItem;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                tableRepository,
                menuItemRepository,
                orderRepository,
                orderItemRepository,
                orderCounterRepository,
                publicSessionService,
                eventPublisher
        );

        // Setup test data
        testRestaurant = new Restaurant();
        testRestaurant.setId(1L);
        testRestaurant.setCode("test-restaurant");
        testRestaurant.setName("Test Restaurant");
        testRestaurant.setActive(true);

        testTable = new RestaurantTable();
        testTable.setId(1L);
        testTable.setRestaurant(testRestaurant);
        testTable.setTableNumber(1);
        testTable.setQrToken("test-qr-token");
        testTable.setActive(true);

        testMenuItem = new MenuItem();
        testMenuItem.setId(1L);
        testMenuItem.setName("Test Item");
        testMenuItem.setPrice(new BigDecimal("10.00"));
        testMenuItem.setRestaurant(testRestaurant);
        testMenuItem.setActive(true);
        testMenuItem.setAvailable(true);
    }

    // ==================== Concurrent Order Submission Tests ====================

    @Nested
    @DisplayName("Concurrent Order Submission")
    class ConcurrentOrderSubmissionTests {

        @Test
        @DisplayName("Should handle 10 concurrent orders to different tables correctly")
        void shouldHandleConcurrentOrdersToDifferentTables() throws InterruptedException {
            // Given
            int concurrentOrders = 10;
            ExecutorService executor = Executors.newFixedThreadPool(concurrentOrders);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrentOrders);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // Setup mocks for each table
            for (int i = 0; i < concurrentOrders; i++) {
                int tableNum = i + 1;
                String qrToken = "qr-token-" + tableNum;
                
                RestaurantTable table = new RestaurantTable();
                table.setId((long) tableNum);
                table.setRestaurant(testRestaurant);
                table.setTableNumber(tableNum);
                table.setQrToken(qrToken);
                table.setActive(true);

                doNothing().when(publicSessionService).verifySessionOrThrow(eq(qrToken), anyString());
                when(tableRepository.findActiveByQrTokenForUpdate(qrToken))
                        .thenReturn(Optional.of(table));
                when(orderRepository.existsByTableIdAndCreatedAtAfter(eq(table.getId()), any(Instant.class)))
                        .thenReturn(false);
            }

            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), eq(1L)))
                    .thenReturn(List.of(testMenuItem));
            
            // Setup order counter with thread-safe increment
            AtomicInteger orderCounter = new AtomicInteger(0);
            RestaurantOrderCounter counter = new RestaurantOrderCounter(1L, 0);
            when(orderCounterRepository.findByIdForUpdate(1L)).thenAnswer(inv -> {
                // Simulate database returning current value
                counter.setLastOrderNumber(orderCounter.get());
                return Optional.of(counter);
            });
            when(orderCounterRepository.save(any(RestaurantOrderCounter.class))).thenAnswer(inv -> {
                RestaurantOrderCounter c = inv.getArgument(0);
                orderCounter.set(c.getLastOrderNumber());
                return c;
            });
            
            when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
                CustomerOrder order = inv.getArgument(0);
                order.setId((long) (Math.random() * 10000));
                order.setCreatedAt(Instant.now());
                return order;
            });
            when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // When - Submit concurrent orders
            for (int i = 0; i < concurrentOrders; i++) {
                int tableNum = i + 1;
                String qrToken = "qr-token-" + tableNum;
                
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        SubmitOrderRequest request = new SubmitOrderRequest(
                                List.of(new SubmitOrderItemRequest(1L, 1, null)),
                                null
                        );
                        orderService.submitOrder(qrToken, "1234", request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Release all threads simultaneously
            startLatch.countDown();
            
            // Wait for all to complete
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(concurrentOrders);
            assertThat(failCount.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should rate limit concurrent orders to same table")
        void shouldRateLimitConcurrentOrdersToSameTable() throws InterruptedException {
            // Given
            int concurrentOrders = 5;
            ExecutorService executor = Executors.newFixedThreadPool(concurrentOrders);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrentOrders);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger rateLimitCount = new AtomicInteger(0);

            // Setup mocks - same table for all orders
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate("test-qr-token"))
                    .thenReturn(Optional.of(testTable));
            
            // First call returns false (no recent order), subsequent calls return true
            AtomicInteger callCount = new AtomicInteger(0);
            when(orderRepository.existsByTableIdAndCreatedAtAfter(eq(1L), any(Instant.class)))
                    .thenAnswer(inv -> callCount.getAndIncrement() > 0);

            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), eq(1L)))
                    .thenReturn(List.of(testMenuItem));
            
            RestaurantOrderCounter counter = new RestaurantOrderCounter(1L, 0);
            when(orderCounterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(counter));
            when(orderCounterRepository.save(any(RestaurantOrderCounter.class))).thenAnswer(inv -> inv.getArgument(0));
            
            when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
                CustomerOrder order = inv.getArgument(0);
                order.setId(1L);
                order.setCreatedAt(Instant.now());
                return order;
            });
            when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // When
            for (int i = 0; i < concurrentOrders; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        SubmitOrderRequest request = new SubmitOrderRequest(
                                List.of(new SubmitOrderItemRequest(1L, 1, null)),
                                null
                        );
                        orderService.submitOrder("test-qr-token", "1234", request);
                        successCount.incrementAndGet();
                    } catch (RateLimitException e) {
                        rateLimitCount.incrementAndGet();
                    } catch (Exception e) {
                        // Other exceptions
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(rateLimitCount.get()).isEqualTo(concurrentOrders - 1);
        }
    }

    // ==================== Order Number Generation Race Condition Tests ====================

    @Nested
    @DisplayName("Order Number Race Condition")
    class OrderNumberRaceConditionTests {

        @Test
        @DisplayName("Should generate unique order numbers under concurrent load")
        void shouldGenerateUniqueOrderNumbersConcurrently() throws InterruptedException {
            // Given
            int concurrentOrders = 20;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrentOrders);
            Set<Integer> generatedNumbers = Collections.synchronizedSet(new HashSet<>());
            AtomicInteger successCount = new AtomicInteger(0);

            // Each order goes to a different table to avoid rate limiting
            for (int i = 0; i < concurrentOrders; i++) {
                int tableNum = i + 1;
                String qrToken = "qr-" + tableNum;
                
                RestaurantTable table = new RestaurantTable();
                table.setId((long) tableNum);
                table.setRestaurant(testRestaurant);
                table.setTableNumber(tableNum);
                table.setQrToken(qrToken);
                table.setActive(true);

                doNothing().when(publicSessionService).verifySessionOrThrow(eq(qrToken), anyString());
                when(tableRepository.findActiveByQrTokenForUpdate(qrToken)).thenReturn(Optional.of(table));
                when(orderRepository.existsByTableIdAndCreatedAtAfter(eq(table.getId()), any(Instant.class)))
                        .thenReturn(false);
            }

            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), eq(1L)))
                    .thenReturn(List.of(testMenuItem));

            // Simulate pessimistic lock behavior using ReentrantLock
            // The lock simulates DB-level row locking on findByIdForUpdate
            AtomicInteger dbCounter = new AtomicInteger(0);
            java.util.concurrent.locks.ReentrantLock counterLock = new java.util.concurrent.locks.ReentrantLock();
            
            when(orderCounterRepository.findByIdForUpdate(1L)).thenAnswer(inv -> {
                // Acquire lock to simulate pessimistic locking - this blocks concurrent access
                counterLock.lock();
                // Return current value - lock is held until save() is called
                RestaurantOrderCounter counter = new RestaurantOrderCounter(1L, dbCounter.get());
                return Optional.of(counter);
            });
            when(orderCounterRepository.save(any(RestaurantOrderCounter.class))).thenAnswer(inv -> {
                try {
                    RestaurantOrderCounter c = inv.getArgument(0);
                    // Update the counter atomically while holding the lock
                    dbCounter.set(c.getLastOrderNumber());
                    return c;
                } finally {
                    // Release the lock after save (simulates end of DB transaction)
                    if (counterLock.isHeldByCurrentThread()) {
                        counterLock.unlock();
                    }
                }
            });

            when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
                CustomerOrder order = inv.getArgument(0);
                order.setId((long) (Math.random() * 100000));
                order.setCreatedAt(Instant.now());
                generatedNumbers.add(order.getRestaurantOrderNumber());
                return order;
            });
            when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // When
            for (int i = 0; i < concurrentOrders; i++) {
                int tableNum = i + 1;
                String qrToken = "qr-" + tableNum;
                
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        SubmitOrderRequest request = new SubmitOrderRequest(
                                List.of(new SubmitOrderItemRequest(1L, 1, null)),
                                null
                        );
                        orderService.submitOrder(qrToken, "1234", request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // Track but don't fail
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(concurrentOrders);
            // In a real scenario with proper DB locking, all numbers should be unique
            // Here we verify our mock simulation works correctly
            assertThat(generatedNumbers).hasSize(concurrentOrders);
        }
    }

    // ==================== Validation Under Load Tests ====================

    @Nested
    @DisplayName("Validation Under Load")
    class ValidationUnderLoadTests {

        @Test
        @DisplayName("Should validate all items even under concurrent load")
        void shouldValidateItemsUnderLoad() throws InterruptedException {
            // Given
            int concurrentRequests = 10;
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrentRequests);
            AtomicInteger validationErrors = new AtomicInteger(0);

            // Setup basic mocks
            for (int i = 0; i < concurrentRequests; i++) {
                int tableNum = i + 1;
                String qrToken = "qr-" + tableNum;
                
                RestaurantTable table = new RestaurantTable();
                table.setId((long) tableNum);
                table.setRestaurant(testRestaurant);
                table.setTableNumber(tableNum);
                table.setQrToken(qrToken);
                table.setActive(true);

                doNothing().when(publicSessionService).verifySessionOrThrow(eq(qrToken), anyString());
                when(tableRepository.findActiveByQrTokenForUpdate(qrToken)).thenReturn(Optional.of(table));
                when(orderRepository.existsByTableIdAndCreatedAtAfter(eq(table.getId()), any(Instant.class)))
                        .thenReturn(false);
            }

            // Return menu item for valid IDs only
            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), eq(1L)))
                    .thenAnswer(inv -> {
                        List<Long> ids = inv.getArgument(0);
                        // Only return items for ID 1
                        if (ids.contains(1L)) {
                            return List.of(testMenuItem);
                        }
                        return List.of();
                    });

            // When - Mix of valid and invalid requests
            for (int i = 0; i < concurrentRequests; i++) {
                int tableNum = i + 1;
                String qrToken = "qr-" + tableNum;
                boolean isInvalid = (i % 2 == 0); // Every other request is invalid
                
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Long menuItemId = isInvalid ? 999L : 1L; // Invalid ID for some
                        SubmitOrderRequest request = new SubmitOrderRequest(
                                List.of(new SubmitOrderItemRequest(menuItemId, 1, null)),
                                null
                        );
                        orderService.submitOrder(qrToken, "1234", request);
                    } catch (Exception e) {
                        if (e.getMessage().contains("Menu item not found")) {
                            validationErrors.incrementAndGet();
                        }
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(validationErrors.get()).isEqualTo(concurrentRequests / 2);
        }

        @Test
        @DisplayName("Should reject orders with too many items")
        void shouldRejectOrdersWithTooManyItems() {
            // Given
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            
            // Create request with 201 items (exceeds MAX_ITEMS_PER_ORDER = 200)
            List<SubmitOrderItemRequest> tooManyItems = new ArrayList<>();
            for (int i = 0; i < 201; i++) {
                tooManyItems.add(new SubmitOrderItemRequest(1L, 1, null));
            }
            SubmitOrderRequest request = new SubmitOrderRequest(tooManyItems, null);

            // When/Then
            assertThatThrownBy(() -> orderService.submitOrder("test-qr-token", "1234", request))
                    .hasMessageContaining("Too many items");
        }

        @Test
        @DisplayName("Should reject quantity exceeding limit")
        void shouldRejectExcessiveQuantity() {
            // Given
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));
            when(orderRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(false);

            // Create request with quantity > MAX_QTY_PER_ITEM (50)
            SubmitOrderRequest request = new SubmitOrderRequest(
                    List.of(new SubmitOrderItemRequest(1L, 51, null)),
                    null
            );

            // When/Then
            assertThatThrownBy(() -> orderService.submitOrder("test-qr-token", "1234", request))
                    .hasMessageContaining("quantity too large");
        }
    }

    // ==================== Memory Pressure Tests ====================

    @Nested
    @DisplayName("Memory Pressure")
    class MemoryPressureTests {

        @Test
        @DisplayName("Should handle large number of order items without OOM")
        void shouldHandleLargeOrderWithoutOOM() {
            // Given
            doNothing().when(publicSessionService).verifySessionOrThrow(anyString(), anyString());
            when(tableRepository.findActiveByQrTokenForUpdate(anyString())).thenReturn(Optional.of(testTable));
            when(orderRepository.existsByTableIdAndCreatedAtAfter(anyLong(), any(Instant.class))).thenReturn(false);

            // Create 200 unique menu items
            List<MenuItem> manyItems = new ArrayList<>();
            List<SubmitOrderItemRequest> requestItems = new ArrayList<>();
            for (int i = 1; i <= 200; i++) {
                MenuItem item = new MenuItem();
                item.setId((long) i);
                item.setName("Item " + i);
                item.setPrice(new BigDecimal("10.00"));
                item.setRestaurant(testRestaurant);
                item.setActive(true);
                item.setAvailable(true);
                manyItems.add(item);
                
                requestItems.add(new SubmitOrderItemRequest((long) i, 1, null));
            }

            when(menuItemRepository.findByIdInAndRestaurantIdAndIsActiveTrue(anyList(), eq(1L)))
                    .thenReturn(manyItems);
            
            RestaurantOrderCounter counter = new RestaurantOrderCounter(1L, 0);
            when(orderCounterRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(counter));
            when(orderCounterRepository.save(any(RestaurantOrderCounter.class))).thenReturn(counter);
            
            when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
                CustomerOrder order = inv.getArgument(0);
                order.setId(1L);
                order.setCreatedAt(Instant.now());
                return order;
            });
            when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            SubmitOrderRequest request = new SubmitOrderRequest(requestItems, null);

            // When
            long beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            SubmitOrderResponse response = orderService.submitOrder("test-qr-token", "1234", request);
            long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            // Then
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isNotNull();
            
            // Memory increase should be reasonable (less than 50MB for 200 items)
            long memoryIncrease = afterMemory - beforeMemory;
            assertThat(memoryIncrease).isLessThan(50 * 1024 * 1024L);
        }
    }
}

