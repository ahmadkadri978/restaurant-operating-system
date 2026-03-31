package com.restaurantqr.table;

import com.restaurantqr.finance.*;
import com.restaurantqr.menu.MenuCategory;
import com.restaurantqr.menu.MenuItem;
import com.restaurantqr.order.CustomerOrder;
import com.restaurantqr.order.OrderItem;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.user.Role;
import com.restaurantqr.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for the close-session-with-payment business flow.
 * <p>
 * Boots a full Spring context with H2, seeds minimal data,
 * and exercises the real wiring: service → repositories → JPA → H2.
 * <p>
 * Validates: session closure, bill finalization, invoice generation,
 * payment recording, and transactional consistency.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional   // Each test rolls back — clean state
@DisplayName("Close-Session-With-Payment Integration Test")
class CloseSessionWithPaymentIntegrationTest {

    @Autowired private StaffTableSessionService sessionService;
    @Autowired private EntityManager em;

    // Seeded entities (set up per test)
    private Restaurant restaurant;
    private RestaurantTable table;
    private TableSession session;
    private User managerUser;

    /**
     * Seed a minimal restaurant → table → open session → order with items.
     */
    @BeforeEach
    void seedData() {
        // Restaurant
        restaurant = new Restaurant();
        restaurant.setName("مطعم اختبار");
        restaurant.setCode("TEST");
        restaurant.setActive(true);
        em.persist(restaurant);

        // Manager user
        managerUser = new User();
        managerUser.setRestaurant(restaurant);
        managerUser.setUsername("manager-integration");
        managerUser.setPasswordHash("hashed");
        managerUser.setRole(Role.MANAGER);
        managerUser.setActive(true);
        em.persist(managerUser);

        // Table with open session
        table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setTableNumber(1);
        table.setQrToken("INT-TEST-T1");
        table.setActive(true);
        table.setSessionCode("9999");
        table.setSessionOpenedAt(Instant.now().minusSeconds(3600));
        em.persist(table);

        // Session history row
        session = new TableSession(restaurant, table, "9999", Instant.now().minusSeconds(3600));
        em.persist(session);

        // Menu category + item
        MenuCategory category = new MenuCategory();
        category.setRestaurant(restaurant);
        category.setName("رئيسي");
        category.setActive(true);
        em.persist(category);

        MenuItem burger = new MenuItem();
        burger.setRestaurant(restaurant);
        burger.setCategory(category);
        burger.setName("برغر");
        burger.setPrice(new BigDecimal("5000.00"));
        burger.setAvailable(true);
        burger.setActive(true);
        em.persist(burger);

        MenuItem cola = new MenuItem();
        cola.setRestaurant(restaurant);
        cola.setCategory(category);
        cola.setName("كولا");
        cola.setPrice(new BigDecimal("1000.00"));
        cola.setAvailable(true);
        cola.setActive(true);
        em.persist(cola);

        // Order linked to the session
        CustomerOrder order = new CustomerOrder();
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setTableSession(session);
        order.setTotalAmount(new BigDecimal("11000.00"));
        order.setStatus(OrderStatus.DONE);
        order.setCreatedAt(Instant.now().minusSeconds(1800));
        em.persist(order);

        // Order items
        OrderItem item1 = new OrderItem();
        item1.setOrder(order);
        item1.setMenuItem(burger);
        item1.setQuantity(2);
        item1.setUnitPrice(new BigDecimal("5000.00"));
        item1.setTotalPrice(new BigDecimal("10000.00"));
        em.persist(item1);

        OrderItem item2 = new OrderItem();
        item2.setOrder(order);
        item2.setMenuItem(cola);
        item2.setQuantity(1);
        item2.setUnitPrice(new BigDecimal("1000.00"));
        item2.setTotalPrice(new BigDecimal("1000.00"));
        em.persist(item2);

        em.flush();
        em.clear();   // Detach all — force service to load from DB
    }

    // ================================================================
    //  Happy path: full close-with-payment flow
    // ================================================================

    @Test
    @DisplayName("Full flow: session closed, bill finalized, invoice generated, payment recorded")
    void fullCloseWithPaymentFlow() {
        BigDecimal amountReceived = new BigDecimal("12000");
        BigDecimal tipAmount = new BigDecimal("500");

        CloseWithPaymentResponse response = sessionService.closeSessionWithPayment(
                restaurant.getId(), table.getId(), amountReceived, tipAmount, managerUser.getId());

        // --- Response assertions ---
        assertThat(response.closed()).isTrue();
        assertThat(response.invoiceNumber()).isNotBlank();
        assertThat(response.invoiceNumber()).startsWith("INV-");

        // grandTotal = items subtotal (11000.00) + charges (0 since no rules)
        BigDecimal expectedGrandTotal = new BigDecimal("11000.00");
        assertThat(response.grandTotal()).isEqualByComparingTo(expectedGrandTotal);
        assertThat(response.amountReceived()).isEqualByComparingTo(amountReceived);
        assertThat(response.tipAmount()).isEqualByComparingTo(tipAmount);
        BigDecimal expectedReturn = amountReceived.subtract(expectedGrandTotal).subtract(tipAmount);
        assertThat(response.amountReturned()).isEqualByComparingTo(expectedReturn);

        // --- Verify session is closed in DB ---
        em.clear();
        RestaurantTable reloadedTable = em.find(RestaurantTable.class, table.getId());
        assertThat(reloadedTable.getSessionCode()).isNull();
        assertThat(reloadedTable.getSessionOpenedAt()).isNull();

        TableSession reloadedSession = em.find(TableSession.class, session.getId());
        assertThat(reloadedSession.getClosedAt()).isNotNull();
        assertThat(reloadedSession.getDurationMinutes()).isNotNull();

        // --- Verify bill exists and is finalized ---
        SessionBill bill = em.createQuery(
                        "SELECT b FROM SessionBill b WHERE b.tableSession.id = :sid", SessionBill.class)
                .setParameter("sid", session.getId())
                .getSingleResult();
        assertThat(bill.getStatus()).isEqualTo(BillStatus.FINALIZED);
        assertThat(bill.getGrandTotal()).isEqualByComparingTo(expectedGrandTotal);

        // --- Verify invoice exists with items ---
        SessionInvoice invoice = em.createQuery(
                        "SELECT i FROM SessionInvoice i WHERE i.tableSession.id = :sid", SessionInvoice.class)
                .setParameter("sid", session.getId())
                .getSingleResult();
        assertThat(invoice.getInvoiceNumber()).isEqualTo(response.invoiceNumber());
        assertThat(invoice.getGrandTotal()).isEqualByComparingTo(expectedGrandTotal);
        assertThat(invoice.getTableNumber()).isEqualTo(1);

        // Invoice items should be present
        int itemCount = em.createQuery(
                        "SELECT COUNT(ii) FROM SessionInvoiceItem ii WHERE ii.sessionInvoice.id = :invId", Long.class)
                .setParameter("invId", invoice.getId())
                .getSingleResult()
                .intValue();
        assertThat(itemCount).isGreaterThanOrEqualTo(2);

        // --- Verify payment recorded ---
        SessionPayment payment = em.createQuery(
                        "SELECT p FROM SessionPayment p WHERE p.tableSession.id = :sid", SessionPayment.class)
                .setParameter("sid", session.getId())
                .getSingleResult();
        assertThat(payment.getAmountReceived()).isEqualByComparingTo(amountReceived);
        assertThat(payment.getTipAmount()).isEqualByComparingTo(tipAmount);
        assertThat(payment.getAmountReturned()).isEqualByComparingTo(expectedReturn);
        assertThat(payment.getPaymentMethod()).isEqualTo("CASH");
        assertThat(payment.getRecordedBy()).isNotNull();
        assertThat(payment.getRecordedBy().getId()).isEqualTo(managerUser.getId());
    }

    // ================================================================
    //  Edge case: exact payment (no surplus, no tip)
    // ================================================================

    @Test
    @DisplayName("Exact payment with zero tip — amountReturned should be zero")
    void exactPaymentNoTip() {
        BigDecimal exactAmount = new BigDecimal("11000.00");
        BigDecimal zeroTip = BigDecimal.ZERO;

        CloseWithPaymentResponse response = sessionService.closeSessionWithPayment(
                restaurant.getId(), table.getId(), exactAmount, zeroTip, managerUser.getId());

        assertThat(response.closed()).isTrue();
        assertThat(response.amountReturned()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.tipAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ================================================================
    //  Error: underpayment should throw
    // ================================================================

    @Test
    @DisplayName("Underpayment should throw BusinessRuleException")
    void underpaymentShouldThrow() {
        BigDecimal tooLittle = new BigDecimal("5000");

        assertThatThrownBy(() -> sessionService.closeSessionWithPayment(
                restaurant.getId(), table.getId(), tooLittle, BigDecimal.ZERO, managerUser.getId()))
                .isInstanceOf(com.restaurantqr.exception.BusinessRuleException.class);
    }

    // ================================================================
    //  Error: tip exceeds surplus should throw
    // ================================================================

    @Test
    @DisplayName("Tip exceeding surplus should throw BusinessRuleException")
    void tipExceedingSurplusShouldThrow() {
        BigDecimal amountReceived = new BigDecimal("12000");
        BigDecimal excessTip = new BigDecimal("2000"); // surplus = 1000, tip 2000 > surplus

        assertThatThrownBy(() -> sessionService.closeSessionWithPayment(
                restaurant.getId(), table.getId(), amountReceived, excessTip, managerUser.getId()))
                .isInstanceOf(com.restaurantqr.exception.BusinessRuleException.class);
    }
}




