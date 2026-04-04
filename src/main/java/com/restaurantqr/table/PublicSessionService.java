package com.restaurantqr.table;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.InvalidSessionCodeException;
import com.restaurantqr.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PublicSessionService {

    private static final Logger log = LoggerFactory.getLogger(PublicSessionService.class);

    private final RestaurantTableRepository tableRepository;

    public PublicSessionService(RestaurantTableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    /**
     * Verifies the session code for a given QR token.
     * NOTE: No @Transactional annotation to avoid holding DB connections
     * during long-lived SSE connections that call this method.
     */
    public void verifySessionOrThrow(String qrToken, String code) {
        if (qrToken == null || qrToken.isBlank()) {
            throw new BusinessRuleException("qrToken is required");
        }
        if (code == null || code.isBlank()) {
            throw new InvalidSessionCodeException("Invalid session code");
        }

        RestaurantTable table = tableRepository.findActiveByQrTokenFetchRestaurant(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found"));

        if (!table.isActive() || table.getRestaurant() == null || !table.getRestaurant().isActive()) {
            throw new ResourceNotFoundException("Table not found");
        }

        String current = table.getSessionCode();
        if (current == null) {
            throw new InvalidSessionCodeException("Session is not open for this table");
        }

        if (!current.equals(code)) {
            throw new InvalidSessionCodeException("Invalid session code");
        }

        log.debug("Session verified | tableId={} | qrToken={}", table.getId(), qrToken);
    }
}

