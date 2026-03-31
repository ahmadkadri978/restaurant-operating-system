package com.restaurantqr.table;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.*;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.user.User;
import com.restaurantqr.user.UserRepository;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StaffTableSessionService Unit Tests")
class StaffTableSessionServiceTest {

    @Mock private RestaurantTableRepository tableRepository;
    @Mock private TableSessionRepository sessionRepository;
    @Mock private BillCalculationService billCalculationService;
    @Mock private InvoiceService invoiceService;
    @Mock private SessionPaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private CashRegisterSessionRepository cashRegisterRepository;

    private StaffTableSessionService service;

    private Restaurant testRestaurant;
    private RestaurantTable testTable;
    private TableSession testSession;
    private SessionBill testBill;
    private SessionInvoice testInvoice;
    private User testUser;
    private static final Long RESTAURANT_ID = 1L;
    private static final Long TABLE_ID = 5L;
    private static final Long USER_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new StaffTableSessionService(
                tableRepository, sessionRepository, billCalculationService,
                invoiceService, paymentRepository, userRepository, cashRegisterRepository
        );

        testRestaurant = new Restaurant();
        testRestaurant.setId(RESTAURANT_ID);

        testTable = new RestaurantTable();
        testTable.setId(TABLE_ID);
        testTable.setRestaurant(testRestaurant);
        testTable.setTableNumber(5);
        testTable.setSessionCode("1234");
        testTable.setSessionOpenedAt(Instant.now().minusSeconds(3600));

        testSession = new TableSession(testRestaurant, testTable, "1234", testTable.getSessionOpenedAt());
        testSession.setId(100L);

        testBill = new SessionBill();
        testBill.setId(200L);
        testBill.setGrandTotal(new BigDecimal("10000"));

        testInvoice = mock(SessionInvoice.class);
        when(testInvoice.getId()).thenReturn(300L);
        when(testInvoice.getInvoiceNumber()).thenReturn("INV-20260331-001");

        testUser = new User();
        testUser.setId(USER_ID);
        testUser.setUsername("manager1");
    }

    // ==================== Close With Payment Tests ====================

    @Nested
    @DisplayName("List Tables")
    class ListTablesTests {

        @Test
        @DisplayName("Should return list of StaffTableDto for a restaurant")
        void shouldReturnTableDtos() {
            RestaurantTable table2 = new RestaurantTable();
            table2.setId(6L);
            table2.setRestaurant(testRestaurant);
            table2.setTableNumber(6);
            table2.setActive(true);
            table2.setQrToken("token-6");
            table2.setSessionCode(null);
            table2.setSessionOpenedAt(null);

            testTable.setQrToken("token-5");

            when(tableRepository.findByRestaurantIdOrderByTableNumberAsc(RESTAURANT_ID))
                    .thenReturn(List.of(testTable, table2));

            List<StaffTableDto> result = service.listTables(RESTAURANT_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(TABLE_ID);
            assertThat(result.get(0).tableNumber()).isEqualTo(5);
            assertThat(result.get(0).sessionCode()).isEqualTo("1234");
            assertThat(result.get(1).id()).isEqualTo(6L);
            assertThat(result.get(1).sessionCode()).isNull();
        }

        @Test
        @DisplayName("Should return empty list when restaurant has no tables")
        void shouldReturnEmptyList() {
            when(tableRepository.findByRestaurantIdOrderByTableNumberAsc(RESTAURANT_ID))
                    .thenReturn(Collections.emptyList());

            List<StaffTableDto> result = service.listTables(RESTAURANT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should throw when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            assertThatThrownBy(() -> service.listTables(null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("restaurantId is required");
        }
    }

    // ==================== Open Session Tests ====================

    @Nested
    @DisplayName("Open Session")
    class OpenSessionTests {

        @BeforeEach
        void setUpClosedTable() {
            // Start with a closed table (no active session)
            testTable.setSessionCode(null);
            testTable.setSessionOpenedAt(null);
            when(tableRepository.findByIdForUpdate(TABLE_ID)).thenReturn(Optional.of(testTable));
            when(tableRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(sessionRepository.save(any(TableSession.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("Should open session and return response with code")
        void shouldOpenSession() {
            OpenSessionResponse response = service.openSession(RESTAURANT_ID, TABLE_ID);

            assertThat(response.tableId()).isEqualTo(TABLE_ID);
            assertThat(response.tableNumber()).isEqualTo(5);
            assertThat(response.sessionCode()).hasSize(4);
            assertThat(response.sessionOpenedAt()).isNotNull();

            verify(tableRepository).saveAndFlush(any());
            verify(sessionRepository).save(any(TableSession.class));
        }

        @Test
        @DisplayName("Should return existing session idempotently if already open")
        void shouldReturnExistingSession() {
            testTable.setSessionCode("5678");
            Instant openedAt = Instant.now().minusSeconds(600);
            testTable.setSessionOpenedAt(openedAt);

            OpenSessionResponse response = service.openSession(RESTAURANT_ID, TABLE_ID);

            assertThat(response.tableId()).isEqualTo(TABLE_ID);
            assertThat(response.sessionCode()).isEqualTo("5678");
            assertThat(response.sessionOpenedAt()).isEqualTo(openedAt);

            // Should NOT save again
            verify(tableRepository, never()).saveAndFlush(any());
            verify(sessionRepository, never()).save(any(TableSession.class));
        }

        @Test
        @DisplayName("Should throw when table not found")
        void shouldThrowWhenTableNotFound() {
            when(tableRepository.findByIdForUpdate(TABLE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.openSession(RESTAURANT_ID, TABLE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw when table belongs to different restaurant")
        void shouldThrowWhenWrongRestaurant() {
            assertThatThrownBy(() -> service.openSession(999L, TABLE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            assertThatThrownBy(() -> service.openSession(null, TABLE_ID))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should throw when tableId is null")
        void shouldThrowWhenTableIdNull() {
            assertThatThrownBy(() -> service.openSession(RESTAURANT_ID, null))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should retry on session code collision and succeed")
        void shouldRetryOnCollision() {
            // First saveAndFlush throws collision, second succeeds
            when(tableRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry"))
                    .thenAnswer(inv -> inv.getArgument(0));

            OpenSessionResponse response = service.openSession(RESTAURANT_ID, TABLE_ID);

            assertThat(response.sessionCode()).hasSize(4);
            verify(tableRepository, times(2)).saveAndFlush(any());
        }

        @Test
        @DisplayName("Should throw after max collision attempts exhausted")
        void shouldThrowAfterMaxRetries() {
            when(tableRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

            assertThatThrownBy(() -> service.openSession(RESTAURANT_ID, TABLE_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Unable to generate session code");

            verify(tableRepository, times(30)).saveAndFlush(any());
        }
    }

    // ==================== Close Session Tests ====================

    @Nested
    @DisplayName("Close Session")
    class CloseSessionTests {

        @BeforeEach
        void setUpOpenTable() {
            when(tableRepository.findByIdForUpdate(TABLE_ID)).thenReturn(Optional.of(testTable));
            when(tableRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("Should close session, finalize bill and generate invoice")
        void shouldCloseSession() {
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID)).thenReturn(Optional.of(testSession));
            when(billCalculationService.finalizeBill(testSession.getId())).thenReturn(testBill);
            when(invoiceService.generateInvoice(testSession.getId())).thenReturn(testInvoice);

            CloseSessionResponse response = service.closeSession(RESTAURANT_ID, TABLE_ID);

            assertThat(response.tableId()).isEqualTo(TABLE_ID);
            assertThat(response.closed()).isTrue();

            verify(sessionRepository).save(testSession);
            verify(billCalculationService).finalizeBill(testSession.getId());
            verify(invoiceService).generateInvoice(testSession.getId());
            verify(tableRepository).saveAndFlush(any());
        }

        @Test
        @DisplayName("Should return idempotently when session already closed")
        void shouldReturnIdempotentlyWhenAlreadyClosed() {
            testTable.setSessionCode(null);

            CloseSessionResponse response = service.closeSession(RESTAURANT_ID, TABLE_ID);

            assertThat(response.tableId()).isEqualTo(TABLE_ID);
            assertThat(response.closed()).isTrue();

            // Should NOT attempt to close session or finalize bill
            verify(sessionRepository, never()).save(any());
            verify(billCalculationService, never()).finalizeBill(anyLong());
            verify(tableRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Should clear table session fields after closing")
        void shouldClearSessionFields() {
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID)).thenReturn(Optional.of(testSession));
            when(billCalculationService.finalizeBill(testSession.getId())).thenReturn(testBill);
            when(invoiceService.generateInvoice(testSession.getId())).thenReturn(testInvoice);

            service.closeSession(RESTAURANT_ID, TABLE_ID);

            assertThat(testTable.getSessionCode()).isNull();
            assertThat(testTable.getSessionOpenedAt()).isNull();
        }

        @Test
        @DisplayName("Should not block close when bill finalization fails")
        void shouldNotBlockWhenBillFails() {
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID)).thenReturn(Optional.of(testSession));
            when(billCalculationService.finalizeBill(testSession.getId()))
                    .thenThrow(new RuntimeException("Bill computation error"));
            when(invoiceService.generateInvoice(testSession.getId())).thenReturn(testInvoice);

            CloseSessionResponse response = service.closeSession(RESTAURANT_ID, TABLE_ID);

            assertThat(response.closed()).isTrue();
            verify(tableRepository).saveAndFlush(any());
        }

        @Test
        @DisplayName("Should not block close when invoice generation fails")
        void shouldNotBlockWhenInvoiceFails() {
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID)).thenReturn(Optional.of(testSession));
            when(billCalculationService.finalizeBill(testSession.getId())).thenReturn(testBill);
            when(invoiceService.generateInvoice(testSession.getId()))
                    .thenThrow(new RuntimeException("Invoice generation error"));

            CloseSessionResponse response = service.closeSession(RESTAURANT_ID, TABLE_ID);

            assertThat(response.closed()).isTrue();
            verify(tableRepository).saveAndFlush(any());
        }

        @Test
        @DisplayName("Should close gracefully when no session history record exists")
        void shouldCloseWhenNoSessionHistory() {
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID)).thenReturn(Optional.empty());

            CloseSessionResponse response = service.closeSession(RESTAURANT_ID, TABLE_ID);

            assertThat(response.closed()).isTrue();
            // Bill and invoice should NOT be called since no session was found
            verify(billCalculationService, never()).finalizeBill(anyLong());
            verify(invoiceService, never()).generateInvoice(anyLong());
        }

        @Test
        @DisplayName("Should throw when table not found")
        void shouldThrowWhenTableNotFound() {
            when(tableRepository.findByIdForUpdate(TABLE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.closeSession(RESTAURANT_ID, TABLE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw when table belongs to different restaurant")
        void shouldThrowWhenWrongRestaurant() {
            assertThatThrownBy(() -> service.closeSession(999L, TABLE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            assertThatThrownBy(() -> service.closeSession(null, TABLE_ID))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should throw when tableId is null")
        void shouldThrowWhenTableIdNull() {
            assertThatThrownBy(() -> service.closeSession(RESTAURANT_ID, null))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    @DisplayName("Close Session With Payment")
    class CloseWithPaymentTests {

        @BeforeEach
        void setUpMocks() {
            when(tableRepository.findByIdForUpdate(TABLE_ID)).thenReturn(Optional.of(testTable));
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID)).thenReturn(Optional.of(testSession));
            when(billCalculationService.finalizeBill(testSession.getId())).thenReturn(testBill);
            when(invoiceService.generateInvoice(testSession.getId())).thenReturn(testInvoice);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            when(paymentRepository.save(any(SessionPayment.class))).thenAnswer(inv -> {
                SessionPayment p = inv.getArgument(0);
                p.setId(400L);
                return p;
            });
            when(tableRepository.saveAndFlush(any())).thenReturn(testTable);
        }

        @Test
        @DisplayName("Should close session and record payment correctly")
        void shouldCloseAndRecordPayment() {
            CloseWithPaymentResponse response = service.closeSessionWithPayment(
                    RESTAURANT_ID, TABLE_ID, new BigDecimal("12000"), new BigDecimal("1000"), USER_ID);

            assertThat(response.tableId()).isEqualTo(TABLE_ID);
            assertThat(response.closed()).isTrue();
            assertThat(response.grandTotal()).isEqualByComparingTo("10000");
            assertThat(response.amountReceived()).isEqualByComparingTo("12000");
            assertThat(response.tipAmount()).isEqualByComparingTo("1000");
            // returned = 12000 - 10000 - 1000 = 1000
            assertThat(response.amountReturned()).isEqualByComparingTo("1000");

            ArgumentCaptor<SessionPayment> paymentCaptor = ArgumentCaptor.forClass(SessionPayment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            SessionPayment savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getGrandTotal()).isEqualByComparingTo("10000");
            assertThat(savedPayment.getAmountReceived()).isEqualByComparingTo("12000");
            assertThat(savedPayment.getTipAmount()).isEqualByComparingTo("1000");
            assertThat(savedPayment.getAmountReturned()).isEqualByComparingTo("1000");
            assertThat(savedPayment.getPaymentMethod()).isEqualTo("CASH");
        }

        @Test
        @DisplayName("Should reject payment when amount received is less than grand total")
        void shouldRejectInsufficientPayment() {
            assertThatThrownBy(() ->
                    service.closeSessionWithPayment(RESTAURANT_ID, TABLE_ID, new BigDecimal("8000"), BigDecimal.ZERO, USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("أقل من الإجمالي");
        }

        @Test
        @DisplayName("Should reject tip exceeding surplus")
        void shouldRejectTipExceedingSurplus() {
            // received=11000, grand=10000, surplus=1000, tip=1500 → invalid
            assertThatThrownBy(() ->
                    service.closeSessionWithPayment(RESTAURANT_ID, TABLE_ID, new BigDecimal("11000"), new BigDecimal("1500"), USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("البقشيش لا يمكن أن يتجاوز");
        }

        @Test
        @DisplayName("Should handle zero tip correctly")
        void shouldHandleZeroTip() {
            CloseWithPaymentResponse response = service.closeSessionWithPayment(
                    RESTAURANT_ID, TABLE_ID, new BigDecimal("10000"), BigDecimal.ZERO, USER_ID);

            assertThat(response.tipAmount()).isEqualByComparingTo("0");
            assertThat(response.amountReturned()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Should link payment to open cash register when one exists")
        void shouldLinkPaymentToOpenRegister() {
            CashRegisterSession register = new CashRegisterSession();
            register.setId(999L);
            when(cashRegisterRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN))
                    .thenReturn(Optional.of(register));

            service.closeSessionWithPayment(RESTAURANT_ID, TABLE_ID, new BigDecimal("10000"), BigDecimal.ZERO, USER_ID);

            ArgumentCaptor<SessionPayment> captor = ArgumentCaptor.forClass(SessionPayment.class);
            verify(paymentRepository).save(captor.capture());
            assertThat(captor.getValue().getCashRegisterSession()).isNotNull();
            assertThat(captor.getValue().getCashRegisterSession().getId()).isEqualTo(999L);
        }

        @Test
        @DisplayName("Should leave register null when no register is open")
        void shouldLeaveRegisterNullWhenNoneOpen() {
            when(cashRegisterRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN))
                    .thenReturn(Optional.empty());

            service.closeSessionWithPayment(RESTAURANT_ID, TABLE_ID, new BigDecimal("10000"), BigDecimal.ZERO, USER_ID);

            ArgumentCaptor<SessionPayment> captor = ArgumentCaptor.forClass(SessionPayment.class);
            verify(paymentRepository).save(captor.capture());
            assertThat(captor.getValue().getCashRegisterSession()).isNull();
        }

        @Test
        @DisplayName("Should reject negative amount received")
        void shouldRejectNegativeAmount() {
            assertThatThrownBy(() ->
                    service.closeSessionWithPayment(RESTAURANT_ID, TABLE_ID, new BigDecimal("-1"), BigDecimal.ZERO, USER_ID))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should reject negative tip")
        void shouldRejectNegativeTip() {
            assertThatThrownBy(() ->
                    service.closeSessionWithPayment(RESTAURANT_ID, TABLE_ID, new BigDecimal("10000"), new BigDecimal("-1"), USER_ID))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should throw when table not found")
        void shouldThrowWhenTableNotFound() {
            when(tableRepository.findByIdForUpdate(TABLE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.closeSessionWithPayment(RESTAURANT_ID, TABLE_ID, new BigDecimal("10000"), BigDecimal.ZERO, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw when table belongs to different restaurant")
        void shouldThrowWhenWrongRestaurant() {
            assertThatThrownBy(() ->
                    service.closeSessionWithPayment(999L, TABLE_ID, new BigDecimal("10000"), BigDecimal.ZERO, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw when no active session")
        void shouldThrowWhenNoActiveSession() {
            testTable.setSessionCode(null);

            assertThatThrownBy(() ->
                    service.closeSessionWithPayment(RESTAURANT_ID, TABLE_ID, new BigDecimal("10000"), BigDecimal.ZERO, USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("No active session");
        }
    }

    // ==================== Parameter Validation Tests ====================

    @Nested
    @DisplayName("Parameter Validation")
    class ParameterValidationTests {

        @Test
        @DisplayName("Should throw when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            assertThatThrownBy(() ->
                    service.closeSessionWithPayment(null, TABLE_ID, BigDecimal.TEN, BigDecimal.ZERO, USER_ID))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should throw when tableId is null")
        void shouldThrowWhenTableIdNull() {
            assertThatThrownBy(() ->
                    service.closeSessionWithPayment(RESTAURANT_ID, null, BigDecimal.TEN, BigDecimal.ZERO, USER_ID))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }
}




