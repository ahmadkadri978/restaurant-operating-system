package com.restaurantqr.finance;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.CashMovementDto;
import com.restaurantqr.finance.dto.CashRegisterDto;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CashRegisterService Unit Tests")
class CashRegisterServiceTest {

    @Mock private CashRegisterSessionRepository registerRepository;
    @Mock private CashMovementRepository movementRepository;
    @Mock private SessionPaymentRepository paymentRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private UserRepository userRepository;

    private CashRegisterServiceImpl service;

    private Restaurant testRestaurant;
    private User testUser;
    private static final Long RESTAURANT_ID = 1L;
    private static final Long USER_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new CashRegisterServiceImpl(
                registerRepository, movementRepository, paymentRepository,
                restaurantRepository, userRepository
        );

        testRestaurant = new Restaurant();
        testRestaurant.setId(RESTAURANT_ID);
        testRestaurant.setName("Test Restaurant");

        testUser = new User();
        testUser.setId(USER_ID);
        testUser.setUsername("manager1");

        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
    }

    // ==================== Open Register Tests ====================

    @Nested
    @DisplayName("Open Register")
    class OpenRegisterTests {

        @Test
        @DisplayName("Should open register with valid opening balance")
        void shouldOpenRegisterSuccessfully() {
            when(registerRepository.existsByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN)).thenReturn(false);
            when(registerRepository.save(any(CashRegisterSession.class))).thenAnswer(inv -> {
                CashRegisterSession s = inv.getArgument(0);
                s.setId(100L);
                return s;
            });

            CashRegisterDto result = service.openRegister(RESTAURANT_ID, USER_ID, new BigDecimal("50000"));

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo("OPEN");
            assertThat(result.openingBalance()).isEqualByComparingTo("50000");
            assertThat(result.openedByUsername()).isEqualTo("manager1");

            ArgumentCaptor<CashRegisterSession> captor = ArgumentCaptor.forClass(CashRegisterSession.class);
            verify(registerRepository).save(captor.capture());
            assertThat(captor.getValue().getOpeningBalance()).isEqualByComparingTo("50000");
            assertThat(captor.getValue().getStatus()).isEqualTo(CashRegisterStatus.OPEN);
        }

        @Test
        @DisplayName("Should open register with zero balance")
        void shouldOpenRegisterWithZeroBalance() {
            when(registerRepository.existsByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN)).thenReturn(false);
            when(registerRepository.save(any())).thenAnswer(inv -> { ((CashRegisterSession)inv.getArgument(0)).setId(1L); return inv.getArgument(0); });

            CashRegisterDto result = service.openRegister(RESTAURANT_ID, USER_ID, BigDecimal.ZERO);

            assertThat(result.openingBalance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Should reject opening when register already open")
        void shouldRejectDuplicateOpen() {
            when(registerRepository.existsByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN)).thenReturn(true);

            assertThatThrownBy(() -> service.openRegister(RESTAURANT_ID, USER_ID, new BigDecimal("50000")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("يوجد صندوق مفتوح");
        }

        @Test
        @DisplayName("Should reject negative opening balance")
        void shouldRejectNegativeBalance() {
            assertThatThrownBy(() -> service.openRegister(RESTAURANT_ID, USER_ID, new BigDecimal("-100")))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should reject null opening balance")
        void shouldRejectNullBalance() {
            assertThatThrownBy(() -> service.openRegister(RESTAURANT_ID, USER_ID, null))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    // ==================== Close Register Tests ====================

    @Nested
    @DisplayName("Close Register")
    class CloseRegisterTests {

        private CashRegisterSession openRegister;

        @BeforeEach
        void setUpOpenRegister() {
            openRegister = new CashRegisterSession(testRestaurant, testUser, new BigDecimal("10000"), java.time.Instant.now());
            openRegister.setId(50L);
        }

        @Test
        @DisplayName("Should close register and compute expected balance correctly — no tip double-counting")
        void shouldCloseWithCorrectExpectedBalance() {
            when(registerRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN))
                    .thenReturn(Optional.of(openRegister));
            // Simulate: 2 payments, received=25000, tips=3000, returned=2000
            when(paymentRepository.sumAmountReceivedByRegisterSession(50L)).thenReturn(new BigDecimal("25000"));
            when(paymentRepository.sumTipByRegisterSession(50L)).thenReturn(new BigDecimal("3000"));
            when(paymentRepository.sumAmountReturnedByRegisterSession(50L)).thenReturn(new BigDecimal("2000"));
            when(movementRepository.sumCashInByRegisterSession(50L)).thenReturn(BigDecimal.ZERO);
            when(movementRepository.sumCashOutByRegisterSession(50L)).thenReturn(BigDecimal.ZERO);
            when(paymentRepository.countByCashRegisterSessionId(50L)).thenReturn(2L);
            when(movementRepository.findByCashRegisterSessionIdOrderByCreatedAtAsc(50L)).thenReturn(Collections.emptyList());
            when(registerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // expected = 10000 + 25000 - 2000 + 0 - 0 = 33000  (tip NOT added separately)
            BigDecimal actualCash = new BigDecimal("33000");
            CashRegisterDto result = service.closeRegister(RESTAURANT_ID, USER_ID, actualCash, "test close");

            assertThat(result.status()).isEqualTo("CLOSED");
            assertThat(result.expectedClosing()).isEqualByComparingTo("33000");
            assertThat(result.actualClosing()).isEqualByComparingTo("33000");
            assertThat(result.discrepancy()).isEqualByComparingTo("0");
            assertThat(result.discrepancyType()).isEqualTo("MATCH");
            assertThat(result.paymentCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should detect shortage when actual < expected")
        void shouldDetectShortage() {
            when(registerRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN))
                    .thenReturn(Optional.of(openRegister));
            when(paymentRepository.sumAmountReceivedByRegisterSession(50L)).thenReturn(new BigDecimal("20000"));
            when(paymentRepository.sumTipByRegisterSession(50L)).thenReturn(BigDecimal.ZERO);
            when(paymentRepository.sumAmountReturnedByRegisterSession(50L)).thenReturn(BigDecimal.ZERO);
            when(movementRepository.sumCashInByRegisterSession(50L)).thenReturn(BigDecimal.ZERO);
            when(movementRepository.sumCashOutByRegisterSession(50L)).thenReturn(BigDecimal.ZERO);
            when(paymentRepository.countByCashRegisterSessionId(50L)).thenReturn(1L);
            when(movementRepository.findByCashRegisterSessionIdOrderByCreatedAtAsc(50L)).thenReturn(Collections.emptyList());
            when(registerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // expected = 10000 + 20000 = 30000, actual = 28000 → shortage -2000
            CashRegisterDto result = service.closeRegister(RESTAURANT_ID, USER_ID, new BigDecimal("28000"), null);

            assertThat(result.discrepancyType()).isEqualTo("SHORTAGE");
            assertThat(result.discrepancy()).isEqualByComparingTo("-2000");
        }

        @Test
        @DisplayName("Should detect overage when actual > expected")
        void shouldDetectOverage() {
            when(registerRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN))
                    .thenReturn(Optional.of(openRegister));
            when(paymentRepository.sumAmountReceivedByRegisterSession(50L)).thenReturn(new BigDecimal("20000"));
            when(paymentRepository.sumTipByRegisterSession(50L)).thenReturn(BigDecimal.ZERO);
            when(paymentRepository.sumAmountReturnedByRegisterSession(50L)).thenReturn(BigDecimal.ZERO);
            when(movementRepository.sumCashInByRegisterSession(50L)).thenReturn(BigDecimal.ZERO);
            when(movementRepository.sumCashOutByRegisterSession(50L)).thenReturn(BigDecimal.ZERO);
            when(paymentRepository.countByCashRegisterSessionId(50L)).thenReturn(1L);
            when(movementRepository.findByCashRegisterSessionIdOrderByCreatedAtAsc(50L)).thenReturn(Collections.emptyList());
            when(registerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CashRegisterDto result = service.closeRegister(RESTAURANT_ID, USER_ID, new BigDecimal("32000"), null);

            assertThat(result.discrepancyType()).isEqualTo("OVERAGE");
            assertThat(result.discrepancy()).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("Should include cash movements in expected balance")
        void shouldIncludeCashMovementsInExpected() {
            when(registerRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN))
                    .thenReturn(Optional.of(openRegister));
            when(paymentRepository.sumAmountReceivedByRegisterSession(50L)).thenReturn(new BigDecimal("20000"));
            when(paymentRepository.sumTipByRegisterSession(50L)).thenReturn(BigDecimal.ZERO);
            when(paymentRepository.sumAmountReturnedByRegisterSession(50L)).thenReturn(BigDecimal.ZERO);
            when(movementRepository.sumCashInByRegisterSession(50L)).thenReturn(new BigDecimal("5000"));
            when(movementRepository.sumCashOutByRegisterSession(50L)).thenReturn(new BigDecimal("3000"));
            when(paymentRepository.countByCashRegisterSessionId(50L)).thenReturn(1L);
            when(movementRepository.findByCashRegisterSessionIdOrderByCreatedAtAsc(50L)).thenReturn(Collections.emptyList());
            when(registerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // expected = 10000 + 20000 - 0 + 5000 - 3000 = 32000
            CashRegisterDto result = service.closeRegister(RESTAURANT_ID, USER_ID, new BigDecimal("32000"), null);

            assertThat(result.expectedClosing()).isEqualByComparingTo("32000");
            assertThat(result.discrepancyType()).isEqualTo("MATCH");
        }

        @Test
        @DisplayName("Should reject close when no register is open")
        void shouldRejectCloseWhenNoneOpen() {
            when(registerRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.closeRegister(RESTAURANT_ID, USER_ID, new BigDecimal("10000"), null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("لا يوجد صندوق مفتوح");
        }

        @Test
        @DisplayName("Should reject null actual closing")
        void shouldRejectNullActualClosing() {
            assertThatThrownBy(() -> service.closeRegister(RESTAURANT_ID, USER_ID, null, null))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    // ==================== Record Movement Tests ====================

    @Nested
    @DisplayName("Record Movement")
    class RecordMovementTests {

        private CashRegisterSession openRegister;

        @BeforeEach
        void setUpOpenRegister() {
            openRegister = new CashRegisterSession(testRestaurant, testUser, new BigDecimal("10000"), java.time.Instant.now());
            openRegister.setId(50L);
            when(registerRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN))
                    .thenReturn(Optional.of(openRegister));
            when(movementRepository.save(any(CashMovement.class))).thenAnswer(inv -> {
                CashMovement m = inv.getArgument(0);
                m.setId(1L);
                return m;
            });
        }

        @Test
        @DisplayName("Should record CASH_IN movement")
        void shouldRecordCashIn() {
            CashMovementDto result = service.recordMovement(RESTAURANT_ID, USER_ID, "CASH_IN", new BigDecimal("5000"), "إضافة فلوس");

            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo("CASH_IN");
            assertThat(result.amount()).isEqualByComparingTo("5000");
            assertThat(result.reason()).isEqualTo("إضافة فلوس");

            ArgumentCaptor<CashMovement> captor = ArgumentCaptor.forClass(CashMovement.class);
            verify(movementRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(CashMovementType.CASH_IN);
        }

        @Test
        @DisplayName("Should record CASH_OUT movement")
        void shouldRecordCashOut() {
            CashMovementDto result = service.recordMovement(RESTAURANT_ID, USER_ID, "CASH_OUT", new BigDecimal("2000"), "شراء مواد");

            assertThat(result.type()).isEqualTo("CASH_OUT");
            assertThat(result.amount()).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("Should reject zero amount")
        void shouldRejectZeroAmount() {
            assertThatThrownBy(() -> service.recordMovement(RESTAURANT_ID, USER_ID, "CASH_IN", BigDecimal.ZERO, "test"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("أكبر من صفر");
        }

        @Test
        @DisplayName("Should reject blank reason")
        void shouldRejectBlankReason() {
            assertThatThrownBy(() -> service.recordMovement(RESTAURANT_ID, USER_ID, "CASH_IN", new BigDecimal("100"), "  "))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Should reject invalid movement type")
        void shouldRejectInvalidType() {
            assertThatThrownBy(() -> service.recordMovement(RESTAURANT_ID, USER_ID, "INVALID", new BigDecimal("100"), "test"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("نوع الحركة غير صالح");
        }

        @Test
        @DisplayName("Should reject movement when no register is open")
        void shouldRejectMovementWhenNoneOpen() {
            when(registerRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recordMovement(RESTAURANT_ID, USER_ID, "CASH_IN", new BigDecimal("100"), "test"))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    // ==================== Get Current Register Tests ====================

    @Nested
    @DisplayName("Get Current Register")
    class GetCurrentRegisterTests {

        @Test
        @DisplayName("Should return empty when no register is open")
        void shouldReturnEmptyWhenNoneOpen() {
            when(registerRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN))
                    .thenReturn(Optional.empty());

            Optional<CashRegisterDto> result = service.getCurrentRegister(RESTAURANT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return register with live expected balance when open")
        void shouldReturnOpenRegisterWithLiveExpected() {
            CashRegisterSession reg = new CashRegisterSession(testRestaurant, testUser, new BigDecimal("5000"), java.time.Instant.now());
            reg.setId(10L);
            when(registerRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, CashRegisterStatus.OPEN))
                    .thenReturn(Optional.of(reg));
            when(paymentRepository.sumAmountReceivedByRegisterSession(10L)).thenReturn(new BigDecimal("15000"));
            when(paymentRepository.sumTipByRegisterSession(10L)).thenReturn(new BigDecimal("2000"));
            when(paymentRepository.sumAmountReturnedByRegisterSession(10L)).thenReturn(new BigDecimal("1000"));
            when(movementRepository.sumCashInByRegisterSession(10L)).thenReturn(BigDecimal.ZERO);
            when(movementRepository.sumCashOutByRegisterSession(10L)).thenReturn(BigDecimal.ZERO);
            when(paymentRepository.countByCashRegisterSessionId(10L)).thenReturn(3L);
            when(movementRepository.findByCashRegisterSessionIdOrderByCreatedAtAsc(10L)).thenReturn(Collections.emptyList());

            Optional<CashRegisterDto> result = service.getCurrentRegister(RESTAURANT_ID);

            assertThat(result).isPresent();
            CashRegisterDto dto = result.get();
            assertThat(dto.status()).isEqualTo("OPEN");
            // expected = 5000 + 15000 - 1000 = 19000 (tip NOT added)
            assertThat(dto.expectedClosing()).isEqualByComparingTo("19000");
            assertThat(dto.totalTips()).isEqualByComparingTo("2000");
            assertThat(dto.paymentCount()).isEqualTo(3);
        }
    }

    // ==================== Get Register By ID Tests ====================

    @Nested
    @DisplayName("Get Register By ID")
    class GetRegisterByIdTests {

        @Test
        @DisplayName("Should reject access to register from different restaurant")
        void shouldRejectCrossRestaurantAccess() {
            Restaurant otherRestaurant = new Restaurant();
            otherRestaurant.setId(999L);
            CashRegisterSession reg = new CashRegisterSession(otherRestaurant, testUser, BigDecimal.ZERO, java.time.Instant.now());
            reg.setId(10L);
            when(registerRepository.findById(10L)).thenReturn(Optional.of(reg));

            assertThatThrownBy(() -> service.getRegisterById(RESTAURANT_ID, 10L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw when register not found")
        void shouldThrowWhenNotFound() {
            when(registerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRegisterById(RESTAURANT_ID, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== M6: Get Register History ====================

    @Nested
    @DisplayName("Get Register History")
    class GetRegisterHistoryTests {

        @Test
        @DisplayName("Should return closed registers for restaurant")
        void shouldReturnClosedRegisters() {
            CashRegisterSession reg1 = new CashRegisterSession(testRestaurant, testUser, new BigDecimal("10000"), java.time.Instant.now());
            reg1.setId(1L);
            reg1.setStatus(CashRegisterStatus.CLOSED);
            CashRegisterSession reg2 = new CashRegisterSession(testRestaurant, testUser, new BigDecimal("20000"), java.time.Instant.now());
            reg2.setId(2L);
            reg2.setStatus(CashRegisterStatus.CLOSED);

            when(registerRepository.findByRestaurantIdAndStatusOrderByClosedAtDesc(RESTAURANT_ID, CashRegisterStatus.CLOSED))
                    .thenReturn(List.of(reg1, reg2));

            // stub toDtoWithTotals dependencies for each register
            for (long id : List.of(1L, 2L)) {
                when(paymentRepository.sumAmountReceivedByRegisterSession(id)).thenReturn(BigDecimal.ZERO);
                when(paymentRepository.sumTipByRegisterSession(id)).thenReturn(BigDecimal.ZERO);
                when(paymentRepository.sumAmountReturnedByRegisterSession(id)).thenReturn(BigDecimal.ZERO);
                when(movementRepository.sumCashInByRegisterSession(id)).thenReturn(BigDecimal.ZERO);
                when(movementRepository.sumCashOutByRegisterSession(id)).thenReturn(BigDecimal.ZERO);
                when(paymentRepository.countByCashRegisterSessionId(id)).thenReturn(0L);
                when(movementRepository.findByCashRegisterSessionIdOrderByCreatedAtAsc(id)).thenReturn(Collections.emptyList());
            }

            List<CashRegisterDto> result = service.getRegisterHistory(RESTAURANT_ID);

            assertThat(result).hasSize(2);
            verify(registerRepository).findByRestaurantIdAndStatusOrderByClosedAtDesc(RESTAURANT_ID, CashRegisterStatus.CLOSED);
        }

        @Test
        @DisplayName("Should return empty list when no closed registers")
        void shouldReturnEmptyWhenNoHistory() {
            when(registerRepository.findByRestaurantIdAndStatusOrderByClosedAtDesc(RESTAURANT_ID, CashRegisterStatus.CLOSED))
                    .thenReturn(Collections.emptyList());

            List<CashRegisterDto> result = service.getRegisterHistory(RESTAURANT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should compute totals for each register in history")
        void shouldComputeTotalsForEachRegister() {
            CashRegisterSession reg = new CashRegisterSession(testRestaurant, testUser, new BigDecimal("5000"), java.time.Instant.now());
            reg.setId(10L);
            reg.setStatus(CashRegisterStatus.CLOSED);
            reg.setExpectedClosing(new BigDecimal("20000"));
            reg.setActualClosing(new BigDecimal("19500"));
            reg.setDiscrepancy(new BigDecimal("-500"));

            when(registerRepository.findByRestaurantIdAndStatusOrderByClosedAtDesc(RESTAURANT_ID, CashRegisterStatus.CLOSED))
                    .thenReturn(List.of(reg));
            when(paymentRepository.sumAmountReceivedByRegisterSession(10L)).thenReturn(new BigDecimal("15000"));
            when(paymentRepository.sumTipByRegisterSession(10L)).thenReturn(new BigDecimal("1000"));
            when(paymentRepository.sumAmountReturnedByRegisterSession(10L)).thenReturn(new BigDecimal("500"));
            when(movementRepository.sumCashInByRegisterSession(10L)).thenReturn(new BigDecimal("2000"));
            when(movementRepository.sumCashOutByRegisterSession(10L)).thenReturn(new BigDecimal("1000"));
            when(paymentRepository.countByCashRegisterSessionId(10L)).thenReturn(5L);
            when(movementRepository.findByCashRegisterSessionIdOrderByCreatedAtAsc(10L)).thenReturn(Collections.emptyList());

            List<CashRegisterDto> result = service.getRegisterHistory(RESTAURANT_ID);

            assertThat(result).hasSize(1);
            CashRegisterDto dto = result.get(0);
            assertThat(dto.totalReceived()).isEqualByComparingTo("15000");
            assertThat(dto.totalTips()).isEqualByComparingTo("1000");
            assertThat(dto.totalReturned()).isEqualByComparingTo("500");
            assertThat(dto.totalCashIn()).isEqualByComparingTo("2000");
            assertThat(dto.totalCashOut()).isEqualByComparingTo("1000");
            assertThat(dto.paymentCount()).isEqualTo(5);
        }
    }
}
