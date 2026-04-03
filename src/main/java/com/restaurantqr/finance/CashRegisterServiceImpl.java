package com.restaurantqr.finance;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.CashMovementDto;
import com.restaurantqr.finance.dto.CashRegisterDto;
import com.restaurantqr.finance.dto.CashRegisterPageResponse;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import com.restaurantqr.user.User;
import com.restaurantqr.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class CashRegisterServiceImpl implements CashRegisterService {

    private static final Logger log = LoggerFactory.getLogger(CashRegisterServiceImpl.class);

    private final CashRegisterSessionRepository registerRepository;
    private final CashMovementRepository movementRepository;
    private final SessionPaymentRepository paymentRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    public CashRegisterServiceImpl(CashRegisterSessionRepository registerRepository,
                                   CashMovementRepository movementRepository,
                                   SessionPaymentRepository paymentRepository,
                                   RestaurantRepository restaurantRepository,
                                   UserRepository userRepository) {
        this.registerRepository = registerRepository;
        this.movementRepository = movementRepository;
        this.paymentRepository = paymentRepository;
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public CashRegisterDto openRegister(Long restaurantId, Long userId, BigDecimal openingBalance) {
        if (openingBalance == null || openingBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("رصيد الافتتاح يجب أن يكون 0 أو أكثر");
        }

        // Check no existing OPEN register
        if (registerRepository.existsByRestaurantIdAndStatus(restaurantId, CashRegisterStatus.OPEN)) {
            throw new BusinessRuleException("يوجد صندوق مفتوح بالفعل. يجب إغلاقه أولاً");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Instant now = Instant.now();
        CashRegisterSession session = new CashRegisterSession(restaurant, user, openingBalance, now);
        registerRepository.save(session);

        log.info("Cash register opened | restaurantId={} | userId={} | openingBalance={}", restaurantId, userId, openingBalance);

        return toDto(session, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, Collections.emptyList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashRegisterDto> getCurrentRegister(Long restaurantId) {
        return registerRepository.findByRestaurantIdAndStatus(restaurantId, CashRegisterStatus.OPEN)
                .map(this::toDtoWithTotals);
    }

    @Override
    @Transactional
    public CashMovementDto recordMovement(Long restaurantId, Long userId, String type, BigDecimal amount, String reason) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("المبلغ يجب أن يكون أكبر من صفر");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("يجب تحديد سبب الحركة");
        }

        CashMovementType movementType;
        try {
            movementType = CashMovementType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("نوع الحركة غير صالح. استخدم CASH_IN أو CASH_OUT");
        }

        CashRegisterSession register = registerRepository.findByRestaurantIdAndStatus(restaurantId, CashRegisterStatus.OPEN)
                .orElseThrow(() -> new BusinessRuleException("لا يوجد صندوق مفتوح حالياً"));

        Restaurant restaurant = register.getRestaurant();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        CashMovement movement = new CashMovement(register, restaurant, movementType, amount, reason, user);
        movementRepository.save(movement);

        log.info("Cash movement recorded | type={} | amount={} | reason={} | registerId={} | userId={}",
                type, amount, reason, register.getId(), userId);

        return toMovementDto(movement);
    }

    @Override
    @Transactional
    public CashRegisterDto closeRegister(Long restaurantId, Long userId, BigDecimal actualClosing, String notes) {
        if (actualClosing == null || actualClosing.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("المبلغ الفعلي يجب أن يكون 0 أو أكثر");
        }

        CashRegisterSession register = registerRepository.findByRestaurantIdAndStatus(restaurantId, CashRegisterStatus.OPEN)
                .orElseThrow(() -> new BusinessRuleException("لا يوجد صندوق مفتوح حالياً"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Compute expected closing
        BigDecimal totalReceived = paymentRepository.sumAmountReceivedByRegisterSession(register.getId());
        BigDecimal totalTips = paymentRepository.sumTipByRegisterSession(register.getId());
        BigDecimal totalReturned = paymentRepository.sumAmountReturnedByRegisterSession(register.getId());
        BigDecimal totalCashIn = movementRepository.sumCashInByRegisterSession(register.getId());
        BigDecimal totalCashOut = movementRepository.sumCashOutByRegisterSession(register.getId());

        // expected = opening + received - returned + cashIn - cashOut
        // NOTE: tip is NOT added separately because it is already included
        // in (received - returned). amountReturned = received - grandTotal - tip,
        // so received - returned = grandTotal + tip. Adding tip again would double-count.
        BigDecimal expectedClosing = register.getOpeningBalance()
                .add(totalReceived)
                .subtract(totalReturned)
                .add(totalCashIn)
                .subtract(totalCashOut);

        BigDecimal discrepancy = actualClosing.subtract(expectedClosing);

        Instant now = Instant.now();
        register.setClosedBy(user);
        register.setClosedAt(now);
        register.setExpectedClosing(expectedClosing);
        register.setActualClosing(actualClosing);
        register.setDiscrepancy(discrepancy);
        register.setNotes(notes);
        register.setStatus(CashRegisterStatus.CLOSED);
        register.setUpdatedAt(now);
        registerRepository.save(register);

        long paymentCount = paymentRepository.countByCashRegisterSessionId(register.getId());
        List<CashMovementDto> movements = movementRepository.findByCashRegisterSessionIdOrderByCreatedAtAsc(register.getId())
                .stream().map(this::toMovementDto).toList();

        log.info("Cash register closed | registerId={} | expected={} | actual={} | discrepancy={} | userId={}",
                register.getId(), expectedClosing, actualClosing, discrepancy, userId);

        return toDto(register, totalReceived, totalTips, totalReturned, totalCashIn, totalCashOut, paymentCount, movements);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashRegisterDto> getRegisterHistory(Long restaurantId) {
        return registerRepository.findByRestaurantIdAndStatusOrderByClosedAtDesc(restaurantId, CashRegisterStatus.CLOSED)
                .stream()
                .map(this::toDtoWithTotals)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegisterPageResponse getRegisterHistoryPaged(Long restaurantId, int page, int size) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by("closedAt").descending()
        );
        Page<CashRegisterDto> dtoPage = registerRepository
                .findByRestaurantIdAndStatus(restaurantId, CashRegisterStatus.CLOSED, pageable)
                .map(this::toDtoWithTotals);
        return CashRegisterPageResponse.from(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegisterDto getRegisterById(Long restaurantId, Long registerSessionId) {
        CashRegisterSession register = registerRepository.findById(registerSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("سجل الصندوق غير موجود"));

        if (!register.getRestaurant().getId().equals(restaurantId)) {
            throw new ResourceNotFoundException("سجل الصندوق غير موجود");
        }

        return toDtoWithTotals(register);
    }

    // ==================== Private helpers ====================

    private CashRegisterDto toDtoWithTotals(CashRegisterSession register) {
        BigDecimal totalReceived = paymentRepository.sumAmountReceivedByRegisterSession(register.getId());
        BigDecimal totalTips = paymentRepository.sumTipByRegisterSession(register.getId());
        BigDecimal totalReturned = paymentRepository.sumAmountReturnedByRegisterSession(register.getId());
        BigDecimal totalCashIn = movementRepository.sumCashInByRegisterSession(register.getId());
        BigDecimal totalCashOut = movementRepository.sumCashOutByRegisterSession(register.getId());
        long paymentCount = paymentRepository.countByCashRegisterSessionId(register.getId());

        List<CashMovementDto> movements = movementRepository.findByCashRegisterSessionIdOrderByCreatedAtAsc(register.getId())
                .stream().map(this::toMovementDto).toList();

        // If still OPEN, compute live expected
        // Tip is NOT added because it's already included in (received - returned)
        if (register.isOpen()) {
            BigDecimal expectedClosing = register.getOpeningBalance()
                    .add(totalReceived)
                    .subtract(totalReturned)
                    .add(totalCashIn)
                    .subtract(totalCashOut);
            return toDto(register, totalReceived, totalTips, totalReturned, totalCashIn, totalCashOut, paymentCount, movements,
                    expectedClosing, null, null);
        }

        return toDto(register, totalReceived, totalTips, totalReturned, totalCashIn, totalCashOut, paymentCount, movements);
    }

    private CashRegisterDto toDto(CashRegisterSession r,
                                   BigDecimal totalReceived, BigDecimal totalTips, BigDecimal totalReturned,
                                   BigDecimal totalCashIn, BigDecimal totalCashOut,
                                   long paymentCount, List<CashMovementDto> movements) {
        return toDto(r, totalReceived, totalTips, totalReturned, totalCashIn, totalCashOut,
                paymentCount, movements, r.getExpectedClosing(), r.getActualClosing(), r.getDiscrepancy());
    }

    private CashRegisterDto toDto(CashRegisterSession r,
                                   BigDecimal totalReceived, BigDecimal totalTips, BigDecimal totalReturned,
                                   BigDecimal totalCashIn, BigDecimal totalCashOut,
                                   long paymentCount, List<CashMovementDto> movements,
                                   BigDecimal expectedClosing, BigDecimal actualClosing, BigDecimal discrepancy) {
        String discrepancyType = null;
        if (discrepancy != null) {
            int cmp = discrepancy.compareTo(BigDecimal.ZERO);
            discrepancyType = cmp > 0 ? "OVERAGE" : cmp < 0 ? "SHORTAGE" : "MATCH";
        }

        return new CashRegisterDto(
                r.getId(),
                r.getStatus().name(),
                r.getOpenedAt(),
                r.getClosedAt(),
                r.getOpenedBy() != null ? r.getOpenedBy().getUsername() : null,
                r.getClosedBy() != null ? r.getClosedBy().getUsername() : null,
                r.getOpeningBalance(),
                totalReceived,
                totalTips,
                totalReturned,
                totalCashIn,
                totalCashOut,
                expectedClosing,
                actualClosing,
                discrepancy,
                discrepancyType,
                paymentCount,
                r.getNotes(),
                movements
        );
    }

    private CashMovementDto toMovementDto(CashMovement m) {
        return new CashMovementDto(
                m.getId(),
                m.getType().name(),
                m.getAmount(),
                m.getReason(),
                m.getRecordedBy() != null ? m.getRecordedBy().getUsername() : null,
                m.getCreatedAt()
        );
    }
}



