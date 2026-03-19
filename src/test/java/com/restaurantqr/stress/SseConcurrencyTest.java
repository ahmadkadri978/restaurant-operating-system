package com.restaurantqr.stress;

import com.restaurantqr.sse.*;
import org.junit.jupiter.api.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Concurrency tests for SSE (Server-Sent Events) components.
 * Tests thread safety of SseEmitterRegistry under high load.
 */
@DisplayName("SSE Concurrency & Stress Tests")
class SseConcurrencyTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
    }

    // ==================== Thread Safety Tests ====================

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should register and track emitter for restaurant")
        void shouldRegisterEmitter() {
            // Given
            SseEmitter emitter = new SseEmitter();
            Long restaurantId = 1L;

            // When
            boolean registered = registry.register(restaurantId, emitter);

            // Then
            assertThat(registered).isTrue();
            assertThat(registry.getConnectionCount(restaurantId)).isEqualTo(1);
            assertThat(registry.getTotalConnectionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle concurrent register/remove operations safely")
        void shouldHandleConcurrentRegisterRemove() throws InterruptedException {
            // Given
            int threadCount = 50;
            int operationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger errorCount = new AtomicInteger(0);
            Long restaurantId = 1L;

            // When - Concurrent register/remove operations
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < operationsPerThread; j++) {
                            SseEmitter emitter = new SseEmitter();
                            registry.register(restaurantId, emitter);
                            // Random delay to increase chance of race condition
                            if (Math.random() > 0.5) {
                                Thread.yield();
                            }
                            registry.remove(restaurantId, emitter);
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(errorCount.get()).isEqualTo(0);
            // After all register/remove pairs, count should be 0
            assertThat(registry.getConnectionCount(restaurantId)).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle concurrent broadcasts without data corruption")
        void shouldHandleConcurrentBroadcasts() throws InterruptedException {
            // Given
            int emitterCount = 20;
            int broadcastCount = 50;
            Long restaurantId = 1L;
            
            // Create mock emitters that count received events
            AtomicInteger totalEventsReceived = new AtomicInteger(0);
            List<MockSseEmitter> mockEmitters = new ArrayList<>();
            
            for (int i = 0; i < emitterCount; i++) {
                MockSseEmitter mockEmitter = new MockSseEmitter();
                mockEmitters.add(mockEmitter);
                registry.register(restaurantId, mockEmitter);
            }

            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(broadcastCount);

            // When - Concurrent broadcasts
            for (int i = 0; i < broadcastCount; i++) {
                final int eventNum = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        OrderCreatedPayload payload = new OrderCreatedPayload(
                                (long) eventNum,
                                eventNum,
                                1,
                                BigDecimal.TEN,
                                null,
                                Instant.now()
                        );
                        SseEvent<OrderCreatedPayload> event = SseEvent.of(
                                SseEventType.ORDER_CREATED,
                                restaurantId,
                                payload
                        );
                        registry.broadcast(restaurantId, event);
                    } catch (Exception e) {
                        // Track errors
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Count total events received
            for (MockSseEmitter emitter : mockEmitters) {
                totalEventsReceived.addAndGet(emitter.getEventCount());
            }

            // Then
            assertThat(completed).isTrue();
            // Each emitter should receive each broadcast
            // Total = emitterCount * broadcastCount
            assertThat(totalEventsReceived.get()).isEqualTo(emitterCount * broadcastCount);
        }

        @Test
        @DisplayName("Should handle concurrent operations across multiple restaurants")
        void shouldHandleMultipleRestaurantsConcurrently() throws InterruptedException {
            // Given
            int restaurantCount = 10;
            int threadsPerRestaurant = 5;
            int totalThreads = restaurantCount * threadsPerRestaurant;
            ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalThreads);
            AtomicInteger errorCount = new AtomicInteger(0);

            // When
            for (int r = 0; r < restaurantCount; r++) {
                Long restaurantId = (long) (r + 1);
                for (int t = 0; t < threadsPerRestaurant; t++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            for (int i = 0; i < 20; i++) {
                                SseEmitter emitter = new SseEmitter();
                                registry.register(restaurantId, emitter);
                                
                                // Occasionally broadcast
                                if (i % 5 == 0) {
                                    SseEvent<?> event = SseEvent.heartbeat(restaurantId);
                                    registry.broadcast(restaurantId, event);
                                }
                                
                                registry.remove(restaurantId, emitter);
                            }
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(errorCount.get()).isEqualTo(0);
            assertThat(registry.getTotalConnectionCount()).isEqualTo(0);
        }
    }

    // ==================== Connection Limit Tests ====================

    @Nested
    @DisplayName("Connection Limits")
    class ConnectionLimitTests {

        @Test
        @DisplayName("Should handle 500 simultaneous connections per restaurant")
        void shouldHandleManyConnectionsPerRestaurant() {
            // Given
            int connectionCount = 500;
            Long restaurantId = 1L;
            List<SseEmitter> emitters = new ArrayList<>();

            // When
            for (int i = 0; i < connectionCount; i++) {
                SseEmitter emitter = new SseEmitter();
                emitters.add(emitter);
                registry.register(restaurantId, emitter);
            }

            // Then
            assertThat(registry.getConnectionCount(restaurantId)).isEqualTo(connectionCount);
            assertThat(registry.getTotalConnectionCount()).isEqualTo(connectionCount);

            // Cleanup
            for (SseEmitter emitter : emitters) {
                registry.remove(restaurantId, emitter);
            }
            assertThat(registry.getConnectionCount(restaurantId)).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle 1000 connections across 100 restaurants")
        void shouldHandleManyRestaurants() {
            // Given
            int restaurantCount = 100;
            int connectionsPerRestaurant = 10;
            Map<Long, List<SseEmitter>> emittersByRestaurant = new ConcurrentHashMap<>();

            // When
            for (int r = 0; r < restaurantCount; r++) {
                Long restaurantId = (long) (r + 1);
                List<SseEmitter> emitters = new ArrayList<>();
                for (int c = 0; c < connectionsPerRestaurant; c++) {
                    SseEmitter emitter = new SseEmitter();
                    emitters.add(emitter);
                    registry.register(restaurantId, emitter);
                }
                emittersByRestaurant.put(restaurantId, emitters);
            }

            // Then
            assertThat(registry.getTotalConnectionCount())
                    .isEqualTo(restaurantCount * connectionsPerRestaurant);
            assertThat(registry.getActiveRestaurantIds()).hasSize(restaurantCount);

            // Verify each restaurant has correct count
            for (long r = 1; r <= restaurantCount; r++) {
                assertThat(registry.getConnectionCount(r)).isEqualTo(connectionsPerRestaurant);
            }
        }
    }

    // ==================== Heartbeat Under Load Tests ====================

    @Nested
    @DisplayName("Heartbeat Under Load")
    class HeartbeatUnderLoadTests {

        @Test
        @DisplayName("Should send heartbeats to all connections reliably")
        void shouldSendHeartbeatsToAllConnections() throws InterruptedException {
            // Given
            int restaurantCount = 10;
            int connectionsPerRestaurant = 10;
            AtomicInteger heartbeatsReceived = new AtomicInteger(0);
            List<MockSseEmitter> allEmitters = new ArrayList<>();

            for (int r = 0; r < restaurantCount; r++) {
                Long restaurantId = (long) (r + 1);
                for (int c = 0; c < connectionsPerRestaurant; c++) {
                    MockSseEmitter emitter = new MockSseEmitter();
                    allEmitters.add(emitter);
                    registry.register(restaurantId, emitter);
                }
            }

            // When - Send heartbeats 3 times
            for (int i = 0; i < 3; i++) {
                registry.sendHeartbeatToAll();
                Thread.sleep(50); // Small delay between heartbeats
            }

            // Then - Count heartbeats received
            for (MockSseEmitter emitter : allEmitters) {
                heartbeatsReceived.addAndGet(emitter.getEventCount());
            }

            int expectedHeartbeats = restaurantCount * connectionsPerRestaurant * 3;
            assertThat(heartbeatsReceived.get()).isEqualTo(expectedHeartbeats);
        }
    }

    // ==================== Dead Connection Cleanup Tests ====================

    @Nested
    @DisplayName("Dead Connection Cleanup")
    class DeadConnectionCleanupTests {

        @Test
        @DisplayName("Should remove failed emitters during broadcast")
        void shouldRemoveFailedEmittersDuringBroadcast() {
            // Given
            Long restaurantId = 1L;
            int totalEmitters = 10;
            int failingEmitters = 3;

            // Register mix of working and failing emitters
            for (int i = 0; i < totalEmitters - failingEmitters; i++) {
                MockSseEmitter emitter = new MockSseEmitter();
                registry.register(restaurantId, emitter);
            }
            for (int i = 0; i < failingEmitters; i++) {
                FailingSseEmitter emitter = new FailingSseEmitter();
                registry.register(restaurantId, emitter);
            }

            assertThat(registry.getConnectionCount(restaurantId)).isEqualTo(totalEmitters);

            // When - Broadcast (failing emitters should be removed)
            SseEvent<?> event = SseEvent.heartbeat(restaurantId);
            registry.broadcast(restaurantId, event);

            // Then - Failing emitters should be removed
            assertThat(registry.getConnectionCount(restaurantId))
                    .isEqualTo(totalEmitters - failingEmitters);
        }
    }

    // ==================== Memory Leak Prevention Tests ====================

    @Nested
    @DisplayName("Memory Leak Prevention")
    class MemoryLeakTests {

        @Test
        @DisplayName("Should clean up restaurant entry when all emitters removed")
        void shouldCleanupEmptyRestaurantEntries() {
            // Given
            int restaurantCount = 100;
            
            for (int r = 0; r < restaurantCount; r++) {
                Long restaurantId = (long) (r + 1);
                SseEmitter emitter = new SseEmitter();
                registry.register(restaurantId, emitter);
                registry.remove(restaurantId, emitter);
            }

            // Then - All entries should be cleaned up
            assertThat(registry.getTotalConnectionCount()).isEqualTo(0);
            assertThat(registry.getActiveRestaurantIds()).isEmpty();
        }

        @Test
        @DisplayName("Should handle rapid connect/disconnect cycles")
        void shouldHandleRapidConnectDisconnectCycles() throws InterruptedException {
            // Given
            int cycles = 1000;
            Long restaurantId = 1L;
            ExecutorService executor = Executors.newFixedThreadPool(4);
            CountDownLatch doneLatch = new CountDownLatch(cycles);

            // When
            for (int i = 0; i < cycles; i++) {
                executor.submit(() -> {
                    try {
                        SseEmitter emitter = new SseEmitter();
                        registry.register(restaurantId, emitter);
                        Thread.sleep(1); // Tiny delay
                        registry.remove(restaurantId, emitter);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            // May have some in-flight, but should be reasonable
            assertThat(registry.getConnectionCount(restaurantId)).isLessThanOrEqualTo(4);
            
            // Wait for cleanup
            Thread.sleep(100);
            assertThat(registry.getConnectionCount(restaurantId)).isEqualTo(0);
        }
    }

    // ==================== Helper Classes ====================

    /**
     * Mock SSE emitter that counts received events.
     */
    static class MockSseEmitter extends SseEmitter {
        private final AtomicInteger eventCount = new AtomicInteger(0);

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            eventCount.incrementAndGet();
        }

        int getEventCount() {
            return eventCount.get();
        }
    }

    /**
     * SSE emitter that always fails to send.
     */
    static class FailingSseEmitter extends SseEmitter {
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("Simulated connection failure");
        }
    }
}






