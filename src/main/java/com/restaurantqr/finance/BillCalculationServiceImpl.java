package com.restaurantqr.finance;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.BillChargeDto;
import com.restaurantqr.finance.dto.BillItemDto;
import com.restaurantqr.finance.dto.BillPreviewDto;
import com.restaurantqr.finance.dto.SessionBillDto;
import com.restaurantqr.order.CustomerOrder;
import com.restaurantqr.order.CustomerOrderRepository;
import com.restaurantqr.order.OrderItem;
import com.restaurantqr.order.OrderItemRepository;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.table.TableSession;
import com.restaurantqr.table.TableSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Service
public class BillCalculationServiceImpl implements BillCalculationService {

    private static final Logger log = LoggerFactory.getLogger(BillCalculationServiceImpl.class);

    private final TableSessionRepository sessionRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RestaurantFinancialRuleRepository ruleRepository;
    private final SessionBillRepository billRepository;

    public BillCalculationServiceImpl(TableSessionRepository sessionRepository,
                                      CustomerOrderRepository orderRepository,
                                      OrderItemRepository orderItemRepository,
                                      RestaurantFinancialRuleRepository ruleRepository,
                                      SessionBillRepository billRepository) {
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.ruleRepository = ruleRepository;
        this.billRepository = billRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public SessionBillDto getOrComputeBill(Long tableSessionId) {
        if (tableSessionId == null) {
            throw new BusinessRuleException("tableSessionId is required");
        }

        // Check if a finalized bill already exists
        Optional<SessionBill> existingBill = billRepository.findByTableSessionIdWithCharges(tableSessionId);
        if (existingBill.isPresent() && existingBill.get().isFinalized()) {
            log.debug("Returning finalized bill | sessionId={} | billId={}", tableSessionId, existingBill.get().getId());
            return toDto(existingBill.get());
        }

        // Compute live bill (DRAFT)
        TableSession session = sessionRepository.findById(tableSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + tableSessionId));

        return computeBillDto(session);
    }

    @Override
    @Transactional
    public SessionBill finalizeBill(Long tableSessionId) {
        if (tableSessionId == null) {
            throw new BusinessRuleException("tableSessionId is required");
        }

        // Idempotent: if already finalized, return existing
        Optional<SessionBill> existing = billRepository.findByTableSessionIdWithCharges(tableSessionId);
        if (existing.isPresent() && existing.get().isFinalized()) {
            log.info("Bill already finalized (idempotent) | sessionId={} | billId={}", tableSessionId, existing.get().getId());
            return existing.get();
        }

        TableSession session = sessionRepository.findById(tableSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + tableSessionId));

        Long restaurantId = session.getRestaurant().getId();

        // Calculate items subtotal
        BigDecimal itemsSubtotal = computeItemsSubtotal(session);

        // Load active financial rules
        List<RestaurantFinancialRule> rules = ruleRepository
                .findByRestaurantIdAndIsActiveTrueOrderByApplyOrderAsc(restaurantId);

        // Compute charges
        List<ChargeCalcResult> chargeResults = computeCharges(itemsSubtotal, rules);
        BigDecimal chargesTotal = chargeResults.stream()
                .map(ChargeCalcResult::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal grandTotal = itemsSubtotal.add(chargesTotal).setScale(2, RoundingMode.HALF_UP);

        // Persist or update bill
        SessionBill bill = existing.orElseGet(() -> new SessionBill(session.getRestaurant(), session));

        bill.setItemsSubtotal(itemsSubtotal);
        bill.setChargesTotal(chargesTotal);
        bill.setGrandTotal(grandTotal);
        bill.setStatus(BillStatus.FINALIZED);
        bill.setFinalizedAt(Instant.now());
        bill.setUpdatedAt(Instant.now());

        // Clear old charges and add new snapshots
        bill.getCharges().clear();
        for (ChargeCalcResult cr : chargeResults) {
            SessionBillCharge charge = SessionBillCharge.fromRule(bill, cr.rule(), cr.amount());
            bill.getCharges().add(charge);
        }

        SessionBill saved = billRepository.save(bill);

        log.info("Bill finalized | sessionId={} | billId={} | subtotal={} | charges={} | grand={}",
                tableSessionId, saved.getId(), itemsSubtotal, chargesTotal, grandTotal);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public BillPreviewDto computeBillPreview(Long tableSessionId) {
        if (tableSessionId == null) {
            throw new BusinessRuleException("tableSessionId is required");
        }

        TableSession session = sessionRepository.findById(tableSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + tableSessionId));

        Long restaurantId = session.getRestaurant().getId();

        // Compute subtotal
        BigDecimal itemsSubtotal = computeItemsSubtotal(session);

        // Compute charges
        List<RestaurantFinancialRule> rules = ruleRepository
                .findByRestaurantIdAndIsActiveTrueOrderByApplyOrderAsc(restaurantId);
        List<ChargeCalcResult> chargeResults = computeCharges(itemsSubtotal, rules);

        BigDecimal chargesTotal = chargeResults.stream()
                .map(ChargeCalcResult::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal grandTotal = itemsSubtotal.add(chargesTotal).setScale(2, RoundingMode.HALF_UP);

        // Build charge DTOs
        List<BillChargeDto> chargeDtos = chargeResults.stream()
                .map(cr -> new BillChargeDto(
                        cr.rule().getNameAr(),
                        cr.rule().getNameEn(),
                        cr.rule().getRuleType(),
                        cr.rule().getValue(),
                        cr.rule().isCompound(),
                        cr.amount()
                ))
                .toList();

        // Build item DTOs (aggregated by name + unitPrice)
        List<BillItemDto> itemDtos = computeAggregatedItems(session);

        int tableNumber = session.getTable() != null ? session.getTable().getTableNumber() : 0;
        String sessionCode = session.getSessionCode();

        log.debug("Computed bill preview | sessionId={} | items={} | subtotal={} | charges={} | grand={}",
                session.getId(), itemDtos.size(), itemsSubtotal, chargesTotal, grandTotal);

        return new BillPreviewDto(
                tableNumber,
                sessionCode,
                session.getOpenedAt(),
                itemsSubtotal,
                chargesTotal,
                grandTotal,
                "SYP",
                itemDtos,
                chargeDtos
        );
    }

    // ==================== Private computation logic ====================

    /**
     * Compute a live (DRAFT) bill DTO without persisting.
     */
    private SessionBillDto computeBillDto(TableSession session) {
        Long restaurantId = session.getRestaurant().getId();

        BigDecimal itemsSubtotal = computeItemsSubtotal(session);

        List<RestaurantFinancialRule> rules = ruleRepository
                .findByRestaurantIdAndIsActiveTrueOrderByApplyOrderAsc(restaurantId);

        List<ChargeCalcResult> chargeResults = computeCharges(itemsSubtotal, rules);
        BigDecimal chargesTotal = chargeResults.stream()
                .map(ChargeCalcResult::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal grandTotal = itemsSubtotal.add(chargesTotal).setScale(2, RoundingMode.HALF_UP);

        List<BillChargeDto> chargeDtos = chargeResults.stream()
                .map(cr -> new BillChargeDto(
                        cr.rule().getNameAr(),
                        cr.rule().getNameEn(),
                        cr.rule().getRuleType(),
                        cr.rule().getValue(),
                        cr.rule().isCompound(),
                        cr.amount()
                ))
                .toList();

        int tableNumber = session.getTable() != null ? session.getTable().getTableNumber() : 0;
        String sessionCode = session.getSessionCode();

        log.debug("Computed DRAFT bill | sessionId={} | subtotal={} | charges={} | grand={}",
                session.getId(), itemsSubtotal, chargesTotal, grandTotal);

        return new SessionBillDto(
                null, // no persisted bill ID for DRAFT
                tableNumber,
                sessionCode,
                itemsSubtotal,
                chargesTotal,
                grandTotal,
                "SYP",
                BillStatus.DRAFT,
                chargeDtos,
                null
        );
    }

    /**
     * Sum total_amount of all non-cancelled orders in the session.
     */
    private BigDecimal computeItemsSubtotal(TableSession session) {
        // Use the explicit FK if available, else fall back to timestamp
        List<CustomerOrder> orders = orderRepository.findByTableSessionIdAndStatusNot(
                session.getId(), OrderStatus.CANCELLED
        );

        if (orders.isEmpty()) {
            // Fallback: timestamp-based lookup (for pre-migration orders)
            if (session.getOpenedAt() != null) {
                Instant end = session.getClosedAt() != null ? session.getClosedAt() : Instant.now();
                orders = orderRepository.findByTableIdAndCreatedAtBetweenAndStatusNot(
                        session.getTable().getId(),
                        session.getOpenedAt(),
                        end,
                        OrderStatus.CANCELLED
                );
            }
        }

        return orders.stream()
                .map(CustomerOrder::getTotalAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Apply financial rules to the items subtotal in order.
     * Implements the calculation pipeline from the plan (Section 7).
     */
    private List<ChargeCalcResult> computeCharges(BigDecimal itemsSubtotal, List<RestaurantFinancialRule> rules) {
        List<ChargeCalcResult> results = new ArrayList<>();
        BigDecimal runningCharges = BigDecimal.ZERO;

        for (RestaurantFinancialRule rule : rules) {
            BigDecimal chargeAmount;

            if (rule.getRuleType() == RuleType.FIXED) {
                chargeAmount = rule.getValue().setScale(2, RoundingMode.HALF_UP);
            } else {
                // PERCENTAGE
                BigDecimal base;
                if (rule.isCompound()) {
                    base = itemsSubtotal.add(runningCharges);
                } else {
                    base = itemsSubtotal;
                }
                chargeAmount = base
                        .multiply(rule.getValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }

            runningCharges = runningCharges.add(chargeAmount);
            results.add(new ChargeCalcResult(rule, chargeAmount));
        }

        return results;
    }

    private SessionBillDto toDto(SessionBill bill) {
        List<BillChargeDto> chargeDtos = bill.getCharges().stream()
                .map(c -> new BillChargeDto(
                        c.getRuleNameAr(),
                        c.getRuleNameEn(),
                        c.getRuleType(),
                        c.getRuleValue(),
                        c.isCompound(),
                        c.getComputedAmount()
                ))
                .toList();

        int tableNumber = 0;
        String sessionCode = null;
        if (bill.getTableSession() != null) {
            if (bill.getTableSession().getTable() != null) {
                tableNumber = bill.getTableSession().getTable().getTableNumber();
            }
            sessionCode = bill.getTableSession().getSessionCode();
        }

        return new SessionBillDto(
                bill.getId(),
                tableNumber,
                sessionCode,
                bill.getItemsSubtotal(),
                bill.getChargesTotal(),
                bill.getGrandTotal(),
                bill.getCurrency(),
                bill.getStatus(),
                chargeDtos,
                bill.getFinalizedAt()
        );
    }

    /**
     * Aggregate order items by (itemName, unitPrice) for bill preview.
     */
    private List<BillItemDto> computeAggregatedItems(TableSession session) {
        List<CustomerOrder> orders = orderRepository.findByTableSessionIdAndStatusNot(
                session.getId(), OrderStatus.CANCELLED
        );

        if (orders.isEmpty()) {
            // Fallback: timestamp-based lookup (for pre-migration orders)
            if (session.getOpenedAt() != null) {
                Instant end = session.getClosedAt() != null ? session.getClosedAt() : Instant.now();
                orders = orderRepository.findByTableIdAndCreatedAtBetweenAndStatusNot(
                        session.getTable().getId(),
                        session.getOpenedAt(),
                        end,
                        OrderStatus.CANCELLED
                );
            }
        }

        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(CustomerOrder::getId).toList();
        List<OrderItem> allItems = orderItemRepository.findByOrderIdInWithMenuItem(orderIds);

        // Aggregate by (itemName, unitPrice)
        record ItemKey(String name, BigDecimal unitPrice) {}

        LinkedHashMap<ItemKey, Integer> aggregated = new LinkedHashMap<>();
        for (OrderItem oi : allItems) {
            String name = oi.getMenuItem().getName();
            BigDecimal price = oi.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
            ItemKey key = new ItemKey(name, price);
            aggregated.merge(key, oi.getQuantity(), Integer::sum);
        }

        return aggregated.entrySet().stream()
                .map(entry -> {
                    ItemKey key = entry.getKey();
                    int qty = entry.getValue();
                    BigDecimal lineTotal = key.unitPrice()
                            .multiply(BigDecimal.valueOf(qty))
                            .setScale(2, RoundingMode.HALF_UP);
                    return new BillItemDto(key.name(), qty, key.unitPrice(), lineTotal);
                })
                .toList();
    }

    /**
     * Internal record to hold rule + computed amount pair.
     */
    private record ChargeCalcResult(RestaurantFinancialRule rule, BigDecimal amount) {}
}

