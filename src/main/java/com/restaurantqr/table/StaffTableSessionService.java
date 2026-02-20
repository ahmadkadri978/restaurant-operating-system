package com.restaurantqr.table;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Service
public class StaffTableSessionService {

    private static final Logger log = LoggerFactory.getLogger(StaffTableSessionService.class);

    private final RestaurantTableRepository tableRepository;

    private final SecureRandom random = new SecureRandom();

    public StaffTableSessionService(RestaurantTableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    @Transactional(readOnly = true)
    public List<StaffTableDto> listTables(Long restaurantId) {
        log.info("List tables for staff. restaurantId={}", restaurantId);

        return tableRepository.findByRestaurantIdOrderByTableNumberAsc(restaurantId)
                .stream()
                .map(t -> new StaffTableDto(
                        t.getId(),
                        t.getTableNumber(),
                        t.isActive(),
                        t.getSessionCode(),
                        t.getSessionOpenedAt()
                ))
                .toList();
    }

    @Transactional
    public OpenSessionResponse openSession(Long restaurantId, Long tableId) {
        log.info("Open session. restaurantId={}, tableId={}", restaurantId, tableId);

        RestaurantTable table = tableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));

        // restaurant boundary
        if (!table.getRestaurant().getId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Table not found: " + tableId);
        }

        // if already open => idempotent
        if (table.getSessionCode() != null) {
            log.info("Session already open (idempotent). tableId={}, code={}", tableId, table.getSessionCode());
            return new OpenSessionResponse(table.getId(), table.getTableNumber(), table.getSessionCode(), table.getSessionOpenedAt());
        }

        String code = generateUniqueCodeForRestaurant(restaurantId);

        table.setSessionCode(code);
        table.setSessionOpenedAt(Instant.now());

        // managed entity, save optional
        tableRepository.save(table);

        return new OpenSessionResponse(table.getId(), table.getTableNumber(), table.getSessionCode(), table.getSessionOpenedAt());
    }

    @Transactional
    public CloseSessionResponse closeSession(Long restaurantId, Long tableId) {
        log.info("Close session. restaurantId={}, tableId={}", restaurantId, tableId);

        RestaurantTable table = tableRepository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));

        if (!table.getRestaurant().getId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Table not found: " + tableId);
        }

        table.setSessionCode(null);
        table.setSessionOpenedAt(null);
        tableRepository.save(table);

        return new CloseSessionResponse(table.getId(), true);
    }

    private String generateUniqueCodeForRestaurant(Long restaurantId) {
        // 10 tries is enough for small restaurants
        for (int i = 0; i < 10; i++) {
            int num = random.nextInt(10_000); // 0..9999
            String code = String.format("%04d", num);

            if (!tableRepository.existsByRestaurantIdAndSessionCode(restaurantId, code)) {
                return code;
            }
        }
        throw new BusinessRuleException("Unable to generate session code. Please retry.");
    }
}

