package com.restaurantqr.finance;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.CashRegisterDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CashRegisterOwnerController Unit Tests")
class CashRegisterOwnerControllerTest {

    @Mock private CashRegisterService cashRegisterService;
    @Mock private Authentication authentication;

    private CashRegisterOwnerController controller;

    private static final Long RESTAURANT_ID = 1L;

    @BeforeEach
    void setUp() {
        controller = new CashRegisterOwnerController(cashRegisterService);
        AuthPrincipal principal = new AuthPrincipal(10L, "OWNER", RESTAURANT_ID);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    private CashRegisterDto sampleDto(Long id) {
        return new CashRegisterDto(
                id, "CLOSED",
                Instant.now().minusSeconds(3600), Instant.now(),
                "staff1", "staff1",
                new BigDecimal("10000"),
                new BigDecimal("20000"), new BigDecimal("1500"), new BigDecimal("3000"),
                new BigDecimal("1000"), new BigDecimal("500"),
                new BigDecimal("25000"), new BigDecimal("24500"), new BigDecimal("500"),
                "SHORTAGE",
                5L, null,
                List.of()
        );
    }

    // ==================== getHistory ====================

    @Nested
    @DisplayName("Get History")
    class GetHistoryTests {

        @Test
        @DisplayName("Should return 200 with register history list")
        void shouldReturn200WithHistory() {
            List<CashRegisterDto> history = List.of(sampleDto(1L), sampleDto(2L));
            when(cashRegisterService.getRegisterHistory(RESTAURANT_ID)).thenReturn(history);

            ResponseEntity<List<CashRegisterDto>> response = controller.getHistory(authentication);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("Should return 200 with empty list when no history")
        void shouldReturn200EmptyWhenNoHistory() {
            when(cashRegisterService.getRegisterHistory(RESTAURANT_ID)).thenReturn(List.of());

            ResponseEntity<List<CashRegisterDto>> response = controller.getHistory(authentication);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("Should delegate to service with correct restaurantId")
        void shouldDelegateToService() {
            when(cashRegisterService.getRegisterHistory(RESTAURANT_ID)).thenReturn(List.of());

            controller.getHistory(authentication);

            verify(cashRegisterService).getRegisterHistory(RESTAURANT_ID);
        }
    }

    // ==================== getRegisterById ====================

    @Nested
    @DisplayName("Get Register By ID")
    class GetRegisterByIdTests {

        @Test
        @DisplayName("Should return 200 with register details")
        void shouldReturn200WithDetails() {
            CashRegisterDto dto = sampleDto(5L);
            when(cashRegisterService.getRegisterById(RESTAURANT_ID, 5L)).thenReturn(dto);

            ResponseEntity<CashRegisterDto> response = controller.getRegisterById(authentication, 5L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(5L);
        }

        @Test
        @DisplayName("Should propagate ResourceNotFoundException from service")
        void shouldPropagateNotFound() {
            when(cashRegisterService.getRegisterById(RESTAURANT_ID, 999L))
                    .thenThrow(new ResourceNotFoundException("Not found"));

            assertThatThrownBy(() -> controller.getRegisterById(authentication, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Auth Edge Cases ====================

    @Nested
    @DisplayName("Auth Extraction")
    class AuthExtractionTests {

        @Test
        @DisplayName("Should throw when authentication is null")
        void shouldThrowWhenAuthNull() {
            assertThatThrownBy(() -> controller.getHistory(null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}



