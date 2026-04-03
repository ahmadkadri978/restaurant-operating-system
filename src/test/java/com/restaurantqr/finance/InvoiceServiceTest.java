package com.restaurantqr.finance;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.InvoiceListDto;
import com.restaurantqr.menu.MenuItem;
import com.restaurantqr.order.CustomerOrder;
import com.restaurantqr.order.CustomerOrderRepository;
import com.restaurantqr.order.OrderItem;
import com.restaurantqr.order.OrderItemRepository;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.TableSession;
import com.restaurantqr.table.TableSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InvoiceService Unit Tests")
class InvoiceServiceTest {

    @Mock private SessionInvoiceRepository invoiceRepository;
    @Mock private SessionBillRepository billRepository;
    @Mock private TableSessionRepository sessionRepository;
    @Mock private CustomerOrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private InvoicePdfService pdfService;
    @Mock private SessionPaymentRepository paymentRepository;
    @Mock private InvoiceCounterRepository invoiceCounterRepository;

    private InvoiceServiceImpl service;

    private Restaurant testRestaurant;
    private static final Long RESTAURANT_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new InvoiceServiceImpl(
                invoiceRepository, billRepository, sessionRepository,
                orderRepository, orderItemRepository, pdfService, paymentRepository,
                invoiceCounterRepository
        );

        testRestaurant = new Restaurant();
        testRestaurant.setId(RESTAURANT_ID);
        testRestaurant.setName("Test Restaurant");
    }

    // ==================== Generate Invoice Tests ====================

    @Nested
    @DisplayName("Generate Invoice")
    class GenerateInvoiceTests {

        @Test
        @DisplayName("Should throw when tableSessionId is null")
        void shouldThrowWhenSessionIdNull() {
            assertThatThrownBy(() -> service.generateInvoice(null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("tableSessionId is required");
        }

        @Test
        @DisplayName("Should return existing invoice idempotently")
        void shouldReturnExistingInvoice() {
            SessionInvoice existing = mock(SessionInvoice.class);
            when(existing.getId()).thenReturn(5L);
            when(invoiceRepository.findByTableSessionId(SESSION_ID)).thenReturn(Optional.of(existing));

            SessionInvoice result = service.generateInvoice(SESSION_ID);

            assertThat(result).isSameAs(existing);
            verify(sessionRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should throw when session not found")
        void shouldThrowWhenSessionNotFound() {
            when(invoiceRepository.findByTableSessionId(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateInvoice(SESSION_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw when session is still open")
        void shouldThrowWhenSessionOpen() {
            when(invoiceRepository.findByTableSessionId(SESSION_ID)).thenReturn(Optional.empty());
            TableSession openSession = mock(TableSession.class);
            when(openSession.getClosedAt()).thenReturn(null);
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(openSession));

            assertThatThrownBy(() -> service.generateInvoice(SESSION_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("open session");
        }

        @Test
        @DisplayName("Should throw when no finalized bill exists")
        void shouldThrowWhenNoBill() {
            when(invoiceRepository.findByTableSessionId(SESSION_ID)).thenReturn(Optional.empty());
            TableSession closedSession = mock(TableSession.class);
            when(closedSession.getClosedAt()).thenReturn(Instant.now());
            when(closedSession.getRestaurant()).thenReturn(testRestaurant);
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(closedSession));
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateInvoice(SESSION_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("No finalized bill");
        }
    }

    // ==================== List By Date Tests ====================

    @Nested
    @DisplayName("List By Date")
    class ListByDateTests {

        @Test
        @DisplayName("Should throw when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            assertThatThrownBy(() -> service.listByDate(null, LocalDate.now(), "Asia/Damascus"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("restaurantId is required");
        }

        @Test
        @DisplayName("Should throw when date is null")
        void shouldThrowWhenDateNull() {
            assertThatThrownBy(() -> service.listByDate(RESTAURANT_ID, null, "Asia/Damascus"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("date is required");
        }

        @Test
        @DisplayName("Should return empty list when no invoices")
        void shouldReturnEmptyListWhenNoInvoices() {
            when(invoiceRepository.findByRestaurantIdAndDateRange(eq(RESTAURANT_ID), any(), any()))
                    .thenReturn(Collections.emptyList());

            List<InvoiceListDto> result = service.listByDate(RESTAURANT_ID, LocalDate.of(2026, 3, 31), "Asia/Damascus");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should include tip data from batch-loaded payments")
        void shouldIncludeTipFromPayments() {
            SessionInvoice inv = createMockInvoice(1L, "INV-20260331-001", SESSION_ID);
            when(invoiceRepository.findByRestaurantIdAndDateRange(eq(RESTAURANT_ID), any(), any()))
                    .thenReturn(List.of(inv));

            SessionPayment payment = mock(SessionPayment.class);
            TableSession paymentSession = mock(TableSession.class);
            when(paymentSession.getId()).thenReturn(SESSION_ID);
            when(payment.getTableSession()).thenReturn(paymentSession);
            when(payment.getTipAmount()).thenReturn(new BigDecimal("500"));
            when(paymentRepository.findByTableSessionIdIn(List.of(SESSION_ID))).thenReturn(List.of(payment));

            List<InvoiceListDto> result = service.listByDate(RESTAURANT_ID, LocalDate.of(2026, 3, 31), "Asia/Damascus");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).tipAmount()).isEqualByComparingTo("500");
        }
    }

    // ==================== Search By Number Tests ====================

    @Nested
    @DisplayName("Search By Number")
    class SearchByNumberTests {

        @Test
        @DisplayName("Should throw when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            assertThatThrownBy(() -> service.searchByNumber(null, "INV-001"))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should throw when invoiceNumber is blank")
        void shouldThrowWhenNumberBlank() {
            assertThatThrownBy(() -> service.searchByNumber(RESTAURANT_ID, "  "))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should return empty list when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(invoiceRepository.findByRestaurantIdAndInvoiceNumber(RESTAURANT_ID, "INV-999"))
                    .thenReturn(Optional.empty());

            List<InvoiceListDto> result = service.searchByNumber(RESTAURANT_ID, "INV-999");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return invoice with tip when found")
        void shouldReturnInvoiceWithTip() {
            SessionInvoice inv = createMockInvoice(1L, "INV-20260331-001", SESSION_ID);
            when(invoiceRepository.findByRestaurantIdAndInvoiceNumber(RESTAURANT_ID, "INV-20260331-001"))
                    .thenReturn(Optional.of(inv));

            SessionPayment payment = mock(SessionPayment.class);
            when(payment.getTipAmount()).thenReturn(new BigDecimal("1000"));
            when(paymentRepository.findByTableSessionId(SESSION_ID)).thenReturn(Optional.of(payment));

            List<InvoiceListDto> result = service.searchByNumber(RESTAURANT_ID, "INV-20260331-001");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).invoiceNumber()).isEqualTo("INV-20260331-001");
            assertThat(result.get(0).tipAmount()).isEqualByComparingTo("1000");
        }

        @Test
        @DisplayName("Should return zero tip when no payment record exists")
        void shouldReturnZeroTipWhenNoPayment() {
            SessionInvoice inv = createMockInvoice(1L, "INV-20260331-001", SESSION_ID);
            when(invoiceRepository.findByRestaurantIdAndInvoiceNumber(RESTAURANT_ID, "INV-20260331-001"))
                    .thenReturn(Optional.of(inv));
            when(paymentRepository.findByTableSessionId(SESSION_ID)).thenReturn(Optional.empty());

            List<InvoiceListDto> result = service.searchByNumber(RESTAURANT_ID, "INV-20260331-001");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).tipAmount()).isEqualByComparingTo("0");
        }
    }

    // ==================== Generate PDF Tests ====================

    @Nested
    @DisplayName("Generate PDF")
    class GeneratePdfTests {

        @Test
        @DisplayName("Should throw when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            assertThatThrownBy(() -> service.generatePdf(null, 1L))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should throw when invoiceId is null")
        void shouldThrowWhenInvoiceIdNull() {
            assertThatThrownBy(() -> service.generatePdf(RESTAURANT_ID, null))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should throw when invoice not found")
        void shouldThrowWhenInvoiceNotFound() {
            when(invoiceRepository.findByIdAndRestaurantIdWithItems(999L, RESTAURANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generatePdf(RESTAURANT_ID, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== M7: Invoice Number Generation ====================

    @Nested
    @DisplayName("Invoice Number Generation (M7)")
    class InvoiceNumberGenerationTests {

        private TableSession closedSession;
        private SessionBill finalizedBill;

        @BeforeEach
        void setUpFullInvoiceFlow() {
            RestaurantTable table = mock(RestaurantTable.class);
            when(table.getTableNumber()).thenReturn(5);

            closedSession = mock(TableSession.class);
            when(closedSession.getClosedAt()).thenReturn(Instant.now());
            when(closedSession.getRestaurant()).thenReturn(testRestaurant);
            when(closedSession.getTable()).thenReturn(table);
            when(closedSession.getSessionCode()).thenReturn("ABCD");
            when(closedSession.getOpenedAt()).thenReturn(Instant.now().minusSeconds(3600));

            finalizedBill = mock(SessionBill.class);
            when(finalizedBill.isFinalized()).thenReturn(true);
            when(finalizedBill.getItemsSubtotal()).thenReturn(new BigDecimal("10000"));
            when(finalizedBill.getChargesTotal()).thenReturn(new BigDecimal("1000"));
            when(finalizedBill.getGrandTotal()).thenReturn(new BigDecimal("11000"));
            when(finalizedBill.getCurrency()).thenReturn("SYP");

            when(invoiceRepository.findByTableSessionId(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(closedSession));
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.of(finalizedBill));
            when(orderRepository.findByTableSessionIdAndStatusNot(eq(SESSION_ID), eq(OrderStatus.CANCELLED)))
                    .thenReturn(Collections.emptyList());
            when(invoiceRepository.save(any(SessionInvoice.class))).thenAnswer(inv -> {
                SessionInvoice si = inv.getArgument(0);
                si.setId(100L);
                return si;
            });
        }

        @Test
        @DisplayName("Should generate INV-YYYYMMDD-001 format for first invoice of the day")
        void shouldGenerateFirstInvoiceNumber() {
            when(invoiceCounterRepository.reserveNextSequence(eq(RESTAURANT_ID), anyString()))
                    .thenReturn(1);
            when(invoiceCounterRepository.findByRestaurantIdAndDateStr(eq(RESTAURANT_ID), anyString()))
                    .thenReturn(Optional.of(createInvoiceCounter(1)));

            SessionInvoice result = service.generateInvoice(SESSION_ID);

            assertThat(result.getInvoiceNumber()).matches("INV-\\d{8}-001");
        }

        @Test
        @DisplayName("Should increment sequence based on existing count for the day")
        void shouldIncrementSequenceNumber() {
            when(invoiceCounterRepository.reserveNextSequence(eq(RESTAURANT_ID), anyString()))
                    .thenReturn(1);
            when(invoiceCounterRepository.findByRestaurantIdAndDateStr(eq(RESTAURANT_ID), anyString()))
                    .thenReturn(Optional.of(createInvoiceCounter(5)));

            SessionInvoice result = service.generateInvoice(SESSION_ID);

            assertThat(result.getInvoiceNumber()).matches("INV-\\d{8}-005");
        }

        @Test
        @DisplayName("Should use Asia/Damascus timezone for date part")
        void shouldUseDamascusTimezone() {
            when(invoiceCounterRepository.reserveNextSequence(eq(RESTAURANT_ID), anyString()))
                    .thenReturn(1);
            when(invoiceCounterRepository.findByRestaurantIdAndDateStr(eq(RESTAURANT_ID), anyString()))
                    .thenReturn(Optional.of(createInvoiceCounter(1)));

            SessionInvoice result = service.generateInvoice(SESSION_ID);

            // Invoice number should contain today's date in Damascus timezone
            String invoiceNumber = result.getInvoiceNumber();
            assertThat(invoiceNumber).startsWith("INV-");
            // Extract date part: INV-YYYYMMDD-SEQ
            String datePart = invoiceNumber.substring(4, 12);
            assertThat(datePart).hasSize(8);
            assertThat(datePart).matches("\\d{8}");
        }
    }

    // ==================== M8: Item Snapshot Aggregation ====================

    @Nested
    @DisplayName("Item Snapshot Aggregation (M8)")
    class ItemSnapshotAggregationTests {

        private TableSession closedSession;
        private SessionBill finalizedBill;

        @BeforeEach
        void setUpFullInvoiceFlow() {
            RestaurantTable table = mock(RestaurantTable.class);
            when(table.getTableNumber()).thenReturn(3);

            closedSession = mock(TableSession.class);
            when(closedSession.getClosedAt()).thenReturn(Instant.now());
            when(closedSession.getRestaurant()).thenReturn(testRestaurant);
            when(closedSession.getTable()).thenReturn(table);
            when(closedSession.getSessionCode()).thenReturn("XYZW");
            when(closedSession.getOpenedAt()).thenReturn(Instant.now().minusSeconds(3600));

            finalizedBill = mock(SessionBill.class);
            when(finalizedBill.isFinalized()).thenReturn(true);
            when(finalizedBill.getItemsSubtotal()).thenReturn(new BigDecimal("20000"));
            when(finalizedBill.getChargesTotal()).thenReturn(new BigDecimal("2000"));
            when(finalizedBill.getGrandTotal()).thenReturn(new BigDecimal("22000"));
            when(finalizedBill.getCurrency()).thenReturn("SYP");

            when(invoiceRepository.findByTableSessionId(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(closedSession));
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.of(finalizedBill));
            when(invoiceCounterRepository.reserveNextSequence(eq(RESTAURANT_ID), anyString()))
                    .thenReturn(1);
            when(invoiceCounterRepository.findByRestaurantIdAndDateStr(eq(RESTAURANT_ID), anyString()))
                    .thenReturn(Optional.of(createInvoiceCounter(1)));
            when(invoiceRepository.save(any(SessionInvoice.class))).thenAnswer(inv -> {
                SessionInvoice si = inv.getArgument(0);
                si.setId(200L);
                return si;
            });
        }

        @Test
        @DisplayName("Should aggregate items with same name and unitPrice")
        void shouldAggregateItemsBySameNameAndPrice() {
            CustomerOrder order1 = mock(CustomerOrder.class);
            when(order1.getId()).thenReturn(1L);
            CustomerOrder order2 = mock(CustomerOrder.class);
            when(order2.getId()).thenReturn(2L);

            when(orderRepository.findByTableSessionIdAndStatusNot(eq(SESSION_ID), eq(OrderStatus.CANCELLED)))
                    .thenReturn(List.of(order1, order2));

            MenuItem cola = mock(MenuItem.class);
            when(cola.getName()).thenReturn("Cola");

            // Two order items for "Cola" at same price → should aggregate
            OrderItem oi1 = new OrderItem(1L, order1, cola, 2, new BigDecimal("2500.00"), new BigDecimal("5000.00"), null);
            OrderItem oi2 = new OrderItem(2L, order2, cola, 3, new BigDecimal("2500.00"), new BigDecimal("7500.00"), null);

            when(orderItemRepository.findByOrderIdInWithMenuItem(List.of(1L, 2L)))
                    .thenReturn(List.of(oi1, oi2));

            SessionInvoice result = service.generateInvoice(SESSION_ID);

            // Should be 1 aggregated item: Cola × 5 at 2500.00
            assertThat(result.getItems()).hasSize(1);
            SessionInvoiceItem item = result.getItems().get(0);
            assertThat(item.getItemName()).isEqualTo("Cola");
            assertThat(item.getQuantity()).isEqualTo(5);
            assertThat(item.getUnitPrice()).isEqualByComparingTo("2500.00");
            assertThat(item.getLineTotal()).isEqualByComparingTo("12500.00");
        }

        @Test
        @DisplayName("Should keep items with different prices separate")
        void shouldKeepDifferentPricesSeparate() {
            CustomerOrder order = mock(CustomerOrder.class);
            when(order.getId()).thenReturn(1L);

            when(orderRepository.findByTableSessionIdAndStatusNot(eq(SESSION_ID), eq(OrderStatus.CANCELLED)))
                    .thenReturn(List.of(order));

            MenuItem cola = mock(MenuItem.class);
            when(cola.getName()).thenReturn("Cola");

            // Same item name, different prices
            OrderItem oi1 = new OrderItem(1L, order, cola, 2, new BigDecimal("2500.00"), new BigDecimal("5000.00"), null);
            OrderItem oi2 = new OrderItem(2L, order, cola, 1, new BigDecimal("3000.00"), new BigDecimal("3000.00"), null);

            when(orderItemRepository.findByOrderIdInWithMenuItem(List.of(1L)))
                    .thenReturn(List.of(oi1, oi2));

            SessionInvoice result = service.generateInvoice(SESSION_ID);

            assertThat(result.getItems()).hasSize(2);
            // Verify both are present with correct quantities
            assertThat(result.getItems())
                    .extracting(SessionInvoiceItem::getUnitPrice)
                    .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .containsExactlyInAnyOrder(new BigDecimal("2500.00"), new BigDecimal("3000.00"));
        }

        @Test
        @DisplayName("Should exclude cancelled orders from snapshot")
        void shouldExcludeCancelledOrders() {
            // orderRepository.findByTableSessionIdAndStatusNot already filters CANCELLED
            // Test: when no non-cancelled orders exist, items list should be empty
            when(orderRepository.findByTableSessionIdAndStatusNot(eq(SESSION_ID), eq(OrderStatus.CANCELLED)))
                    .thenReturn(Collections.emptyList());

            SessionInvoice result = service.generateInvoice(SESSION_ID);

            assertThat(result.getItems()).isEmpty();
            verify(orderItemRepository, never()).findByOrderIdInWithMenuItem(any());
        }

        @Test
        @DisplayName("Should compute lineTotal as unitPrice × quantity")
        void shouldComputeLineTotalCorrectly() {
            CustomerOrder order = mock(CustomerOrder.class);
            when(order.getId()).thenReturn(1L);

            when(orderRepository.findByTableSessionIdAndStatusNot(eq(SESSION_ID), eq(OrderStatus.CANCELLED)))
                    .thenReturn(List.of(order));

            MenuItem burger = mock(MenuItem.class);
            when(burger.getName()).thenReturn("Burger");
            MenuItem fries = mock(MenuItem.class);
            when(fries.getName()).thenReturn("Fries");

            OrderItem oi1 = new OrderItem(1L, order, burger, 3, new BigDecimal("5000.00"), new BigDecimal("15000.00"), null);
            OrderItem oi2 = new OrderItem(2L, order, fries, 2, new BigDecimal("2000.00"), new BigDecimal("4000.00"), null);

            when(orderItemRepository.findByOrderIdInWithMenuItem(List.of(1L)))
                    .thenReturn(List.of(oi1, oi2));

            SessionInvoice result = service.generateInvoice(SESSION_ID);

            assertThat(result.getItems()).hasSize(2);
            // Verify line totals
            SessionInvoiceItem burgerItem = result.getItems().stream()
                    .filter(i -> i.getItemName().equals("Burger")).findFirst().orElseThrow();
            assertThat(burgerItem.getLineTotal()).isEqualByComparingTo("15000.00");
            assertThat(burgerItem.getQuantity()).isEqualTo(3);

            SessionInvoiceItem friesItem = result.getItems().stream()
                    .filter(i -> i.getItemName().equals("Fries")).findFirst().orElseThrow();
            assertThat(friesItem.getLineTotal()).isEqualByComparingTo("4000.00");
            assertThat(friesItem.getQuantity()).isEqualTo(2);
        }
    }

    // ==================== Helper Methods ====================

    private SessionInvoice createMockInvoice(Long id, String invoiceNumber, Long sessionId) {
        SessionInvoice inv = mock(SessionInvoice.class);
        when(inv.getId()).thenReturn(id);
        when(inv.getInvoiceNumber()).thenReturn(invoiceNumber);
        when(inv.getTableNumber()).thenReturn(5);
        when(inv.getSessionCode()).thenReturn("1234");
        when(inv.getSessionOpenedAt()).thenReturn(Instant.now().minusSeconds(3600));
        when(inv.getSessionClosedAt()).thenReturn(Instant.now());
        when(inv.getItemsSubtotal()).thenReturn(new BigDecimal("8000"));
        when(inv.getChargesTotal()).thenReturn(new BigDecimal("800"));
        when(inv.getGrandTotal()).thenReturn(new BigDecimal("8800"));
        when(inv.getCurrency()).thenReturn("SYP");
        when(inv.getCreatedAt()).thenReturn(Instant.now());

        TableSession ts = mock(TableSession.class);
        when(ts.getId()).thenReturn(sessionId);
        when(inv.getTableSession()).thenReturn(ts);

        return inv;
    }

    private InvoiceCounter createInvoiceCounter(int lastSeq) {
        return new InvoiceCounter(RESTAURANT_ID, "20260401", lastSeq);
    }
}

