package com.restaurantqr.table;

import com.restaurantqr.exception.InvalidSessionCodeException;
import com.restaurantqr.exception.ResourceNotFoundException;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicSessionService {

    private static final Logger log = LoggerFactory.getLogger(PublicSessionService.class);

    private final RestaurantTableRepository tableRepository;

    public PublicSessionService(RestaurantTableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    @Transactional(readOnly = true)
    public void verifySessionOrThrow(String qrToken, String code) {
        RestaurantTable table = tableRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found"));

        if (!table.isActive() || !table.getRestaurant().isActive()) {
            throw new ResourceNotFoundException("Table not found");
        }

        String current = table.getSessionCode();
        if (current == null) {
            throw new InvalidSessionCodeException("Session is not open for this table");
        }

        if (!current.equals(code)) {
            throw new InvalidSessionCodeException("Invalid session code");
        }

        log.info("Session verified. tableId={}, qrToken={}", table.getId(), qrToken);
    }
}

