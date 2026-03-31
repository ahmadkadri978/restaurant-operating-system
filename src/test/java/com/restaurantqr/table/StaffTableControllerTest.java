package com.restaurantqr.table;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.BillCalculationService;
import com.restaurantqr.finance.dto.BillPreviewDto;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for StaffTableController — remaining controller gaps.
 * Tests getBillPreview and closeSessionWithPayment endpoints
 * not covered by TableE2EIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StaffTableController Unit Tests")
class StaffTableControllerTest {

    @Mock private StaffTableSessionService sessionService;
    @Mock private BillCalculationService billCalculationService;
    @Mock private TableSessionRepository sessionRepository;

    private StaffTableController controller;

    private Authentication authentication;
    private static final Long RESTAURANT_ID = 1L;
    private static final Long TABLE_ID = 5L;
    private static final Long SESSION_ID = 100L;
    private static final Long USER_ID = 10L;

    @BeforeEach
    void setUp() {
        controller = new StaffTableController(sessionService, billCalculationService, sessionRepository);

        AuthPrincipal principal = new AuthPrincipal(USER_ID, "MANAGER", RESTAURANT_ID);
        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    // ==================== Bill Preview ====================

    @Nested
    @DisplayName("getBillPreview")
    class BillPreviewTests {

        @Test
        @DisplayName("Should return 200 with bill preview for active session")
        void shouldReturn200WithBillPreview() {
            // Given
            TableSession session = createSessionForRestaurant(RESTAURANT_ID);
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID)).thenReturn(Optional.of(session));

            BillPreviewDto preview = new BillPreviewDto(
                    5, "1234", Instant.now(),
                    new BigDecimal("20000.00"), new BigDecimal("5000.00"),
                    new BigDecimal("25000.00"), "SYP",
                    List.of(), List.of()
            );
            when(billCalculationService.computeBillPreview(SESSION_ID)).thenReturn(preview);

            // When
            ResponseEntity<BillPreviewDto> response = controller.getBillPreview(authentication, TABLE_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().grandTotal()).isEqualByComparingTo(new BigDecimal("25000.00"));
            assertThat(response.getBody().tableNumber()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should delegate to billCalculationService with session ID")
        void shouldDelegateWithSessionId() {
            // Given
            TableSession session = createSessionForRestaurant(RESTAURANT_ID);
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID)).thenReturn(Optional.of(session));

            BillPreviewDto preview = new BillPreviewDto(
                    5, "1234", Instant.now(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "SYP",
                    List.of(), List.of()
            );
            when(billCalculationService.computeBillPreview(SESSION_ID)).thenReturn(preview);

            // When
            controller.getBillPreview(authentication, TABLE_ID);

            // Then
            verify(billCalculationService).computeBillPreview(SESSION_ID);
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when no active session")
        void shouldThrowWhenNoActiveSession() {
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.getBillPreview(authentication, TABLE_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("No active session for table");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when session belongs to different restaurant")
        void shouldThrowWhenSessionBelongsToDifferentRestaurant() {
            // Given — session belongs to restaurant 99, but auth is restaurant 1
            TableSession session = createSessionForRestaurant(99L);
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID)).thenReturn(Optional.of(session));

            // When & Then
            assertThatThrownBy(() -> controller.getBillPreview(authentication, TABLE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }
    }

    // ==================== Close Session With Payment ====================

    @Nested
    @DisplayName("closeSessionWithPayment")
    class CloseWithPaymentTests {

        @Test
        @DisplayName("Should return 200 on successful close with payment")
        void shouldReturn200OnSuccess() {
            // Given
            CloseWithPaymentRequest request = new CloseWithPaymentRequest(
                    new BigDecimal("30000.00"), new BigDecimal("2000.00")
            );
            CloseWithPaymentResponse expected = new CloseWithPaymentResponse(
                    TABLE_ID, true, 1L, "INV-20260331-001",
                    new BigDecimal("25000.00"), new BigDecimal("30000.00"),
                    new BigDecimal("2000.00"), new BigDecimal("3000.00")
            );
            when(sessionService.closeSessionWithPayment(
                    RESTAURANT_ID, TABLE_ID,
                    new BigDecimal("30000.00"), new BigDecimal("2000.00"),
                    USER_ID
            )).thenReturn(expected);

            // When
            ResponseEntity<CloseWithPaymentResponse> response =
                    controller.closeSessionWithPayment(authentication, TABLE_ID, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().invoiceNumber()).isEqualTo("INV-20260331-001");
            assertThat(response.getBody().grandTotal()).isEqualByComparingTo(new BigDecimal("25000.00"));
            assertThat(response.getBody().amountReturned()).isEqualByComparingTo(new BigDecimal("3000.00"));
        }

        @Test
        @DisplayName("Should delegate with correct parameters including userId")
        void shouldDelegateWithCorrectParams() {
            // Given
            CloseWithPaymentRequest request = new CloseWithPaymentRequest(
                    new BigDecimal("10000.00"), BigDecimal.ZERO
            );
            CloseWithPaymentResponse expected = new CloseWithPaymentResponse(
                    TABLE_ID, true, 1L, "INV-001",
                    new BigDecimal("10000.00"), new BigDecimal("10000.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO
            );
            when(sessionService.closeSessionWithPayment(
                    RESTAURANT_ID, TABLE_ID,
                    new BigDecimal("10000.00"), BigDecimal.ZERO,
                    USER_ID
            )).thenReturn(expected);

            // When
            controller.closeSessionWithPayment(authentication, TABLE_ID, request);

            // Then
            verify(sessionService).closeSessionWithPayment(
                    RESTAURANT_ID, TABLE_ID,
                    new BigDecimal("10000.00"), BigDecimal.ZERO,
                    USER_ID
            );
        }

        @Test
        @DisplayName("Should propagate BusinessRuleException on underpayment")
        void shouldPropagateBusinessRuleOnUnderpayment() {
            // Given
            CloseWithPaymentRequest request = new CloseWithPaymentRequest(
                    new BigDecimal("1000.00"), BigDecimal.ZERO
            );
            when(sessionService.closeSessionWithPayment(
                    eq(RESTAURANT_ID), eq(TABLE_ID), any(), any(), eq(USER_ID)
            )).thenThrow(new BusinessRuleException("Amount received is less than grand total"));

            // When & Then
            assertThatThrownBy(() ->
                    controller.closeSessionWithPayment(authentication, TABLE_ID, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Amount received is less than grand total");
        }

        @Test
        @DisplayName("Should propagate ResourceNotFoundException when table not found")
        void shouldPropagateNotFoundForTable() {
            // Given
            CloseWithPaymentRequest request = new CloseWithPaymentRequest(
                    new BigDecimal("10000.00"), BigDecimal.ZERO
            );
            when(sessionService.closeSessionWithPayment(
                    eq(RESTAURANT_ID), eq(TABLE_ID), any(), any(), eq(USER_ID)
            )).thenThrow(new ResourceNotFoundException("Table not found: " + TABLE_ID));

            // When & Then
            assertThatThrownBy(() ->
                    controller.closeSessionWithPayment(authentication, TABLE_ID, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }
    }

    // ==================== Helpers ====================

    private TableSession createSessionForRestaurant(Long restaurantId) {
        com.restaurantqr.restaurant.Restaurant restaurant = new com.restaurantqr.restaurant.Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setName("Test");

        RestaurantTable table = new RestaurantTable();
        table.setId(TABLE_ID);
        table.setTableNumber(5);

        TableSession session = new TableSession(restaurant, table, "1234", Instant.now());
        // Set ID via reflection since JPA normally sets it
        try {
            java.lang.reflect.Field idField = TableSession.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(session, SESSION_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return session;
    }
}

