package com.restaurantqr.finance;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.InvoiceListDto;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private static final String DEFAULT_TIMEZONE = "Asia/Damascus";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SessionInvoiceRepository invoiceRepository;
    private final SessionBillRepository billRepository;
    private final TableSessionRepository sessionRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InvoicePdfService pdfService;

    public InvoiceServiceImpl(SessionInvoiceRepository invoiceRepository,
                              SessionBillRepository billRepository,
                              TableSessionRepository sessionRepository,
                              CustomerOrderRepository orderRepository,
                              OrderItemRepository orderItemRepository,
                              InvoicePdfService pdfService) {
        this.invoiceRepository = invoiceRepository;
        this.billRepository = billRepository;
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.pdfService = pdfService;
    }

    @Override
    @Transactional
    public SessionInvoice generateInvoice(Long tableSessionId) {
        if (tableSessionId == null) {
            throw new BusinessRuleException("tableSessionId is required");
        }

        // Idempotent: if invoice already exists, return it
        Optional<SessionInvoice> existing = invoiceRepository.findByTableSessionId(tableSessionId);
        if (existing.isPresent()) {
            log.info("Invoice already exists (idempotent) | sessionId={} | invoiceId={}",
                    tableSessionId, existing.get().getId());
            return existing.get();
        }

        // Load session
        TableSession session = sessionRepository.findById(tableSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + tableSessionId));

        if (session.getClosedAt() == null) {
            throw new BusinessRuleException("Cannot generate invoice for an open session");
        }

        // Load finalized bill
        SessionBill bill = billRepository.findByTableSessionIdWithCharges(tableSessionId)
                .orElseThrow(() -> new BusinessRuleException(
                        "No finalized bill for session: " + tableSessionId));

        if (!bill.isFinalized()) {
            throw new BusinessRuleException("Bill is not finalized for session: " + tableSessionId);
        }

        Long restaurantId = session.getRestaurant().getId();

        // Generate invoice number
        String invoiceNumber = generateInvoiceNumber(restaurantId, Instant.now());

        // Create invoice
        SessionInvoice invoice = new SessionInvoice(
                session.getRestaurant(),
                session,
                bill,
                invoiceNumber,
                session.getTable().getTableNumber(),
                session.getSessionCode(),
                session.getOpenedAt(),
                session.getClosedAt(),
                bill.getItemsSubtotal(),
                bill.getChargesTotal(),
                bill.getGrandTotal(),
                bill.getCurrency()
        );

        // Snapshot order items (aggregated by name + unitPrice)
        List<SessionInvoiceItem> snapshotItems = snapshotOrderItems(invoice, tableSessionId);
        invoice.getItems().addAll(snapshotItems);

        SessionInvoice saved = invoiceRepository.save(invoice);

        log.info("Invoice generated | sessionId={} | invoiceId={} | invoiceNumber={} | grandTotal={}",
                tableSessionId, saved.getId(), invoiceNumber, bill.getGrandTotal());

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceListDto> listByDate(Long restaurantId, LocalDate date, String zone) {
        if (restaurantId == null) {
            throw new BusinessRuleException("restaurantId is required");
        }
        if (date == null) {
            throw new BusinessRuleException("date is required");
        }

        ZoneId zoneId = parseZone(zone);
        Instant dayStart = date.atStartOfDay(zoneId).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<SessionInvoice> invoices = invoiceRepository
                .findByRestaurantIdAndDateRange(restaurantId, dayStart, dayEnd);

        return invoices.stream().map(this::toListDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceListDto> searchByNumber(Long restaurantId, String invoiceNumber) {
        if (restaurantId == null) {
            throw new BusinessRuleException("restaurantId is required");
        }
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new BusinessRuleException("invoiceNumber is required");
        }

        return invoiceRepository.findByRestaurantIdAndInvoiceNumber(restaurantId, invoiceNumber.trim())
                .map(this::toListDto)
                .map(List::of)
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(Long restaurantId, Long invoiceId) {
        if (restaurantId == null || invoiceId == null) {
            throw new BusinessRuleException("restaurantId and invoiceId are required");
        }

        SessionInvoice invoice = invoiceRepository.findByIdAndRestaurantIdWithItems(invoiceId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        // Load bill charges (lazy, within transaction)
        SessionBill bill = invoice.getSessionBill();
        List<SessionBillCharge> charges = bill.getCharges();
        // Force Hibernate lazy-load initialization within this transaction
        //noinspection ResultOfMethodCallIgnored
        charges.size();

        // Load restaurant name for PDF header
        String restaurantName = invoice.getRestaurant().getName();

        log.info("Generating PDF | invoiceId={} | invoiceNumber={}", invoiceId, invoice.getInvoiceNumber());

        return pdfService.render(invoice, charges, restaurantName);
    }

    // ==================== Private helpers ====================

    /**
     * Generate invoice number: INV-YYYYMMDD-SEQ (daily sequence per restaurant).
     */
    private String generateInvoiceNumber(Long restaurantId, Instant now) {
        ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
        LocalDate today = now.atZone(zoneId).toLocalDate();
        String dateStr = today.format(DATE_FMT);

        Instant dayStart = today.atStartOfDay(zoneId).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zoneId).toInstant();

        long count = invoiceRepository.countByRestaurantIdAndDay(restaurantId, dayStart, dayEnd);
        int seq = (int) count + 1;

        return String.format("INV-%s-%03d", dateStr, seq);
    }

    /**
     * Snapshot order items for the session, aggregated by (itemName, unitPrice).
     */
    private List<SessionInvoiceItem> snapshotOrderItems(SessionInvoice invoice, Long tableSessionId) {
        // Load non-cancelled orders for session
        List<CustomerOrder> orders = orderRepository.findByTableSessionIdAndStatusNot(
                tableSessionId, OrderStatus.CANCELLED);

        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(CustomerOrder::getId).toList();
        List<OrderItem> allItems = orderItemRepository.findByOrderIdInWithMenuItem(orderIds);

        // Aggregate by (itemName, unitPrice)
        record ItemKey(String name, BigDecimal unitPrice) {}

        Map<ItemKey, Integer> aggregated = new LinkedHashMap<>();
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
                    return new SessionInvoiceItem(invoice, key.name(), qty, key.unitPrice(), lineTotal);
                })
                .toList();
    }

    private InvoiceListDto toListDto(SessionInvoice inv) {
        return new InvoiceListDto(
                inv.getId(),
                inv.getInvoiceNumber(),
                inv.getTableNumber(),
                inv.getSessionCode(),
                inv.getSessionOpenedAt(),
                inv.getSessionClosedAt(),
                inv.getGrandTotal(),
                inv.getCurrency(),
                inv.getCreatedAt()
        );
    }

    private ZoneId parseZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(zone);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}', falling back to {}", zone, DEFAULT_TIMEZONE);
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }
}



