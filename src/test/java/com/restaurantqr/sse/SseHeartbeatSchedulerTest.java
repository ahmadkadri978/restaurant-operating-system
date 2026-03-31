package com.restaurantqr.sse;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Unit Tests for SseHeartbeatScheduler.
 * Tests heartbeat dispatch logic and zero-connection skip behavior
 * for both staff and public registries.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SseHeartbeatScheduler Unit Tests")
class SseHeartbeatSchedulerTest {

    @Mock
    private SseEmitterRegistry registry;

    @Mock
    private PublicMenuSseRegistry publicMenuRegistry;

    private SseHeartbeatScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SseHeartbeatScheduler(registry, publicMenuRegistry);
    }

    // ==================== Staff Heartbeats ====================

    @Nested
    @DisplayName("sendHeartbeats — Staff")
    class StaffHeartbeats {

        @Test
        @DisplayName("Should send heartbeats when staff connections exist")
        void shouldSendHeartbeatsWhenConnectionsExist() {
            when(registry.getTotalConnectionCount()).thenReturn(5);

            scheduler.sendHeartbeats();

            verify(registry).sendHeartbeatToAll();
        }

        @Test
        @DisplayName("Should skip heartbeats when no staff connections")
        void shouldSkipHeartbeatsWhenNoConnections() {
            when(registry.getTotalConnectionCount()).thenReturn(0);

            scheduler.sendHeartbeats();

            verify(registry, never()).sendHeartbeatToAll();
        }

        @Test
        @DisplayName("Should check connection count before sending")
        void shouldCheckConnectionCountBeforeSending() {
            when(registry.getTotalConnectionCount()).thenReturn(3);

            scheduler.sendHeartbeats();

            InOrder inOrder = inOrder(registry);
            inOrder.verify(registry).getTotalConnectionCount();
            inOrder.verify(registry).sendHeartbeatToAll();
        }
    }

    // ==================== Public Heartbeats ====================

    @Nested
    @DisplayName("sendPublicHeartbeats — Public Menu")
    class PublicHeartbeats {

        @Test
        @DisplayName("Should send heartbeats when public connections exist")
        void shouldSendPublicHeartbeatsWhenConnectionsExist() {
            when(publicMenuRegistry.getTotalConnectionCount()).thenReturn(10);

            scheduler.sendPublicHeartbeats();

            verify(publicMenuRegistry).sendHeartbeatToAll();
        }

        @Test
        @DisplayName("Should skip heartbeats when no public connections")
        void shouldSkipPublicHeartbeatsWhenNoConnections() {
            when(publicMenuRegistry.getTotalConnectionCount()).thenReturn(0);

            scheduler.sendPublicHeartbeats();

            verify(publicMenuRegistry, never()).sendHeartbeatToAll();
        }
    }
}

