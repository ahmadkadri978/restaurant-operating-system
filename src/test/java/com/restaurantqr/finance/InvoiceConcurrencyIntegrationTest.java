package com.restaurantqr.finance;

import com.restaurantqr.menu.MenuCategory;
import com.restaurantqr.menu.MenuItem;
import com.restaurantqr.order.CustomerOrder;
import com.restaurantqr.order.OrderItem;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.CloseWithPaymentResponse;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.StaffTableSessionService;
import com.restaurantqr.table.TableSession;
import com.restaurantqr.user.Role;
import com.restaurantqr.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Invoice Concurrency Integration Test")
class InvoiceConcurrencyIntegrationTest {

    private static final ZoneId DAMASCUS = ZoneId.of("Asia/Damascus");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired private InvoiceService invoiceService;
    @Autowired private InvoiceCounterRepository invoiceCounterRepository;
    @Autowired private StaffTableSessionService sessionService;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Parallel generateInvoice should reserve distinct invoice numbers for same restaurant/day")
    void parallelGenerateInvoiceShouldReserveDistinctInvoiceNumbers() throws Exception {
        ClosedSessionsSeed seed = tx().execute(status -> seedClosedSessionsForInvoiceGeneration());

        List<String> invoiceNumbers = runConcurrently(List.of(
                () -> invoiceService.generateInvoice(seed.sessionId1()).getInvoiceNumber(),
                () -> invoiceService.generateInvoice(seed.sessionId2()).getInvoiceNumber()
        ));

        assertThat(invoiceNumbers).hasSize(2);
        assertThat(Set.copyOf(invoiceNumbers)).hasSize(2);
        assertThat(invoiceNumbers).allMatch(number -> number.matches("INV-\\d{8}-\\d{3}"));

        tx().executeWithoutResult(status -> {
            String dateStr = LocalDate.now(DAMASCUS).format(DATE_FMT);
            InvoiceCounter counter = invoiceCounterRepository.findByRestaurantIdAndDateStr(seed.restaurantId(), dateStr)
                    .orElseThrow();

            assertThat(counter.getLastSeq()).isEqualTo(2);

            Long invoiceCount = em.createQuery(
                            "SELECT COUNT(i) FROM SessionInvoice i WHERE i.restaurant.id = :restaurantId", Long.class)
                    .setParameter("restaurantId", seed.restaurantId())
                    .getSingleResult();
            assertThat(invoiceCount).isEqualTo(2L);
        });
    }

    @Test
    @DisplayName("Parallel closeSessionWithPayment should close both sessions and create distinct invoices")
    void parallelCloseSessionWithPaymentShouldCloseBothSessionsAndCreateDistinctInvoices() throws Exception {
        OpenSessionsSeed seed = tx().execute(status -> seedOpenSessionsForCloseWithPayment());

        List<CloseWithPaymentResponse> responses = runConcurrently(List.of(
                () -> sessionService.closeSessionWithPayment(
                        seed.restaurantId(), seed.tableId1(), seed.amountReceived(), BigDecimal.ZERO, seed.userId()),
                () -> sessionService.closeSessionWithPayment(
                        seed.restaurantId(), seed.tableId2(), seed.amountReceived(), BigDecimal.ZERO, seed.userId())
        ));

        assertThat(responses).hasSize(2);
        assertThat(responses).allMatch(CloseWithPaymentResponse::closed);
        assertThat(responses)
                .extracting(CloseWithPaymentResponse::invoiceNumber)
                .allMatch(number -> number.matches("INV-\\d{8}-\\d{3}"));
        assertThat(responses)
                .extracting(CloseWithPaymentResponse::invoiceNumber)
                .doesNotHaveDuplicates();

        tx().executeWithoutResult(status -> {
            RestaurantTable table1 = em.find(RestaurantTable.class, seed.tableId1());
            RestaurantTable table2 = em.find(RestaurantTable.class, seed.tableId2());
            assertThat(table1.getSessionCode()).isNull();
            assertThat(table2.getSessionCode()).isNull();

            Long paymentCount = em.createQuery(
                            "SELECT COUNT(p) FROM SessionPayment p WHERE p.restaurant.id = :restaurantId", Long.class)
                    .setParameter("restaurantId", seed.restaurantId())
                    .getSingleResult();
            assertThat(paymentCount).isEqualTo(2L);

            Long invoiceCount = em.createQuery(
                            "SELECT COUNT(i) FROM SessionInvoice i WHERE i.restaurant.id = :restaurantId", Long.class)
                    .setParameter("restaurantId", seed.restaurantId())
                    .getSingleResult();
            assertThat(invoiceCount).isEqualTo(2L);

            String dateStr = LocalDate.now(DAMASCUS).format(DATE_FMT);
            InvoiceCounter counter = invoiceCounterRepository.findByRestaurantIdAndDateStr(seed.restaurantId(), dateStr)
                    .orElseThrow();
            assertThat(counter.getLastSeq()).isEqualTo(2);
        });
    }

    private ClosedSessionsSeed seedClosedSessionsForInvoiceGeneration() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("مطعم تزامن الفواتير");
        restaurant.setCode("INVCC");
        restaurant.setActive(true);
        em.persist(restaurant);

        RestaurantTable table1 = createTable(restaurant, 1, "INV-C-1");
        RestaurantTable table2 = createTable(restaurant, 2, "INV-C-2");

        Instant openedAt = Instant.now().minusSeconds(3600);
        Instant closedAt = Instant.now().minusSeconds(60);

        TableSession session1 = new TableSession(restaurant, table1, "1101", openedAt);
        session1.close(closedAt);
        em.persist(session1);

        TableSession session2 = new TableSession(restaurant, table2, "1102", openedAt);
        session2.close(closedAt);
        em.persist(session2);

        em.persist(createFinalizedBill(restaurant, session1, new BigDecimal("10000.00")));
        em.persist(createFinalizedBill(restaurant, session2, new BigDecimal("10000.00")));

        em.flush();
        em.clear();

        return new ClosedSessionsSeed(restaurant.getId(), session1.getId(), session2.getId());
    }

    private OpenSessionsSeed seedOpenSessionsForCloseWithPayment() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("مطعم إغلاق متوازي");
        restaurant.setCode("CLSCC");
        restaurant.setActive(true);
        em.persist(restaurant);

        User managerUser = new User();
        managerUser.setRestaurant(restaurant);
        managerUser.setUsername("manager-concurrency");
        managerUser.setPasswordHash("hashed");
        managerUser.setRole(Role.MANAGER);
        managerUser.setActive(true);
        em.persist(managerUser);

        MenuCategory category = new MenuCategory();
        category.setRestaurant(restaurant);
        category.setName("رئيسي");
        category.setActive(true);
        em.persist(category);

        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        item.setCategory(category);
        item.setName("وجبة");
        item.setPrice(new BigDecimal("11000.00"));
        item.setAvailable(true);
        item.setActive(true);
        em.persist(item);

        RestaurantTable table1 = createOpenTable(restaurant, 1, "CLS-C-1", "2201");
        RestaurantTable table2 = createOpenTable(restaurant, 2, "CLS-C-2", "2202");

        TableSession session1 = new TableSession(restaurant, table1, "2201", Instant.now().minusSeconds(3600));
        TableSession session2 = new TableSession(restaurant, table2, "2202", Instant.now().minusSeconds(3600));
        em.persist(session1);
        em.persist(session2);

        createOrderWithSingleItem(restaurant, table1, session1, item, new BigDecimal("11000.00"));
        createOrderWithSingleItem(restaurant, table2, session2, item, new BigDecimal("11000.00"));

        em.flush();
        em.clear();

        return new OpenSessionsSeed(
                restaurant.getId(),
                managerUser.getId(),
                table1.getId(),
                table2.getId(),
                new BigDecimal("11000.00")
        );
    }

    private RestaurantTable createTable(Restaurant restaurant, int tableNumber, String qrToken) {
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setTableNumber(tableNumber);
        table.setQrToken(qrToken);
        table.setActive(true);
        em.persist(table);
        return table;
    }

    private RestaurantTable createOpenTable(Restaurant restaurant, int tableNumber, String qrToken, String sessionCode) {
        RestaurantTable table = createTable(restaurant, tableNumber, qrToken);
        table.setSessionCode(sessionCode);
        table.setSessionOpenedAt(Instant.now().minusSeconds(3600));
        return table;
    }

    private SessionBill createFinalizedBill(Restaurant restaurant, TableSession session, BigDecimal grandTotal) {
        SessionBill bill = new SessionBill(restaurant, session);
        bill.setItemsSubtotal(grandTotal);
        bill.setChargesTotal(BigDecimal.ZERO);
        bill.setGrandTotal(grandTotal);
        bill.setStatus(BillStatus.FINALIZED);
        bill.setFinalizedAt(Instant.now().minusSeconds(30));
        bill.setUpdatedAt(Instant.now().minusSeconds(30));
        return bill;
    }

    private void createOrderWithSingleItem(Restaurant restaurant, RestaurantTable table, TableSession session,
                                           MenuItem item, BigDecimal totalAmount) {
        CustomerOrder order = new CustomerOrder();
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setTableSession(session);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.DONE);
        order.setCreatedAt(Instant.now().minusSeconds(900));
        em.persist(order);

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setMenuItem(item);
        orderItem.setQuantity(1);
        orderItem.setUnitPrice(totalAmount);
        orderItem.setTotalPrice(totalAmount);
        em.persist(orderItem);
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                        return task.call();
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return futures.stream()
                    .map(future -> getFutureValue(future))
                    .toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private <T> T getFutureValue(Future<T> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private record ClosedSessionsSeed(Long restaurantId, Long sessionId1, Long sessionId2) {}

    private record OpenSessionsSeed(Long restaurantId, Long userId, Long tableId1, Long tableId2,
                                    BigDecimal amountReceived) {}
}


