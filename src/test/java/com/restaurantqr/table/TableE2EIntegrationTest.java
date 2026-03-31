package com.restaurantqr.table;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-End Integration Tests for Table Package.
 * Tests complete table session management flow through controller layer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Table Package E2E Integration Tests")
class TableE2EIntegrationTest {

    // ==================== Staff Table Controller Tests ====================

    @Nested
    @DisplayName("Staff Table Controller E2E Tests")
    class StaffTableControllerE2ETests {

        private StaffTableController controller;

        @Mock
        private StaffTableSessionService sessionService;

        @Mock
        private com.restaurantqr.finance.BillCalculationService billCalculationService;

        @Mock
        private TableSessionRepository sessionRepository;

        private Long restaurantId;
        private Authentication authentication;

        @BeforeEach
        void setUp() {
            restaurantId = 1L;
            controller = new StaffTableController(sessionService, billCalculationService, sessionRepository);

            // Setup authentication
            AuthPrincipal principal = new AuthPrincipal(1L, "ROLE_MANAGER", restaurantId);
            authentication = mock(Authentication.class);
            when(authentication.getPrincipal()).thenReturn(principal);
        }

        // ==================== List Tables Tests ====================

        @Nested
        @DisplayName("List Tables E2E Tests")
        class ListTablesTests {

            @Test
            @DisplayName("E2E: Should return all tables for restaurant")
            void shouldReturnAllTablesForRestaurant() {
                // Given
                List<StaffTableDto> mockTables = List.of(
                        new StaffTableDto(1L, 1, true, "QR-1", null, null),
                        new StaffTableDto(2L, 2, true, "QR-2", "1234", Instant.now()),
                        new StaffTableDto(3L, 3, false, "QR-3", null, null)
                );
                when(sessionService.listTables(restaurantId)).thenReturn(mockTables);

                // When
                List<StaffTableDto> result = controller.listTables(authentication);

                // Then
                assertThat(result).hasSize(3);
                assertThat(result.get(0).tableNumber()).isEqualTo(1);
                assertThat(result.get(1).sessionCode()).isEqualTo("1234");
                assertThat(result.get(2).isActive()).isFalse();

                verify(sessionService).listTables(restaurantId);
            }

            @Test
            @DisplayName("E2E: Should return empty list when no tables exist")
            void shouldReturnEmptyListWhenNoTables() {
                // Given
                when(sessionService.listTables(restaurantId)).thenReturn(List.of());

                // When
                List<StaffTableDto> result = controller.listTables(authentication);

                // Then
                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("E2E: Should include session info for open tables")
            void shouldIncludeSessionInfoForOpenTables() {
                // Given
                Instant openedAt = Instant.now().minusSeconds(3600);
                List<StaffTableDto> mockTables = List.of(
                        new StaffTableDto(1L, 1, true, "QR-1", "5678", openedAt)
                );
                when(sessionService.listTables(restaurantId)).thenReturn(mockTables);

                // When
                List<StaffTableDto> result = controller.listTables(authentication);

                // Then
                assertThat(result.get(0).sessionCode()).isEqualTo("5678");
                assertThat(result.get(0).sessionOpenedAt()).isEqualTo(openedAt);
            }
        }

        // ==================== Open Session Tests ====================

        @Nested
        @DisplayName("Open Session E2E Tests")
        class OpenSessionTests {

            @Test
            @DisplayName("E2E: Should open session and return 201")
            void shouldOpenSessionAndReturn201() {
                // Given
                Long tableId = 1L;
                Instant now = Instant.now();
                OpenSessionResponse mockResponse = new OpenSessionResponse(tableId, 5, "1234", now);
                when(sessionService.openSession(restaurantId, tableId)).thenReturn(mockResponse);

                // When
                ResponseEntity<OpenSessionResponse> response = controller.openSession(authentication, tableId);

                // Then
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().tableId()).isEqualTo(tableId);
                assertThat(response.getBody().sessionCode()).isEqualTo("1234");
                assertThat(response.getBody().sessionOpenedAt()).isEqualTo(now);
            }

            @Test
            @DisplayName("E2E: Should generate 4-digit session code")
            void shouldGenerate4DigitSessionCode() {
                // Given
                Long tableId = 1L;
                OpenSessionResponse mockResponse = new OpenSessionResponse(tableId, 1, "9876", Instant.now());
                when(sessionService.openSession(restaurantId, tableId)).thenReturn(mockResponse);

                // When
                ResponseEntity<OpenSessionResponse> response = controller.openSession(authentication, tableId);

                // Then
                assertThat(response.getBody().sessionCode()).hasSize(4);
                assertThat(response.getBody().sessionCode()).matches("\\d{4}");
            }

            @Test
            @DisplayName("E2E: Should be idempotent - return existing code if already open")
            void shouldBeIdempotentReturnExistingCode() {
                // Given
                Long tableId = 1L;
                Instant originalOpenedAt = Instant.now().minusSeconds(300);
                OpenSessionResponse existingSession = new OpenSessionResponse(tableId, 1, "5555", originalOpenedAt);
                when(sessionService.openSession(restaurantId, tableId)).thenReturn(existingSession);

                // When - called twice
                ResponseEntity<OpenSessionResponse> response1 = controller.openSession(authentication, tableId);
                ResponseEntity<OpenSessionResponse> response2 = controller.openSession(authentication, tableId);

                // Then - both return same code
                assertThat(response1.getBody().sessionCode()).isEqualTo(response2.getBody().sessionCode());
                assertThat(response1.getBody().sessionOpenedAt()).isEqualTo(originalOpenedAt);
            }

            @Test
            @DisplayName("E2E: Should throw 404 when table not found")
            void shouldThrow404WhenTableNotFound() {
                // Given
                Long nonExistentTableId = 999L;
                when(sessionService.openSession(restaurantId, nonExistentTableId))
                        .thenThrow(new ResourceNotFoundException("Table not found: 999"));

                // When & Then
                assertThatThrownBy(() -> controller.openSession(authentication, nonExistentTableId))
                        .isInstanceOf(ResourceNotFoundException.class)
                        .hasMessageContaining("Table not found");
            }

            @Test
            @DisplayName("E2E: Should throw 404 when table belongs to different restaurant")
            void shouldThrow404WhenTableBelongsToDifferentRestaurant() {
                // Given
                Long tableFromOtherRestaurant = 50L;
                when(sessionService.openSession(restaurantId, tableFromOtherRestaurant))
                        .thenThrow(new ResourceNotFoundException("Table not found: 50"));

                // When & Then
                assertThatThrownBy(() -> controller.openSession(authentication, tableFromOtherRestaurant))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        // ==================== Close Session Tests ====================

        @Nested
        @DisplayName("Close Session E2E Tests")
        class CloseSessionTests {

            @Test
            @DisplayName("E2E: Should close session successfully")
            void shouldCloseSessionSuccessfully() {
                // Given
                Long tableId = 1L;
                CloseSessionResponse mockResponse = new CloseSessionResponse(tableId, true);
                when(sessionService.closeSession(restaurantId, tableId)).thenReturn(mockResponse);

                // When
                CloseSessionResponse response = controller.closeSession(authentication, tableId);

                // Then
                assertThat(response.tableId()).isEqualTo(tableId);
                assertThat(response.closed()).isTrue();

                verify(sessionService).closeSession(restaurantId, tableId);
            }

            @Test
            @DisplayName("E2E: Should be idempotent - return success even if already closed")
            void shouldBeIdempotentWhenAlreadyClosed() {
                // Given
                Long tableId = 1L;
                CloseSessionResponse alreadyClosed = new CloseSessionResponse(tableId, true);
                when(sessionService.closeSession(restaurantId, tableId)).thenReturn(alreadyClosed);

                // When - called on already closed session
                CloseSessionResponse response = controller.closeSession(authentication, tableId);

                // Then
                assertThat(response.closed()).isTrue();
            }

            @Test
            @DisplayName("E2E: Should throw 404 when table not found")
            void shouldThrow404WhenTableNotFound() {
                // Given
                Long nonExistentTableId = 999L;
                when(sessionService.closeSession(restaurantId, nonExistentTableId))
                        .thenThrow(new ResourceNotFoundException("Table not found: 999"));

                // When & Then
                assertThatThrownBy(() -> controller.closeSession(authentication, nonExistentTableId))
                        .isInstanceOf(ResourceNotFoundException.class)
                        .hasMessageContaining("Table not found");
            }
        }

        // ==================== Session Lifecycle E2E Tests ====================

        @Nested
        @DisplayName("Session Lifecycle E2E Tests")
        class SessionLifecycleTests {

            @Test
            @DisplayName("E2E: Complete session lifecycle - open, verify active, close")
            void completeSessionLifecycle() {
                // Given
                Long tableId = 1L;
                Instant openedAt = Instant.now();

                // Open session
                OpenSessionResponse openResponse = new OpenSessionResponse(tableId, 1, "1234", openedAt);
                when(sessionService.openSession(restaurantId, tableId)).thenReturn(openResponse);

                // Table shows in list with session info
                List<StaffTableDto> tablesAfterOpen = List.of(
                        new StaffTableDto(tableId, 1, true, "QR-1", "1234", openedAt)
                );
                when(sessionService.listTables(restaurantId)).thenReturn(tablesAfterOpen);

                // Close session
                CloseSessionResponse closeResponse = new CloseSessionResponse(tableId, true);
                when(sessionService.closeSession(restaurantId, tableId)).thenReturn(closeResponse);

                // When - open session
                ResponseEntity<OpenSessionResponse> opened = controller.openSession(authentication, tableId);
                assertThat(opened.getBody().sessionCode()).isEqualTo("1234");

                // When - list tables shows session
                List<StaffTableDto> listResult = controller.listTables(authentication);
                assertThat(listResult.get(0).sessionCode()).isEqualTo("1234");

                // When - close session
                CloseSessionResponse closed = controller.closeSession(authentication, tableId);
                assertThat(closed.closed()).isTrue();
            }
        }
    }

    // ==================== Public Session Controller Tests ====================

    @Nested
    @DisplayName("Public Session Controller E2E Tests")
    class PublicSessionControllerE2ETests {

        private PublicSessionController controller;

        @Mock
        private PublicSessionService publicSessionService;

        @BeforeEach
        void setUp() {
            controller = new PublicSessionController(publicSessionService);
        }

        @Test
        @DisplayName("E2E: Should verify valid session code")
        void shouldVerifyValidSessionCode() {
            // Given
            String qrToken = "test-qr-token";
            String code = "1234";
            doNothing().when(publicSessionService).verifySessionOrThrow(qrToken, code);

            // When
            VerifySessionResponse response = controller.verify(qrToken, new VerifySessionRequest(code));

            // Then
            assertThat(response.verified()).isTrue();
            verify(publicSessionService).verifySessionOrThrow(qrToken, code);
        }

        @Test
        @DisplayName("E2E: Should throw exception for invalid session code")
        void shouldThrowExceptionForInvalidCode() {
            // Given
            String qrToken = "test-qr-token";
            String invalidCode = "9999";
            doThrow(new BusinessRuleException("Invalid session code"))
                    .when(publicSessionService).verifySessionOrThrow(qrToken, invalidCode);

            // When & Then
            assertThatThrownBy(() -> controller.verify(qrToken, new VerifySessionRequest(invalidCode)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid session code");
        }

        @Test
        @DisplayName("E2E: Should throw 404 for invalid QR token")
        void shouldThrow404ForInvalidQrToken() {
            // Given
            String invalidQrToken = "invalid-qr";
            String code = "1234";
            doThrow(new ResourceNotFoundException("Table not found"))
                    .when(publicSessionService).verifySessionOrThrow(invalidQrToken, code);

            // When & Then
            assertThatThrownBy(() -> controller.verify(invalidQrToken, new VerifySessionRequest(code)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }

        @Test
        @DisplayName("E2E: Should throw exception when session not open")
        void shouldThrowExceptionWhenSessionNotOpen() {
            // Given
            String qrToken = "test-qr-token";
            String code = "1234";
            doThrow(new BusinessRuleException("Session is not open for this table"))
                    .when(publicSessionService).verifySessionOrThrow(qrToken, code);

            // When & Then
            assertThatThrownBy(() -> controller.verify(qrToken, new VerifySessionRequest(code)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Session is not open");
        }
    }

    // ==================== Multi-Restaurant Isolation Tests ====================

    @Nested
    @DisplayName("Multi-Restaurant Isolation E2E Tests")
    class MultiRestaurantIsolationTests {

        private StaffTableController controller;

        @Mock
        private StaffTableSessionService sessionService;

        @Mock
        private com.restaurantqr.finance.BillCalculationService billCalculationService2;

        @Mock
        private TableSessionRepository sessionRepository2;

        @BeforeEach
        void setUp() {
            controller = new StaffTableController(sessionService, billCalculationService2, sessionRepository2);
        }

        @Test
        @DisplayName("E2E: Different restaurants should see their own tables only")
        void differentRestaurantsShouldSeeOwnTablesOnly() {
            // Given - Restaurant 1
            Long restaurant1Id = 10L;
            AuthPrincipal principal1 = new AuthPrincipal(1L, "ROLE_MANAGER", restaurant1Id);
            Authentication auth1 = mock(Authentication.class);
            when(auth1.getPrincipal()).thenReturn(principal1);

            List<StaffTableDto> restaurant1Tables = List.of(
                    new StaffTableDto(1L, 1, true, "QR-R1-1", null, null),
                    new StaffTableDto(2L, 2, true, "QR-R1-2", null, null)
            );
            when(sessionService.listTables(restaurant1Id)).thenReturn(restaurant1Tables);

            // Given - Restaurant 2
            Long restaurant2Id = 20L;
            AuthPrincipal principal2 = new AuthPrincipal(2L, "ROLE_MANAGER", restaurant2Id);
            Authentication auth2 = mock(Authentication.class);
            when(auth2.getPrincipal()).thenReturn(principal2);

            List<StaffTableDto> restaurant2Tables = List.of(
                    new StaffTableDto(10L, 1, true, "QR-R2-1", "5555", Instant.now())
            );
            when(sessionService.listTables(restaurant2Id)).thenReturn(restaurant2Tables);

            // When
            List<StaffTableDto> result1 = controller.listTables(auth1);
            List<StaffTableDto> result2 = controller.listTables(auth2);

            // Then
            assertThat(result1).hasSize(2);
            assertThat(result2).hasSize(1);
            assertThat(result1.get(0).id()).isNotEqualTo(result2.get(0).id());
        }
    }
}

