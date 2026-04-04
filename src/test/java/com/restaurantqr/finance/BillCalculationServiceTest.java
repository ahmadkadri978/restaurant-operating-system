package com.restaurantqr.finance;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.BillChargeDto;
import com.restaurantqr.finance.dto.SessionBillDto;
import com.restaurantqr.order.CustomerOrder;
import com.restaurantqr.order.CustomerOrderRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BillCalculationServiceImpl — core billing engine.
 * Covers: bill computation, finalization, compound charges, rounding,
 * edge cases (zero orders, empty rules, idempotent finalization), and DTO mapping.
 *
 * Tests the exact calculation pipeline from RESTAURANT_FINANCIAL_MODEL_PLAN.md Section 7.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BillCalculationService Unit Tests")
class BillCalculationServiceTest {

    @Mock
    private TableSessionRepository sessionRepository;

    @Mock
    private CustomerOrderRepository orderRepository;

    @Mock
    private com.restaurantqr.order.OrderItemRepository orderItemRepository;

    @Mock
    private RestaurantFinancialRuleRepository ruleRepository;

    @Mock
    private SessionBillRepository billRepository;

    @Captor
    private ArgumentCaptor<SessionBill> billCaptor;

    private BillCalculationServiceImpl billService;

    private Restaurant testRestaurant;
    private RestaurantTable testTable;
    private TableSession testSession;
    private static final Long RESTAURANT_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @BeforeEach
    void setUp() {
        billService = new BillCalculationServiceImpl(
                sessionRepository, orderRepository, orderItemRepository, ruleRepository, billRepository
        );

        testRestaurant = new Restaurant();
        testRestaurant.setId(RESTAURANT_ID);
        testRestaurant.setName("مطعم اختباري");

        testTable = new RestaurantTable();
        testTable.setId(5L);
        testTable.setTableNumber(5);
        testTable.setRestaurant(testRestaurant);
        testTable.setActive(true);

        testSession = new TableSession(testRestaurant, testTable, "1234", Instant.now());
        testSession.setId(SESSION_ID);
    }

    // ==================== Helper Methods ====================

    private RestaurantFinancialRule makeRule(Long id, String nameAr, String nameEn,
                                             RuleType type, String value,
                                             int order, boolean compound) {
        RestaurantFinancialRule rule = new RestaurantFinancialRule(
                testRestaurant, nameAr, nameEn, type,
                new BigDecimal(value), order, compound
        );
        rule.setId(id);
        return rule;
    }

    private CustomerOrder makeOrder(Long id, BigDecimal totalAmount, OrderStatus status) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setRestaurant(testRestaurant);
        order.setTable(testTable);
        order.setTotalAmount(totalAmount);
        order.setStatus(status);
        order.setCreatedAt(Instant.now());
        order.setTableSession(testSession);
        return order;
    }

    private void setupDefaultOrders(BigDecimal... amounts) {
        List<CustomerOrder> orders = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            orders.add(makeOrder((long) (i + 1), amounts[i], OrderStatus.DONE));
        }
        when(orderRepository.findByTableSessionIdAndStatusNot(SESSION_ID, OrderStatus.CANCELLED))
                .thenReturn(orders);
    }

    private void setupDefaultRules(RestaurantFinancialRule... rules) {
        when(ruleRepository.findByRestaurantIdAndIsActiveTrueOrderByApplyOrderAsc(RESTAURANT_ID))
                .thenReturn(List.of(rules));
    }

    // ==================== getOrComputeBill() Tests ====================

    @Nested
    @DisplayName("getOrComputeBill() — Live DRAFT computation")
    class GetOrComputeBillTests {

        @Test
        @DisplayName("Should compute DRAFT bill with single PERCENTAGE rule")
        void shouldComputeDraftBillWithPercentageRule() {
            // Given
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("10000.00"));

            RestaurantFinancialRule vatRule = makeRule(1L, "ضريبة", "VAT",
                    RuleType.PERCENTAGE, "10.0000", 1, false);
            setupDefaultRules(vatRule);

            // When
            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            // Then
            assertThat(bill).isNotNull();
            assertThat(bill.status()).isEqualTo(BillStatus.DRAFT);
            assertThat(bill.billId()).isNull(); // DRAFT bills have no persisted ID
            assertThat(bill.itemsSubtotal()).isEqualByComparingTo("10000.00");
            assertThat(bill.chargesTotal()).isEqualByComparingTo("1000.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("11000.00");
            assertThat(bill.currency()).isEqualTo("SYP");
            assertThat(bill.charges()).hasSize(1);

            BillChargeDto charge = bill.charges().get(0);
            assertThat(charge.nameAr()).isEqualTo("ضريبة");
            assertThat(charge.nameEn()).isEqualTo("VAT");
            assertThat(charge.ruleType()).isEqualTo(RuleType.PERCENTAGE);
            assertThat(charge.ruleValue()).isEqualByComparingTo("10.0000");
            assertThat(charge.computedAmount()).isEqualByComparingTo("1000.00");
            assertThat(charge.isCompound()).isFalse();
        }

        @Test
        @DisplayName("Should compute DRAFT bill with FIXED rule")
        void shouldComputeDraftBillWithFixedRule() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("5000.00"));

            RestaurantFinancialRule feeRule = makeRule(1L, "رسم طاقة", "Energy Fee",
                    RuleType.FIXED, "500.0000", 1, false);
            setupDefaultRules(feeRule);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.itemsSubtotal()).isEqualByComparingTo("5000.00");
            assertThat(bill.chargesTotal()).isEqualByComparingTo("500.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("5500.00");
            assertThat(bill.charges()).hasSize(1);
            assertThat(bill.charges().get(0).ruleType()).isEqualTo(RuleType.FIXED);
            assertThat(bill.charges().get(0).computedAmount()).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("Should compute bill with no financial rules (subtotal = grand total)")
        void shouldComputeBillWithNoRules() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("7500.00"));
            setupDefaultRules(); // empty rules

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.itemsSubtotal()).isEqualByComparingTo("7500.00");
            assertThat(bill.chargesTotal()).isEqualByComparingTo("0.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("7500.00");
            assertThat(bill.charges()).isEmpty();
        }

        @Test
        @DisplayName("Should compute bill with zero orders (subtotal = 0)")
        void shouldComputeBillWithZeroOrders() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            when(orderRepository.findByTableSessionIdAndStatusNot(SESSION_ID, OrderStatus.CANCELLED))
                    .thenReturn(Collections.emptyList());
            // Fallback: also no timestamp-based results
            when(orderRepository.findByTableIdAndCreatedAtBetweenAndStatusNot(
                    eq(testTable.getId()), any(), any(), eq(OrderStatus.CANCELLED)))
                    .thenReturn(Collections.emptyList());

            RestaurantFinancialRule feeRule = makeRule(1L, "رسم طاقة", "Energy Fee",
                    RuleType.FIXED, "500.0000", 1, false);
            setupDefaultRules(feeRule);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.itemsSubtotal()).isEqualByComparingTo("0.00");
            // Fixed fee still applies even with 0 items
            assertThat(bill.chargesTotal()).isEqualByComparingTo("500.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("Should return finalized bill if one already exists")
        void shouldReturnFinalizedBillIfExists() {
            SessionBill finalized = new SessionBill(testRestaurant, testSession);
            finalized.setId(100L);
            finalized.setItemsSubtotal(new BigDecimal("10000.00"));
            finalized.setChargesTotal(new BigDecimal("2000.00"));
            finalized.setGrandTotal(new BigDecimal("12000.00"));
            finalized.setStatus(BillStatus.FINALIZED);
            finalized.setFinalizedAt(Instant.now());
            finalized.setCharges(new ArrayList<>());

            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID))
                    .thenReturn(Optional.of(finalized));

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.status()).isEqualTo(BillStatus.FINALIZED);
            assertThat(bill.billId()).isEqualTo(100L);
            assertThat(bill.grandTotal()).isEqualByComparingTo("12000.00");

            // Should NOT recompute — no session/order lookups
            verify(sessionRepository, never()).findById(any());
            verify(orderRepository, never()).findByTableSessionIdAndStatusNot(any(), any());
        }

        @Test
        @DisplayName("Should set table number and session code on bill DTO")
        void shouldSetTableNumberAndSessionCode() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("1000.00"));
            setupDefaultRules();

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.tableNumber()).isEqualTo(5);
            assertThat(bill.sessionCode()).isEqualTo("1234");
        }

        @Test
        @DisplayName("Should throw when tableSessionId is null")
        void shouldThrowWhenSessionIdNull() {
            assertThatThrownBy(() -> billService.getOrComputeBill(null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("tableSessionId is required");
        }

        @Test
        @DisplayName("Should throw when session not found")
        void shouldThrowWhenSessionNotFound() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> billService.getOrComputeBill(SESSION_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Session not found");
        }

        @Test
        @DisplayName("Should aggregate multiple order totals")
        void shouldAggregateMultipleOrderTotals() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(
                    new BigDecimal("3000.00"),
                    new BigDecimal("2500.00"),
                    new BigDecimal("4500.00")
            );
            setupDefaultRules(); // no charges for simplicity

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.itemsSubtotal()).isEqualByComparingTo("10000.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("10000.00");
        }
    }

    // ==================== Compound Charges Tests ====================

    @Nested
    @DisplayName("Compound Charge Calculation")
    class CompoundChargeTests {

        @Test
        @DisplayName("Should apply compound percentage on subtotal + prior charges (plan Section 7.3 example)")
        void shouldApplyCompoundPercentageCorrectly() {
            // This is the exact example from the plan:
            // Service Charge: 10% (non-compound, order 1)
            // Energy Fee: 500 FIXED (order 2)
            // VAT: 5% compound (order 3)
            // Items subtotal: 10,000 SYP

            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("10000.00"));

            RestaurantFinancialRule serviceCharge = makeRule(1L, "خدمة", "Service Charge",
                    RuleType.PERCENTAGE, "10.0000", 1, false);
            RestaurantFinancialRule energyFee = makeRule(2L, "رسم طاقة", "Energy Fee",
                    RuleType.FIXED, "500.0000", 2, false);
            RestaurantFinancialRule vat = makeRule(3L, "ضريبة", "VAT",
                    RuleType.PERCENTAGE, "5.0000", 3, true); // COMPOUND

            setupDefaultRules(serviceCharge, energyFee, vat);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            // Expected:
            // Service Charge: 10,000 × 10% = 1,000
            // Energy Fee: 500
            // VAT (compound): (10,000 + 1,000 + 500) × 5% = 575
            // Total charges: 1,000 + 500 + 575 = 2,075
            // Grand total: 10,000 + 2,075 = 12,075
            assertThat(bill.itemsSubtotal()).isEqualByComparingTo("10000.00");
            assertThat(bill.charges()).hasSize(3);
            assertThat(bill.charges().get(0).computedAmount()).isEqualByComparingTo("1000.00");
            assertThat(bill.charges().get(1).computedAmount()).isEqualByComparingTo("500.00");
            assertThat(bill.charges().get(2).computedAmount()).isEqualByComparingTo("575.00");
            assertThat(bill.chargesTotal()).isEqualByComparingTo("2075.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("12075.00");
        }

        @Test
        @DisplayName("Non-compound percentage should only apply on items subtotal, not prior charges")
        void nonCompoundShouldOnlyApplyOnSubtotal() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("10000.00"));

            // Both percentage, both non-compound, so both apply on 10,000
            RestaurantFinancialRule rule1 = makeRule(1L, "خدمة", "Service",
                    RuleType.PERCENTAGE, "10.0000", 1, false);
            RestaurantFinancialRule rule2 = makeRule(2L, "ضريبة", "Tax",
                    RuleType.PERCENTAGE, "5.0000", 2, false);
            setupDefaultRules(rule1, rule2);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            // Rule 1: 10,000 × 10% = 1,000
            // Rule 2: 10,000 × 5% = 500 (NOT compound, so still on 10,000)
            assertThat(bill.charges().get(0).computedAmount()).isEqualByComparingTo("1000.00");
            assertThat(bill.charges().get(1).computedAmount()).isEqualByComparingTo("500.00");
            assertThat(bill.chargesTotal()).isEqualByComparingTo("1500.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("11500.00");
        }

        @Test
        @DisplayName("Multiple compound rules should cascade correctly")
        void multipleCompoundRulesShouldCascade() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("10000.00"));

            // Both compound
            RestaurantFinancialRule rule1 = makeRule(1L, "رسم1", "Fee1",
                    RuleType.PERCENTAGE, "10.0000", 1, true);
            RestaurantFinancialRule rule2 = makeRule(2L, "رسم2", "Fee2",
                    RuleType.PERCENTAGE, "10.0000", 2, true);
            setupDefaultRules(rule1, rule2);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            // Rule 1 (compound): (10,000 + 0) × 10% = 1,000
            // Rule 2 (compound): (10,000 + 1,000) × 10% = 1,100
            assertThat(bill.charges().get(0).computedAmount()).isEqualByComparingTo("1000.00");
            assertThat(bill.charges().get(1).computedAmount()).isEqualByComparingTo("1100.00");
            assertThat(bill.chargesTotal()).isEqualByComparingTo("2100.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("12100.00");
        }

        @Test
        @DisplayName("Compound PERCENTAGE on zero subtotal should produce zero charge")
        void compoundOnZeroSubtotalShouldBeZero() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            when(orderRepository.findByTableSessionIdAndStatusNot(SESSION_ID, OrderStatus.CANCELLED))
                    .thenReturn(Collections.emptyList());
            when(orderRepository.findByTableIdAndCreatedAtBetweenAndStatusNot(
                    eq(testTable.getId()), any(), any(), eq(OrderStatus.CANCELLED)))
                    .thenReturn(Collections.emptyList());

            RestaurantFinancialRule compoundVat = makeRule(1L, "ضريبة مركبة", "Compound VAT",
                    RuleType.PERCENTAGE, "10.0000", 1, true);
            setupDefaultRules(compoundVat);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.itemsSubtotal()).isEqualByComparingTo("0.00");
            assertThat(bill.charges().get(0).computedAmount()).isEqualByComparingTo("0.00");
            assertThat(bill.chargesTotal()).isEqualByComparingTo("0.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("0.00");
        }
    }

    // ==================== Rounding Tests ====================

    @Nested
    @DisplayName("Rounding Behavior (HALF_UP, 2 decimal places)")
    class RoundingTests {

        @Test
        @DisplayName("Should round each charge to 2 decimal places with HALF_UP")
        void shouldRoundChargeToTwoDecimals() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            // 3333.33 × 10% = 333.333 → rounds to 333.33
            setupDefaultOrders(new BigDecimal("3333.33"));

            RestaurantFinancialRule vat = makeRule(1L, "ضريبة", "VAT",
                    RuleType.PERCENTAGE, "10.0000", 1, false);
            setupDefaultRules(vat);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.charges().get(0).computedAmount()).isEqualByComparingTo("333.33");
            assertThat(bill.grandTotal()).isEqualByComparingTo("3666.66");
        }

        @Test
        @DisplayName("Should round up at 0.5 boundary (HALF_UP)")
        void shouldRoundUpAtHalfBoundary() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            // 1234.55 × 7% = 86.4185 → rounds to 86.42 (HALF_UP)
            setupDefaultOrders(new BigDecimal("1234.55"));

            RestaurantFinancialRule rule = makeRule(1L, "رسم", "Fee",
                    RuleType.PERCENTAGE, "7.0000", 1, false);
            setupDefaultRules(rule);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            BigDecimal expected = new BigDecimal("1234.55")
                    .multiply(new BigDecimal("7.0000"))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            assertThat(bill.charges().get(0).computedAmount()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("Should handle precision with very small subtotal")
        void shouldHandleVerySmallSubtotal() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("0.01"));

            RestaurantFinancialRule rule = makeRule(1L, "ضريبة", "Tax",
                    RuleType.PERCENTAGE, "10.0000", 1, false);
            setupDefaultRules(rule);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            // 0.01 × 10% = 0.001 → rounds to 0.00 (HALF_UP: 0.001 rounds down)
            assertThat(bill.charges().get(0).computedAmount()).isEqualByComparingTo("0.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("0.01");
        }

        @Test
        @DisplayName("Should handle large subtotal correctly")
        void shouldHandleLargeSubtotal() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("9999999.99"));

            RestaurantFinancialRule rule = makeRule(1L, "ضريبة", "Tax",
                    RuleType.PERCENTAGE, "15.0000", 1, false);
            setupDefaultRules(rule);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            BigDecimal expectedCharge = new BigDecimal("9999999.99")
                    .multiply(new BigDecimal("15"))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            assertThat(bill.charges().get(0).computedAmount()).isEqualByComparingTo(expectedCharge);
        }

        @Test
        @DisplayName("Fixed rule value should be rounded to 2 decimal places")
        void fixedRuleValueShouldBeRoundedTo2Decimals() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("1000.00"));

            RestaurantFinancialRule rule = makeRule(1L, "رسم", "Fee",
                    RuleType.FIXED, "123.4567", 1, false);
            setupDefaultRules(rule);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            // Fixed: 123.4567 → setScale(2, HALF_UP) → 123.46
            assertThat(bill.charges().get(0).computedAmount()).isEqualByComparingTo("123.46");
        }
    }

    // ==================== finalizeBill() Tests ====================

    @Nested
    @DisplayName("finalizeBill() — Bill finalization on session close")
    class FinalizeBillTests {

        @Test
        @DisplayName("Should finalize bill with correct totals and persisted charge snapshots")
        void shouldFinalizeBillCorrectly() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("10000.00"));

            RestaurantFinancialRule vatRule = makeRule(1L, "ضريبة", "VAT",
                    RuleType.PERCENTAGE, "10.0000", 1, false);
            RestaurantFinancialRule feeRule = makeRule(2L, "رسم", "Fee",
                    RuleType.FIXED, "500.0000", 2, false);
            setupDefaultRules(vatRule, feeRule);

            when(billRepository.save(any(SessionBill.class))).thenAnswer(inv -> {
                SessionBill b = inv.getArgument(0);
                b.setId(100L);
                return b;
            });

            // When
            SessionBill result = billService.finalizeBill(SESSION_ID);

            // Then
            verify(billRepository).save(billCaptor.capture());
            SessionBill captured = billCaptor.getValue();

            assertThat(captured.getStatus()).isEqualTo(BillStatus.FINALIZED);
            assertThat(captured.getFinalizedAt()).isNotNull();
            assertThat(captured.getItemsSubtotal()).isEqualByComparingTo("10000.00");
            assertThat(captured.getChargesTotal()).isEqualByComparingTo("1500.00");
            assertThat(captured.getGrandTotal()).isEqualByComparingTo("11500.00");

            // Verify charge snapshots
            assertThat(captured.getCharges()).hasSize(2);

            SessionBillCharge charge1 = captured.getCharges().get(0);
            assertThat(charge1.getRuleNameAr()).isEqualTo("ضريبة");
            assertThat(charge1.getRuleNameEn()).isEqualTo("VAT");
            assertThat(charge1.getRuleType()).isEqualTo(RuleType.PERCENTAGE);
            assertThat(charge1.getRuleValue()).isEqualByComparingTo("10.0000");
            assertThat(charge1.isCompound()).isFalse();
            assertThat(charge1.getComputedAmount()).isEqualByComparingTo("1000.00");
            assertThat(charge1.getSourceRuleId()).isEqualTo(1L);

            SessionBillCharge charge2 = captured.getCharges().get(1);
            assertThat(charge2.getRuleNameAr()).isEqualTo("رسم");
            assertThat(charge2.getRuleType()).isEqualTo(RuleType.FIXED);
            assertThat(charge2.getComputedAmount()).isEqualByComparingTo("500.00");
            assertThat(charge2.getSourceRuleId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Should be idempotent — return existing finalized bill without recomputing")
        void shouldBeIdempotent() {
            SessionBill existing = new SessionBill(testRestaurant, testSession);
            existing.setId(100L);
            existing.setStatus(BillStatus.FINALIZED);
            existing.setFinalizedAt(Instant.now());
            existing.setItemsSubtotal(new BigDecimal("5000.00"));
            existing.setChargesTotal(new BigDecimal("500.00"));
            existing.setGrandTotal(new BigDecimal("5500.00"));
            existing.setCharges(new ArrayList<>());

            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID))
                    .thenReturn(Optional.of(existing));

            SessionBill result = billService.finalizeBill(SESSION_ID);

            assertThat(result.getId()).isEqualTo(100L);
            assertThat(result.isFinalized()).isTrue();
            // Should NOT recalculate or save again
            verify(billRepository, never()).save(any());
            verify(sessionRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should update existing DRAFT bill to FINALIZED")
        void shouldUpdateDraftToFinalized() {
            SessionBill draftBill = new SessionBill(testRestaurant, testSession);
            draftBill.setId(50L);
            draftBill.setStatus(BillStatus.DRAFT);
            draftBill.setCharges(new ArrayList<>());

            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID))
                    .thenReturn(Optional.of(draftBill));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("8000.00"));
            setupDefaultRules(); // no rules

            when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SessionBill result = billService.finalizeBill(SESSION_ID);

            verify(billRepository).save(billCaptor.capture());
            SessionBill saved = billCaptor.getValue();

            assertThat(saved.getId()).isEqualTo(50L); // same bill entity updated
            assertThat(saved.getStatus()).isEqualTo(BillStatus.FINALIZED);
            assertThat(saved.getItemsSubtotal()).isEqualByComparingTo("8000.00");
            assertThat(saved.getChargesTotal()).isEqualByComparingTo("0.00");
            assertThat(saved.getGrandTotal()).isEqualByComparingTo("8000.00");
        }

        @Test
        @DisplayName("Should finalize bill with zero orders (empty session)")
        void shouldFinalizeBillWithZeroOrders() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            when(orderRepository.findByTableSessionIdAndStatusNot(SESSION_ID, OrderStatus.CANCELLED))
                    .thenReturn(Collections.emptyList());
            when(orderRepository.findByTableIdAndCreatedAtBetweenAndStatusNot(
                    eq(testTable.getId()), any(), any(), eq(OrderStatus.CANCELLED)))
                    .thenReturn(Collections.emptyList());

            RestaurantFinancialRule energyFee = makeRule(1L, "رسم طاقة", "Energy Fee",
                    RuleType.FIXED, "500.0000", 1, false);
            setupDefaultRules(energyFee);

            when(billRepository.save(any())).thenAnswer(inv -> {
                SessionBill b = inv.getArgument(0);
                b.setId(1L);
                return b;
            });

            SessionBill result = billService.finalizeBill(SESSION_ID);

            verify(billRepository).save(billCaptor.capture());
            SessionBill saved = billCaptor.getValue();

            assertThat(saved.getItemsSubtotal()).isEqualByComparingTo("0.00");
            // Fixed charge still applies
            assertThat(saved.getChargesTotal()).isEqualByComparingTo("500.00");
            assertThat(saved.getGrandTotal()).isEqualByComparingTo("500.00");
            assertThat(saved.getCharges()).hasSize(1);
        }

        @Test
        @DisplayName("Should throw when tableSessionId is null")
        void shouldThrowWhenSessionIdNull() {
            assertThatThrownBy(() -> billService.finalizeBill(null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("tableSessionId is required");
        }

        @Test
        @DisplayName("Should throw when session not found")
        void shouldThrowWhenSessionNotFound() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> billService.finalizeBill(SESSION_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Session not found");
        }

        @Test
        @DisplayName("Finalized bill should snapshot compound charge details")
        void shouldSnapshotCompoundChargeDetails() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("10000.00"));

            RestaurantFinancialRule compoundRule = makeRule(1L, "ضريبة مركبة", "Compound Tax",
                    RuleType.PERCENTAGE, "5.0000", 1, true);
            setupDefaultRules(compoundRule);

            when(billRepository.save(any())).thenAnswer(inv -> {
                SessionBill b = inv.getArgument(0);
                b.setId(1L);
                return b;
            });

            billService.finalizeBill(SESSION_ID);

            verify(billRepository).save(billCaptor.capture());
            SessionBillCharge charge = billCaptor.getValue().getCharges().get(0);

            assertThat(charge.isCompound()).isTrue();
            assertThat(charge.getApplyOrder()).isEqualTo(1);
            assertThat(charge.getRuleValue()).isEqualByComparingTo("5.0000");
        }
    }

    // ==================== Mixed Rules Order Tests ====================

    @Nested
    @DisplayName("Rule Ordering and Mixed Rule Types")
    class RuleOrderingTests {

        @Test
        @DisplayName("Rules should be applied in applyOrder sequence")
        void rulesShouldBeAppliedInOrder() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("20000.00"));

            // Apply in reverse logical order to verify sorting matters
            RestaurantFinancialRule first = makeRule(1L, "أول", "First",
                    RuleType.PERCENTAGE, "10.0000", 1, false);
            RestaurantFinancialRule second = makeRule(2L, "ثاني", "Second",
                    RuleType.FIXED, "1000.0000", 2, false);
            RestaurantFinancialRule third = makeRule(3L, "ثالث", "Third",
                    RuleType.PERCENTAGE, "5.0000", 3, true); // compound

            setupDefaultRules(first, second, third);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.charges()).hasSize(3);
            // First: 20,000 × 10% = 2,000
            assertThat(bill.charges().get(0).computedAmount()).isEqualByComparingTo("2000.00");
            assertThat(bill.charges().get(0).nameEn()).isEqualTo("First");

            // Second: fixed 1,000
            assertThat(bill.charges().get(1).computedAmount()).isEqualByComparingTo("1000.00");
            assertThat(bill.charges().get(1).nameEn()).isEqualTo("Second");

            // Third (compound): (20,000 + 2,000 + 1,000) × 5% = 1,150
            assertThat(bill.charges().get(2).computedAmount()).isEqualByComparingTo("1150.00");
            assertThat(bill.charges().get(2).nameEn()).isEqualTo("Third");

            assertThat(bill.chargesTotal()).isEqualByComparingTo("4150.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("24150.00");
        }

        @Test
        @DisplayName("Single FIXED rule only")
        void singleFixedRuleOnly() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("5000.00"));

            RestaurantFinancialRule fixed = makeRule(1L, "رسم", "Fee",
                    RuleType.FIXED, "250.0000", 1, false);
            setupDefaultRules(fixed);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.chargesTotal()).isEqualByComparingTo("250.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("5250.00");
        }

        @Test
        @DisplayName("Multiple FIXED rules should all apply")
        void multipleFixedRulesShouldAllApply() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("3000.00"));

            RestaurantFinancialRule fee1 = makeRule(1L, "رسم1", "Fee1",
                    RuleType.FIXED, "100.0000", 1, false);
            RestaurantFinancialRule fee2 = makeRule(2L, "رسم2", "Fee2",
                    RuleType.FIXED, "200.0000", 2, false);
            RestaurantFinancialRule fee3 = makeRule(3L, "رسم3", "Fee3",
                    RuleType.FIXED, "300.0000", 3, false);
            setupDefaultRules(fee1, fee2, fee3);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.chargesTotal()).isEqualByComparingTo("600.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("3600.00");
        }
    }

    // ==================== DTO Mapping Tests ====================

    @Nested
    @DisplayName("DTO Mapping for Finalized Bills")
    class DtoMappingTests {

        @Test
        @DisplayName("Should map finalized bill entity to DTO with all charge details")
        void shouldMapFinalizedBillToDto() {
            Instant finalizedTime = Instant.parse("2026-03-29T12:00:00Z");

            SessionBill bill = new SessionBill(testRestaurant, testSession);
            bill.setId(42L);
            bill.setItemsSubtotal(new BigDecimal("8000.00"));
            bill.setChargesTotal(new BigDecimal("1200.00"));
            bill.setGrandTotal(new BigDecimal("9200.00"));
            bill.setCurrency("SYP");
            bill.setStatus(BillStatus.FINALIZED);
            bill.setFinalizedAt(finalizedTime);

            SessionBillCharge charge = new SessionBillCharge();
            charge.setId(1L);
            charge.setSessionBill(bill);
            charge.setRuleNameAr("خدمة");
            charge.setRuleNameEn("Service");
            charge.setRuleType(RuleType.PERCENTAGE);
            charge.setRuleValue(new BigDecimal("15.0000"));
            charge.setCompound(false);
            charge.setApplyOrder(1);
            charge.setComputedAmount(new BigDecimal("1200.00"));
            charge.setSourceRuleId(5L);

            bill.setCharges(List.of(charge));

            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID))
                    .thenReturn(Optional.of(bill));

            SessionBillDto dto = billService.getOrComputeBill(SESSION_ID);

            assertThat(dto.billId()).isEqualTo(42L);
            assertThat(dto.tableNumber()).isEqualTo(5);
            assertThat(dto.sessionCode()).isEqualTo("1234");
            assertThat(dto.itemsSubtotal()).isEqualByComparingTo("8000.00");
            assertThat(dto.chargesTotal()).isEqualByComparingTo("1200.00");
            assertThat(dto.grandTotal()).isEqualByComparingTo("9200.00");
            assertThat(dto.currency()).isEqualTo("SYP");
            assertThat(dto.status()).isEqualTo(BillStatus.FINALIZED);
            assertThat(dto.finalizedAt()).isEqualTo(finalizedTime);

            assertThat(dto.charges()).hasSize(1);
            BillChargeDto chargeDto = dto.charges().get(0);
            assertThat(chargeDto.nameAr()).isEqualTo("خدمة");
            assertThat(chargeDto.nameEn()).isEqualTo("Service");
            assertThat(chargeDto.ruleType()).isEqualTo(RuleType.PERCENTAGE);
            assertThat(chargeDto.ruleValue()).isEqualByComparingTo("15.0000");
            assertThat(chargeDto.isCompound()).isFalse();
            assertThat(chargeDto.computedAmount()).isEqualByComparingTo("1200.00");
        }

        @Test
        @DisplayName("Should handle null table and session code gracefully in DTO")
        void shouldHandleNullTableAndSessionCode() {
            TableSession orphanSession = new TableSession();
            orphanSession.setId(SESSION_ID);
            orphanSession.setRestaurant(testRestaurant);
            // table is null, sessionCode is null

            SessionBill bill = new SessionBill(testRestaurant, orphanSession);
            bill.setId(1L);
            bill.setStatus(BillStatus.FINALIZED);
            bill.setFinalizedAt(Instant.now());
            bill.setItemsSubtotal(BigDecimal.ZERO);
            bill.setChargesTotal(BigDecimal.ZERO);
            bill.setGrandTotal(BigDecimal.ZERO);
            bill.setCharges(new ArrayList<>());

            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID))
                    .thenReturn(Optional.of(bill));

            SessionBillDto dto = billService.getOrComputeBill(SESSION_ID);

            assertThat(dto.tableNumber()).isEqualTo(0);
            assertThat(dto.sessionCode()).isNull();
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle order with null totalAmount gracefully")
        void shouldHandleNullTotalAmount() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));

            CustomerOrder order1 = makeOrder(1L, new BigDecimal("5000.00"), OrderStatus.DONE);
            CustomerOrder order2 = makeOrder(2L, null, OrderStatus.DONE); // null totalAmount

            when(orderRepository.findByTableSessionIdAndStatusNot(SESSION_ID, OrderStatus.CANCELLED))
                    .thenReturn(List.of(order1, order2));
            setupDefaultRules();

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            // Should skip null and only sum 5000.00
            assertThat(bill.itemsSubtotal()).isEqualByComparingTo("5000.00");
        }

        @Test
        @DisplayName("Should handle 100% percentage rule")
        void shouldHandle100PercentRule() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("10000.00"));

            RestaurantFinancialRule fullTax = makeRule(1L, "ضريبة كاملة", "Full Tax",
                    RuleType.PERCENTAGE, "100.0000", 1, false);
            setupDefaultRules(fullTax);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.charges().get(0).computedAmount()).isEqualByComparingTo("10000.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("20000.00");
        }

        @Test
        @DisplayName("Should compute bill with many rules (up to 20)")
        void shouldComputeBillWithManyRules() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("1000.00"));

            RestaurantFinancialRule[] rules = new RestaurantFinancialRule[20];
            for (int i = 0; i < 20; i++) {
                rules[i] = makeRule((long) (i + 1), "رسم" + i, "Fee" + i,
                        RuleType.FIXED, "10.0000", i + 1, false);
            }
            setupDefaultRules(rules);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            // 20 fixed fees × 10.00 = 200.00
            assertThat(bill.charges()).hasSize(20);
            assertThat(bill.chargesTotal()).isEqualByComparingTo("200.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("1200.00");
        }

        @Test
        @DisplayName("Should compute items subtotal from multiple orders correctly")
        void shouldSumMultipleOrders() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(
                    new BigDecimal("1500.00"),
                    new BigDecimal("2500.00"),
                    new BigDecimal("3000.00"),
                    new BigDecimal("1000.00"),
                    new BigDecimal("2000.00")
            );
            setupDefaultRules();

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.itemsSubtotal()).isEqualByComparingTo("10000.00");
        }

        @Test
        @DisplayName("DRAFT bill should have null finalizedAt")
        void draftBillShouldHaveNullFinalizedAt() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));
            setupDefaultOrders(new BigDecimal("1000.00"));
            setupDefaultRules();

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.status()).isEqualTo(BillStatus.DRAFT);
            assertThat(bill.finalizedAt()).isNull();
        }
    }

    // ==================== M5: Cancelled Order Exclusion ====================

    @Nested
    @DisplayName("Cancelled Order Exclusion from Bill")
    class CancelledOrderExclusionTests {

        @Test
        @DisplayName("Should exclude cancelled orders from subtotal calculation")
        void shouldExcludeCancelledOrdersFromSubtotal() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));

            // Only non-cancelled orders should be returned by the repo
            List<CustomerOrder> nonCancelledOrders = List.of(
                    makeOrder(1L, new BigDecimal("5000.00"), OrderStatus.DONE),
                    makeOrder(2L, new BigDecimal("3000.00"), OrderStatus.SENT_TO_KITCHEN)
            );
            when(orderRepository.findByTableSessionIdAndStatusNot(SESSION_ID, OrderStatus.CANCELLED))
                    .thenReturn(nonCancelledOrders);
            setupDefaultRules();

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            // 5000 + 3000 = 8000 (cancelled orders are excluded by the repository query)
            assertThat(bill.itemsSubtotal()).isEqualByComparingTo("8000.00");
        }

        @Test
        @DisplayName("Should compute zero subtotal when all orders are cancelled")
        void shouldComputeZeroSubtotalWhenAllCancelled() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));

            // Repository returns empty list since all orders are cancelled
            when(orderRepository.findByTableSessionIdAndStatusNot(SESSION_ID, OrderStatus.CANCELLED))
                    .thenReturn(List.of());
            // Fallback also returns empty
            when(orderRepository.findByTableIdAndCreatedAtBetweenAndStatusNot(
                    eq(testTable.getId()), any(), any(), eq(OrderStatus.CANCELLED)))
                    .thenReturn(List.of());
            setupDefaultRules();

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            assertThat(bill.itemsSubtotal()).isEqualByComparingTo("0.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Should apply financial rules correctly on non-cancelled orders only")
        void shouldApplyRulesOnlyOnNonCancelledOrders() {
            when(billRepository.findByTableSessionIdWithCharges(SESSION_ID)).thenReturn(Optional.empty());
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(testSession));

            // Only DONE order included (10000)
            setupDefaultOrders(new BigDecimal("10000.00"));

            RestaurantFinancialRule vatRule = makeRule(1L, "ضريبة", "VAT",
                    RuleType.PERCENTAGE, "10.0000", 1, false);
            setupDefaultRules(vatRule);

            SessionBillDto bill = billService.getOrComputeBill(SESSION_ID);

            // subtotal=10000, VAT=10%=1000, grand=11000
            assertThat(bill.itemsSubtotal()).isEqualByComparingTo("10000.00");
            assertThat(bill.chargesTotal()).isEqualByComparingTo("1000.00");
            assertThat(bill.grandTotal()).isEqualByComparingTo("11000.00");
        }
    }
}
