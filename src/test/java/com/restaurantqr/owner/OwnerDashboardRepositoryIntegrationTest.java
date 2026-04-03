package com.restaurantqr.owner;

import com.restaurantqr.finance.BillStatus;
import com.restaurantqr.finance.SessionBill;
import com.restaurantqr.finance.SessionBillRepository;
import com.restaurantqr.finance.SessionPayment;
import com.restaurantqr.finance.SessionPaymentRepository;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.TableSession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-level integration test for Owner Dashboard JPQL queries.
 *
 * <p>Purpose: prove that the JPQL queries in {@code SessionBillRepository} and
 * {@code SessionPaymentRepository} filter on {@code finalizedAt} / {@code paidAt}
 * respectively, NOT on {@code tableSession.openedAt}.
 *
 * <p>This is the only test in the suite that exercises real SQL against H2, making it
 * the definitive proof for Changes 1 and 2 of the Owner Dashboard Financial Update Plan.
 *
 * <p>Pattern: seeds entities directly via {@code EntityManager}, calls repository
 * aggregation methods, and asserts expected sums. Each test rolls back via
 * {@code @Transactional}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Owner Dashboard Repository Integration Tests — JPQL time-boundary verification")
class OwnerDashboardRepositoryIntegrationTest {

    // ── fixed window for all tests: [DAY_1, DAY_2) ──────────────────────────────
    private static final Instant WINDOW_START = Instant.ofEpochSecond(86_400L);   // epoch + 1 day
    private static final Instant WINDOW_END   = Instant.ofEpochSecond(172_800L);  // epoch + 2 days

    // ── instants relative to window ──────────────────────────────────────────────
    /** 12 hours before window opens — used as openedAt for cross-boundary sessions. */
    private static final Instant BEFORE_WINDOW = Instant.ofEpochSecond(43_200L);
    /** 12 hours into the window — clearly inside [WINDOW_START, WINDOW_END). */
    private static final Instant INSIDE_WINDOW = Instant.ofEpochSecond(129_600L);
    /** Exactly at WINDOW_END — must be excluded (exclusive end). */
    private static final Instant AT_END_BOUNDARY = WINDOW_END;
    /** One millisecond before end — must be included. */
    private static final Instant JUST_BEFORE_END = WINDOW_END.minusMillis(1);
    /** One hour after window ends — clearly outside. */
    private static final Instant AFTER_WINDOW = Instant.ofEpochSecond(176_400L);

    @Autowired private SessionBillRepository    billRepository;
    @Autowired private SessionPaymentRepository paymentRepository;
    @Autowired private EntityManager            em;

    private Restaurant restaurant;
    private long       nextTableNum = 1;

    @BeforeEach
    void seedRestaurant() {
        restaurant = new Restaurant();
        restaurant.setName("Audit Test Restaurant");
        restaurant.setCode("AUDIT");
        restaurant.setActive(true);
        em.persist(restaurant);
        em.flush();
    }

    // ============================================================
    //  Section 1 — SessionBillRepository: finalizedAt boundary
    // ============================================================

    @Nested
    @DisplayName("SessionBillRepository — finalizedAt boundary")
    class BillFinalizedAtBoundaryTests {

        /**
         * THE KEY TEST — directly proves the query uses finalizedAt, not openedAt.
         *
         * <p>Two sessions:
         * <ul>
         *   <li>Session A: openedAt = BEFORE_WINDOW (outside), finalizedAt = INSIDE_WINDOW → MUST be counted
         *   <li>Session B: openedAt = INSIDE_WINDOW (inside), finalizedAt = AFTER_WINDOW    → MUST NOT be counted
         * </ul>
         *
         * <p>Under the old (wrong) code that filtered on openedAt:
         * Session A would be excluded (openedAt outside window) and Session B included (openedAt inside).
         * The assertion would fail, proving the fix is necessary.
         *
         * <p>Under the new (correct) code that filters on finalizedAt:
         * Session A is included (finalizedAt inside) and Session B excluded (finalizedAt after window).
         * The assertion passes.
         */
        @Test
        @DisplayName("Bills are filtered by finalizedAt — session opened before window but finalized inside is COUNTED")
        void billFinalizedInsideWindowIsCountedEvenWhenOpenedOutside() {
            // Session A: opened before window, finalized inside window → should be counted
            TableSession sessionA = seedClosedSession(BEFORE_WINDOW);
            seedFinalizedBill(sessionA, new BigDecimal("500.00"), INSIDE_WINDOW);

            // Session B: opened inside window, finalized after window → should NOT be counted
            TableSession sessionB = seedClosedSession(INSIDE_WINDOW);
            seedFinalizedBill(sessionB, new BigDecimal("999.00"), AFTER_WINDOW);

            em.flush();
            em.clear();

            BigDecimal result = billRepository.sumGrandTotalByRestaurantAndDateRange(
                    restaurant.getId(), WINDOW_START, WINDOW_END);

            assertThat(result)
                    .as("Only billA (finalizedAt inside window) should be summed; " +
                        "billB (finalizedAt outside window) must be excluded even though its session opened inside the window")
                    .isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("Bills are NOT filtered by openedAt — session opened inside window but finalized outside is EXCLUDED")
        void billFinalizedOutsideWindowIsExcludedEvenWhenOpenedInside() {
            // Session opened inside window, finalized outside → must be excluded
            TableSession session = seedClosedSession(INSIDE_WINDOW);
            seedFinalizedBill(session, new BigDecimal("1000.00"), AFTER_WINDOW);

            em.flush();
            em.clear();

            BigDecimal result = billRepository.sumGrandTotalByRestaurantAndDateRange(
                    restaurant.getId(), WINDOW_START, WINDOW_END);

            assertThat(result)
                    .as("Bill finalized outside the window must not be counted, even though its session opened inside the window")
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Window start boundary is inclusive")
        void windowStartIsInclusive() {
            TableSession session = seedClosedSession(BEFORE_WINDOW);
            seedFinalizedBill(session, new BigDecimal("200.00"), WINDOW_START); // exactly at start

            em.flush();
            em.clear();

            BigDecimal result = billRepository.sumGrandTotalByRestaurantAndDateRange(
                    restaurant.getId(), WINDOW_START, WINDOW_END);

            assertThat(result)
                    .as("finalizedAt = WINDOW_START is inclusive — bill must be counted")
                    .isEqualByComparingTo(new BigDecimal("200.00"));
        }

        @Test
        @DisplayName("Window end boundary is exclusive")
        void windowEndIsExclusive() {
            // One bill just before end (included), one exactly at end (excluded)
            TableSession sessionA = seedClosedSession(BEFORE_WINDOW);
            seedFinalizedBill(sessionA, new BigDecimal("300.00"), JUST_BEFORE_END);

            TableSession sessionB = seedClosedSession(BEFORE_WINDOW);
            seedFinalizedBill(sessionB, new BigDecimal("700.00"), AT_END_BOUNDARY);

            em.flush();
            em.clear();

            BigDecimal result = billRepository.sumGrandTotalByRestaurantAndDateRange(
                    restaurant.getId(), WINDOW_START, WINDOW_END);

            assertThat(result)
                    .as("Only the bill just before WINDOW_END should be counted; the bill exactly at WINDOW_END is exclusive")
                    .isEqualByComparingTo(new BigDecimal("300.00"));
        }

        @Test
        @DisplayName("Multiple queries (chargesTotal, count, itemsSubtotal) all use finalizedAt")
        void allBillQueriesUseFinalizedAt() {
            // Single bill: opened before window, finalized inside window
            TableSession session = seedClosedSession(BEFORE_WINDOW);
            SessionBill bill = new SessionBill(restaurant, session);
            bill.setItemsSubtotal(new BigDecimal("400.00"));
            bill.setChargesTotal(new BigDecimal("80.00"));
            bill.setGrandTotal(new BigDecimal("480.00"));
            bill.setStatus(BillStatus.FINALIZED);
            bill.setFinalizedAt(INSIDE_WINDOW);
            em.persist(bill);

            em.flush();
            em.clear();

            Long   count         = billRepository.countFinalizedBillsByRestaurantAndDateRange(restaurant.getId(), WINDOW_START, WINDOW_END);
            BigDecimal grandTotal = billRepository.sumGrandTotalByRestaurantAndDateRange(restaurant.getId(), WINDOW_START, WINDOW_END);
            BigDecimal charges    = billRepository.sumChargesTotalByRestaurantAndDateRange(restaurant.getId(), WINDOW_START, WINDOW_END);
            BigDecimal items      = billRepository.sumItemsSubtotalByRestaurantAndDateRange(restaurant.getId(), WINDOW_START, WINDOW_END);

            assertThat(count).as("count should be 1 — finalizedAt inside window").isEqualTo(1L);
            assertThat(grandTotal).as("grandTotal should be 480.00").isEqualByComparingTo(new BigDecimal("480.00"));
            assertThat(charges).as("chargesTotal should be 80.00").isEqualByComparingTo(new BigDecimal("80.00"));
            assertThat(items).as("itemsSubtotal should be 400.00").isEqualByComparingTo(new BigDecimal("400.00"));
        }

        @Test
        @DisplayName("Restaurant isolation — bills from other restaurant are not counted")
        void billRestaurantIsolation() {
            // Other restaurant
            Restaurant other = new Restaurant();
            other.setName("Other Restaurant");
            other.setCode("OTH");
            other.setActive(true);
            em.persist(other);

            RestaurantTable otherTable = new RestaurantTable();
            otherTable.setRestaurant(other);
            otherTable.setTableNumber(99);
            otherTable.setQrToken("OTHER-QR");
            otherTable.setActive(true);
            em.persist(otherTable);

            TableSession otherSession = new TableSession(other, otherTable, "0000", BEFORE_WINDOW);
            otherSession.close(INSIDE_WINDOW);
            em.persist(otherSession);

            SessionBill otherBill = new SessionBill(other, otherSession);
            otherBill.setGrandTotal(new BigDecimal("9999.00"));
            otherBill.setStatus(BillStatus.FINALIZED);
            otherBill.setFinalizedAt(INSIDE_WINDOW);
            em.persist(otherBill);

            // Our restaurant has NO bills
            em.flush();
            em.clear();

            BigDecimal result = billRepository.sumGrandTotalByRestaurantAndDateRange(
                    restaurant.getId(), WINDOW_START, WINDOW_END);

            assertThat(result)
                    .as("Bills from a different restaurant must not appear in this restaurant's sum")
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ============================================================
    //  Section 2 — SessionPaymentRepository: paidAt boundary
    // ============================================================

    @Nested
    @DisplayName("SessionPaymentRepository — paidAt boundary")
    class PaymentPaidAtBoundaryTests {

        /**
         * THE KEY TEST for tip metrics — proves the query uses paidAt, not openedAt.
         *
         * <p>Two sessions:
         * <ul>
         *   <li>Session A: openedAt = BEFORE_WINDOW, paidAt = INSIDE_WINDOW → tip MUST be counted
         *   <li>Session B: openedAt = INSIDE_WINDOW, paidAt = AFTER_WINDOW  → tip MUST NOT be counted
         * </ul>
         */
        @Test
        @DisplayName("Tips are filtered by paidAt — payment made inside window is COUNTED even when session opened outside")
        void tipPaidInsideWindowIsCountedEvenWhenSessionOpenedOutside() {
            // Session A: opened before window, payment recorded inside window → tip counted
            TableSession sessionA = seedClosedSession(BEFORE_WINDOW);
            SessionBill  billA   = seedFinalizedBill(sessionA, new BigDecimal("1000.00"), INSIDE_WINDOW);
            seedPayment(sessionA, billA, new BigDecimal("50.00"), INSIDE_WINDOW);

            // Session B: opened inside window, payment recorded after window → tip NOT counted
            TableSession sessionB = seedClosedSession(INSIDE_WINDOW);
            SessionBill  billB   = seedFinalizedBill(sessionB, new BigDecimal("1000.00"), AFTER_WINDOW);
            seedPayment(sessionB, billB, new BigDecimal("200.00"), AFTER_WINDOW);

            em.flush();
            em.clear();

            BigDecimal result = paymentRepository.sumTipByRestaurantAndDateRange(
                    restaurant.getId(), WINDOW_START, WINDOW_END);

            assertThat(result)
                    .as("Only tipA (paidAt inside window) should be summed; " +
                        "tipB (paidAt outside window) must be excluded even though its session opened inside the window")
                    .isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("Tips are NOT filtered by openedAt — payment outside window is EXCLUDED even when session opened inside")
        void tipPaidOutsideWindowIsExcludedEvenWhenSessionOpenedInside() {
            TableSession session = seedClosedSession(INSIDE_WINDOW);
            SessionBill  bill   = seedFinalizedBill(session, new BigDecimal("1000.00"), AFTER_WINDOW);
            seedPayment(session, bill, new BigDecimal("100.00"), AFTER_WINDOW);

            em.flush();
            em.clear();

            BigDecimal result = paymentRepository.sumTipByRestaurantAndDateRange(
                    restaurant.getId(), WINDOW_START, WINDOW_END);

            assertThat(result)
                    .as("Tip paid after window must be excluded, even though the session opened inside the window")
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Tip window start boundary is inclusive")
        void tipWindowStartIsInclusive() {
            TableSession session = seedClosedSession(BEFORE_WINDOW);
            SessionBill  bill   = seedFinalizedBill(session, new BigDecimal("1000.00"), WINDOW_START);
            seedPayment(session, bill, new BigDecimal("25.00"), WINDOW_START); // paidAt = exactly at start

            em.flush();
            em.clear();

            BigDecimal result = paymentRepository.sumTipByRestaurantAndDateRange(
                    restaurant.getId(), WINDOW_START, WINDOW_END);

            assertThat(result)
                    .as("paidAt = WINDOW_START is inclusive — tip must be counted")
                    .isEqualByComparingTo(new BigDecimal("25.00"));
        }

        @Test
        @DisplayName("Tip window end boundary is exclusive")
        void tipWindowEndIsExclusive() {
            TableSession sessionA = seedClosedSession(BEFORE_WINDOW);
            SessionBill  billA   = seedFinalizedBill(sessionA, new BigDecimal("1000.00"), JUST_BEFORE_END);
            seedPayment(sessionA, billA, new BigDecimal("15.00"), JUST_BEFORE_END);

            TableSession sessionB = seedClosedSession(BEFORE_WINDOW);
            SessionBill  billB   = seedFinalizedBill(sessionB, new BigDecimal("1000.00"), AT_END_BOUNDARY);
            seedPayment(sessionB, billB, new BigDecimal("85.00"), AT_END_BOUNDARY);

            em.flush();
            em.clear();

            BigDecimal result = paymentRepository.sumTipByRestaurantAndDateRange(
                    restaurant.getId(), WINDOW_START, WINDOW_END);

            assertThat(result)
                    .as("Only the tip just before WINDOW_END should be counted; the tip at WINDOW_END is exclusive")
                    .isEqualByComparingTo(new BigDecimal("15.00"));
        }

        @Test
        @DisplayName("Restaurant isolation — tips from other restaurant are not counted")
        void tipRestaurantIsolation() {
            Restaurant other = new Restaurant();
            other.setName("Other Restaurant");
            other.setCode("OTH2");
            other.setActive(true);
            em.persist(other);

            RestaurantTable otherTable = new RestaurantTable();
            otherTable.setRestaurant(other);
            otherTable.setTableNumber(88);
            otherTable.setQrToken("OTHER-QR2");
            otherTable.setActive(true);
            em.persist(otherTable);

            TableSession otherSession = new TableSession(other, otherTable, "0001", BEFORE_WINDOW);
            otherSession.close(INSIDE_WINDOW);
            em.persist(otherSession);

            SessionBill otherBill = new SessionBill(other, otherSession);
            otherBill.setGrandTotal(new BigDecimal("500.00"));
            otherBill.setStatus(BillStatus.FINALIZED);
            otherBill.setFinalizedAt(INSIDE_WINDOW);
            em.persist(otherBill);

            SessionPayment otherPayment = new SessionPayment(
                    other, otherSession, otherBill,
                    new BigDecimal("500.00"),
                    new BigDecimal("600.00"),
                    BigDecimal.ZERO,
                    new BigDecimal("9999.00"), // tip
                    "CASH",
                    INSIDE_WINDOW,
                    null
            );
            em.persist(otherPayment);

            em.flush();
            em.clear();

            BigDecimal result = paymentRepository.sumTipByRestaurantAndDateRange(
                    restaurant.getId(), WINDOW_START, WINDOW_END);

            assertThat(result)
                    .as("Tips from a different restaurant must not appear in this restaurant's sum")
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ============================================================
    //  Helper methods
    // ============================================================

    /** Seeds a closed TableSession with a given openedAt, returns the persisted entity. */
    private TableSession seedClosedSession(Instant openedAt) {
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setTableNumber((int) nextTableNum++);
        table.setQrToken("QR-" + nextTableNum);
        table.setActive(true);
        em.persist(table);

        TableSession session = new TableSession(restaurant, table, "CODE", openedAt);
        session.close(openedAt.plusSeconds(3600)); // closed 1h after opening
        em.persist(session);
        return session;
    }

    /** Seeds a FINALIZED SessionBill with a specific finalizedAt and grandTotal. */
    private SessionBill seedFinalizedBill(TableSession session, BigDecimal grandTotal, Instant finalizedAt) {
        SessionBill bill = new SessionBill(restaurant, session);
        bill.setItemsSubtotal(grandTotal);
        bill.setChargesTotal(BigDecimal.ZERO);
        bill.setGrandTotal(grandTotal);
        bill.setStatus(BillStatus.FINALIZED);
        bill.setFinalizedAt(finalizedAt);
        em.persist(bill);
        return bill;
    }

    /** Seeds a SessionPayment with a specific paidAt and tipAmount. */
    private void seedPayment(TableSession session, SessionBill bill,
                             BigDecimal tipAmount, Instant paidAt) {
        SessionPayment payment = new SessionPayment(
                restaurant, session, bill,
                bill.getGrandTotal(),
                bill.getGrandTotal(),
                BigDecimal.ZERO,
                tipAmount,
                "CASH",
                paidAt,
                null
        );
        em.persist(payment);
    }
}



