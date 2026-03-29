package com.restaurantqr.table;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.BillCalculationService;
import com.restaurantqr.finance.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;


@Service
public class StaffTableSessionService {

    private static final Logger log = LoggerFactory.getLogger(StaffTableSessionService.class);

    private static final int MAX_GENERATION_ATTEMPTS = 30;

    private final RestaurantTableRepository tableRepository;
    private final TableSessionRepository sessionRepository;
    private final BillCalculationService billCalculationService;
    private final InvoiceService invoiceService;
    private final SecureRandom random = new SecureRandom();

    public StaffTableSessionService(RestaurantTableRepository tableRepository,
                                    TableSessionRepository sessionRepository,
                                    BillCalculationService billCalculationService,
                                    InvoiceService invoiceService) {
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
        this.billCalculationService = billCalculationService;
        this.invoiceService = invoiceService;
    }

    @Transactional(readOnly = true)
    public List<StaffTableDto> listTables(Long restaurantId) {
        if (restaurantId == null) {
            throw new BusinessRuleException("restaurantId is required");
        }

        log.debug("List tables | restaurantId={}", restaurantId);

        return tableRepository.findByRestaurantIdOrderByTableNumberAsc(restaurantId)
                .stream()
                .map(t -> new StaffTableDto(
                        t.getId(),
                        t.getTableNumber(),
                        t.isActive(),
                        t.getQrToken(),
                        t.getSessionCode(),
                        t.getSessionOpenedAt()
                ))
                .toList();
    }

    @Transactional
    public OpenSessionResponse openSession(Long restaurantId, Long tableId) {
        if (restaurantId == null || tableId == null) {
            throw new BusinessRuleException("restaurantId and tableId are required");
        }

        log.info("Open session request | restaurantId={} | tableId={}", restaurantId, tableId);

        // ✅ Lock the table row to make openSession idempotent per table under concurrency
        RestaurantTable table = tableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));

        // ✅ Restaurant boundary
        if (table.getRestaurant() == null || !table.getRestaurant().getId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Table not found: " + tableId);
        }

        // ✅ Idempotent: if already open, return existing code
        if (table.getSessionCode() != null) {
            log.info("Session already open (idempotent) | tableId={} | code={}", tableId, table.getSessionCode());
            return new OpenSessionResponse(
                    table.getId(),
                    table.getTableNumber(),
                    table.getSessionCode(),
                    table.getSessionOpenedAt()
            );
        }

        // ✅ Try generate+persist code; handle UNIQUE collisions safely
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = generate4Digits();

            Instant now = Instant.now();
            table.setSessionCode(code);
            table.setSessionOpenedAt(now);

            try {
                // save+flush ensures DB constraint is checked NOW (inside this method)
                tableRepository.saveAndFlush(table);

                // ✅ Persist session history record
                TableSession session = new TableSession(
                        table.getRestaurant(), table, code, now
                );
                sessionRepository.save(session);

                log.info("Session opened | tableId={} | attempt={} | code={}", tableId, attempt, code);

                return new OpenSessionResponse(
                        table.getId(),
                        table.getTableNumber(),
                        table.getSessionCode(),
                        table.getSessionOpenedAt()
                );

            } catch (DataIntegrityViolationException ex) {
                // Likely UNIQUE(restaurant_id, session_code) collision
                log.warn("Session code collision | restaurantId={} | tableId={} | attempt={} | code={}",
                        restaurantId, tableId, attempt, code);

                // reset in-memory values so next attempt is clean
                table.setSessionCode(null);
                table.setSessionOpenedAt(null);

                // continue retry
            }
        }

        throw new BusinessRuleException("Unable to generate session code. Please retry.");
    }

    @Transactional
    public CloseSessionResponse closeSession(Long restaurantId, Long tableId) {
        if (restaurantId == null || tableId == null) {
            throw new BusinessRuleException("restaurantId and tableId are required");
        }

        log.info("Close session request | restaurantId={} | tableId={}", restaurantId, tableId);

        // ✅ Lock row to avoid concurrent open/close races
        RestaurantTable table = tableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));

        // ✅ Restaurant boundary
        if (table.getRestaurant() == null || !table.getRestaurant().getId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Table not found: " + tableId);
        }

        // ✅ Idempotent: already closed
        if (table.getSessionCode() == null) {
            log.info("Session already closed (idempotent) | tableId={}", tableId);
            return new CloseSessionResponse(table.getId(), true);
        }

        // ✅ Persist session history before clearing live fields
        Instant now = Instant.now();
        sessionRepository.findOpenSessionByTableId(tableId).ifPresent(session -> {
            session.close(now);
            sessionRepository.save(session);
            log.debug("Session history closed | tableId={} | sessionId={} | durationMinutes={}",
                    tableId, session.getId(), session.getDurationMinutes());

            // ✅ Finalize the session bill (snapshot financial rules + compute totals)
            try {
                billCalculationService.finalizeBill(session.getId());
                log.info("Session bill finalized | tableId={} | sessionId={}", tableId, session.getId());
            } catch (Exception e) {
                // Bill finalization failure should not block session close
                log.error("Failed to finalize bill for session {} | tableId={} | error={}",
                        session.getId(), tableId, e.getMessage(), e);
            }

            // ✅ Generate invoice (snapshot all session data for formal document)
            try {
                invoiceService.generateInvoice(session.getId());
                log.info("Invoice generated | tableId={} | sessionId={}", tableId, session.getId());
            } catch (Exception e) {
                // Invoice generation failure should not block session close
                log.error("Failed to generate invoice for session {} | tableId={} | error={}",
                        session.getId(), tableId, e.getMessage(), e);
            }
        });

        // ✅ Clear live session fields
        table.setSessionCode(null);
        table.setSessionOpenedAt(null);

        // flush now (optional but nice for deterministic behavior/logging)
        tableRepository.saveAndFlush(table);

        log.info("Session closed | tableId={}", tableId);

        return new CloseSessionResponse(table.getId(), true);
    }

    private String generate4Digits() {
        int num = random.nextInt(10_000); // 0..9999
        return String.format("%04d", num);
    }
}

