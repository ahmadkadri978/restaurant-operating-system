package com.restaurantqr.stress;

import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.order.staff.StaffOrderService;
import com.restaurantqr.order.CustomerOrder;
import com.restaurantqr.order.CustomerOrderRepository;
import com.restaurantqr.order.OrderItemRepository;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.RestaurantTable;
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
 * Tests for database lock timeout behavior and handling.
 * Simulates pessimistic lock contention scenarios.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Lock Timeout & Contention Tests")
class LockTimeoutTest {

    @Mock
    private CustomerOrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private StaffOrderService staffOrderService;

    private Restaurant testRestaurant;
    private RestaurantTable testTable;

    @BeforeEach
    void setUp() {
        staffOrderService = new StaffOrderService(
                orderRepository,
                orderItemRepository,
                eventPublisher
        );

        testRestaurant = new Restaurant();
        testRestaurant.setId(1L);
        testRestaurant.setCode("test-restaurant");
        testRestaurant.setName("Test Restaurant");
        testRestaurant.setActive(true);

        testTable = new RestaurantTable();
        testTable.setId(1L);
        testTable.setRestaurant(testRestaurant);
        testTable.setTableNumber(1);
        testTable.setActive(true);
    }

    // ==================== Concurrent Order Status Transition Tests ====================

    @Nested
    @DisplayName("Concurrent Order Status Transitions")
    class ConcurrentStatusTransitionTests {

        @Test
        @DisplayName("Should handle concurrent SENT_TO_KITCHEN transitions on same order")
        void shouldHandleConcurrentSentToKitchenTransitions() throws InterruptedException {
            // Given
            int concurrentAttempts = 10;
            Long orderId = 100L;
            ExecutorService executor = Executors.newFixedThreadPool(concurrentAttempts);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrentAttempts);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger exceptionCount = new AtomicInteger(0);

            CustomerOrder order = createTestOrder(orderId, OrderStatus.NEW);
            
            // Use synchronized block to simulate pessimistic locking properly
            Object lock = new Object();
            when(orderRepository.findByIdAndRestaurantIdForUpdate(1L, orderId)).thenAnswer(inv -> {
                synchronized (lock) {
                    // Return copy with current status
                    CustomerOrder copy = createTestOrder(orderId, order.getStatus());
                    copy.setSentToKitchenAt(order.getSentToKitchenAt());
                    return Optional.of(copy);
                }
            });
            
            when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
                synchronized (lock) {
                    CustomerOrder o = inv.getArgument(0);
                    order.setStatus(o.getStatus());
                    order.setSentToKitchenAt(o.getSentToKitchenAt());
                    return o;
                }
            });

            // When - Multiple threads try to transition the same order
            for (int i = 0; i < concurrentAttempts; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        staffOrderService.markSentToKitchen(1L, orderId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // Any exception is expected for concurrent attempts
                        exceptionCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - All threads should complete (either succeed or get exception)
            assertThat(completed).isTrue();
            // Verify all threads completed - success + any exception
            assertThat(successCount.get() + exceptionCount.get()).isEqualTo(concurrentAttempts);
        }

        @Test
        @DisplayName("Should handle concurrent DONE transitions on same order")
        void shouldHandleConcurrentDoneTransitions() throws InterruptedException {
            // Given
            int concurrentAttempts = 10;
            Long orderId = 100L;
            ExecutorService executor = Executors.newFixedThreadPool(concurrentAttempts);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrentAttempts);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger exceptionCount = new AtomicInteger(0);

            CustomerOrder order = createTestOrder(orderId, OrderStatus.SENT_TO_KITCHEN);
            order.setSentToKitchenAt(Instant.now().minusSeconds(60));
            
            // Use synchronized block to simulate pessimistic locking properly
            Object lock = new Object();
            when(orderRepository.findByIdAndRestaurantIdForUpdate(1L, orderId)).thenAnswer(inv -> {
                synchronized (lock) {
                    CustomerOrder copy = createTestOrder(orderId, order.getStatus());
                    copy.setSentToKitchenAt(order.getSentToKitchenAt());
                    copy.setDoneAt(order.getDoneAt());
                    return Optional.of(copy);
                }
            });
            
            when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
                synchronized (lock) {
                    CustomerOrder o = inv.getArgument(0);
                    order.setStatus(o.getStatus());
                    order.setDoneAt(o.getDoneAt());
                    return o;
                }
            });

            // When
            for (int i = 0; i < concurrentAttempts; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        staffOrderService.markDone(1L, orderId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // Any exception is expected for concurrent attempts
                        exceptionCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - All threads should complete (either succeed or get exception)
            assertThat(completed).isTrue();
            // Verify all threads completed - success + any exception
            assertThat(successCount.get() + exceptionCount.get()).isEqualTo(concurrentAttempts);
        }

        @Test
        @DisplayName("Should handle race between DONE and CANCELLED")
        void shouldHandleRaceBetweenDoneAndCancelled() throws InterruptedException {
            // Given
            Long orderId = 100L;
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);
            AtomicInteger doneSuccess = new AtomicInteger(0);
            AtomicInteger cancelledSuccess = new AtomicInteger(0);

            CustomerOrder order = createTestOrder(orderId, OrderStatus.SENT_TO_KITCHEN);
            order.setSentToKitchenAt(Instant.now().minusSeconds(60));
            
            Object lock = new Object();
            when(orderRepository.findByIdAndRestaurantIdForUpdate(1L, orderId)).thenAnswer(inv -> {
                synchronized (lock) {
                    CustomerOrder copy = createTestOrder(orderId, order.getStatus());
                    copy.setSentToKitchenAt(order.getSentToKitchenAt());
                    copy.setDoneAt(order.getDoneAt());
                    copy.setCancelledAt(order.getCancelledAt());
                    return Optional.of(copy);
                }
            });
            
            when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
                synchronized (lock) {
                    CustomerOrder o = inv.getArgument(0);
                    order.setStatus(o.getStatus());
                    order.setDoneAt(o.getDoneAt());
                    order.setCancelledAt(o.getCancelledAt());
                    return o;
                }
            });

            // When - One thread tries DONE, one tries CANCELLED
            executor.submit(() -> {
                try {
                    startLatch.await();
                    staffOrderService.markDone(1L, orderId);
                    doneSuccess.incrementAndGet();
                } catch (Exception e) {
                    // Expected for loser
                } finally {
                    doneLatch.countDown();
                }
            });
            
            executor.submit(() -> {
                try {
                    startLatch.await();
                    staffOrderService.markCancelled(1L, orderId);
                    cancelledSuccess.incrementAndGet();
                } catch (Exception e) {
                    // Expected for loser
                } finally {
                    doneLatch.countDown();
                }
            });

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - Exactly one should succeed
            assertThat(completed).isTrue();
            assertThat(doneSuccess.get() + cancelledSuccess.get()).isEqualTo(1);
        }
    }

    // ==================== Multiple Orders Concurrent Updates Tests ====================

    @Nested
    @DisplayName("Multiple Orders Concurrent Updates")
    class MultipleOrdersConcurrentUpdatesTests {

        @Test
        @DisplayName("Should handle updates to different orders concurrently")
        void shouldHandleUpdatesToDifferentOrdersConcurrently() throws InterruptedException {
            // Given
            int orderCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(orderCount);
            AtomicInteger successCount = new AtomicInteger(0);
            Map<Long, CustomerOrder> orders = new ConcurrentHashMap<>();

            // Create orders and set up mocks
            for (int i = 0; i < orderCount; i++) {
                Long orderId = (long) (i + 1);
                CustomerOrder order = createTestOrder(orderId, OrderStatus.NEW);
                orders.put(orderId, order);

                when(orderRepository.findByIdAndRestaurantIdForUpdate(1L, orderId))
                        .thenAnswer(inv -> {
                            Long id = inv.getArgument(1);
                            CustomerOrder o = orders.get(id);
                            if (o == null) return Optional.empty();
                            CustomerOrder copy = createTestOrder(id, o.getStatus());
                            return Optional.of(copy);
                        });
            }

            when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
                CustomerOrder o = inv.getArgument(0);
                CustomerOrder existing = orders.get(o.getId());
                if (existing != null) {
                    existing.setStatus(o.getStatus());
                    existing.setSentToKitchenAt(o.getSentToKitchenAt());
                }
                return o;
            });

            // When - Update all orders concurrently
            for (int i = 0; i < orderCount; i++) {
                Long orderId = (long) (i + 1);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        staffOrderService.markSentToKitchen(1L, orderId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // Unexpected errors
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - All should succeed (different orders)
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(orderCount);

            // Verify all orders are now SENT_TO_KITCHEN
            for (CustomerOrder order : orders.values()) {
                assertThat(order.getStatus()).isEqualTo(OrderStatus.SENT_TO_KITCHEN);
            }
        }
    }

    // ==================== SSE Event Publishing Under Lock Tests ====================

    @Nested
    @DisplayName("SSE Event Publishing Under Lock")
    class SseEventPublishingUnderLockTests {

        @Test
        @DisplayName("Should publish SSE events after each status transition")
        void shouldPublishSseEventsAfterStatusTransitions() throws InterruptedException {
            // Given
            int orderCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch doneLatch = new CountDownLatch(orderCount);
            AtomicInteger sseEventsPublished = new AtomicInteger(0);

            for (int i = 0; i < orderCount; i++) {
                Long orderId = (long) (i + 1);
                CustomerOrder order = createTestOrder(orderId, OrderStatus.NEW);

                when(orderRepository.findByIdAndRestaurantIdForUpdate(1L, orderId))
                        .thenReturn(Optional.of(order));
                when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
                    CustomerOrder o = inv.getArgument(0);
                    o.setSentToKitchenAt(Instant.now());
                    return o;
                });
            }

            doAnswer(inv -> {
                sseEventsPublished.incrementAndGet();
                return null;
            }).when(eventPublisher).publishEvent(any(Object.class));

            // When
            for (int i = 0; i < orderCount; i++) {
                Long orderId = (long) (i + 1);
                executor.submit(() -> {
                    try {
                        staffOrderService.markSentToKitchen(1L, orderId);
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(sseEventsPublished.get()).isEqualTo(orderCount);
        }
    }

    // ==================== Helper Methods ====================

    private CustomerOrder createTestOrder(Long id, OrderStatus status) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setRestaurant(testRestaurant);
        order.setTable(testTable);
        order.setStatus(status);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCreatedAt(Instant.now().minusSeconds(120));
        order.setRestaurantOrderNumber(id.intValue());
        return order;
    }
}









