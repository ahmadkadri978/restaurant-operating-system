package com.restaurantqr.finance;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.finance.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CashRegisterController Unit Tests")
class CashRegisterControllerTest {

    @Mock private CashRegisterService cashRegisterService;
    @Mock private Authentication authentication;

    private CashRegisterController controller;

    private static final Long RESTAURANT_ID = 1L;
    private static final Long USER_ID = 10L;

    @BeforeEach
    void setUp() {
        controller = new CashRegisterController(cashRegisterService);
        AuthPrincipal principal = new AuthPrincipal(USER_ID, "ROLE_MANAGER", RESTAURANT_ID);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    @Test
    @DisplayName("Open register should delegate to service")
    void openRegisterShouldDelegateToService() {
        CashRegisterDto expected = new CashRegisterDto(1L, "OPEN", Instant.now(), null,
                "manager1", null, new BigDecimal("50000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, 0, null, Collections.emptyList());
        when(cashRegisterService.openRegister(eq(RESTAURANT_ID), eq(USER_ID), any())).thenReturn(expected);

        ResponseEntity<CashRegisterDto> response = controller.openRegister(
                authentication, new OpenCashRegisterRequest(new BigDecimal("50000")));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("OPEN");
        verify(cashRegisterService).openRegister(RESTAURANT_ID, USER_ID, new BigDecimal("50000"));
    }

    @Test
    @DisplayName("Get current should return empty body when no register open")
    void getCurrentShouldReturnEmptyWhenNoneOpen() {
        when(cashRegisterService.getCurrentRegister(RESTAURANT_ID)).thenReturn(Optional.empty());

        ResponseEntity<CashRegisterDto> response = controller.getCurrentRegister(authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("Get current should return register when open")
    void getCurrentShouldReturnRegisterWhenOpen() {
        CashRegisterDto dto = new CashRegisterDto(1L, "OPEN", Instant.now(), null,
                "manager1", null, new BigDecimal("50000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000"),
                null, null, null, 0, null, Collections.emptyList());
        when(cashRegisterService.getCurrentRegister(RESTAURANT_ID)).thenReturn(Optional.of(dto));

        ResponseEntity<CashRegisterDto> response = controller.getCurrentRegister(authentication);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Record movement should delegate to service")
    void recordMovementShouldDelegateToService() {
        CashMovementDto expected = new CashMovementDto(1L, "CASH_OUT", new BigDecimal("5000"), "supplies", "manager1", Instant.now());
        when(cashRegisterService.recordMovement(eq(RESTAURANT_ID), eq(USER_ID), eq("CASH_OUT"), any(), eq("supplies")))
                .thenReturn(expected);

        ResponseEntity<CashMovementDto> response = controller.recordMovement(
                authentication, new CashMovementRequest("CASH_OUT", new BigDecimal("5000"), "supplies"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().type()).isEqualTo("CASH_OUT");
        verify(cashRegisterService).recordMovement(RESTAURANT_ID, USER_ID, "CASH_OUT", new BigDecimal("5000"), "supplies");
    }

    @Test
    @DisplayName("Close register should delegate to service")
    void closeRegisterShouldDelegateToService() {
        CashRegisterDto expected = new CashRegisterDto(1L, "CLOSED", Instant.now(), Instant.now(),
                "manager1", "manager1", new BigDecimal("50000"),
                new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("5000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("145000"),
                new BigDecimal("145000"), BigDecimal.ZERO, "MATCH", 5, "notes", Collections.emptyList());
        when(cashRegisterService.closeRegister(eq(RESTAURANT_ID), eq(USER_ID), any(), any()))
                .thenReturn(expected);

        ResponseEntity<CashRegisterDto> response = controller.closeRegister(
                authentication, new CloseCashRegisterRequest(new BigDecimal("145000"), "notes"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().status()).isEqualTo("CLOSED");
        assertThat(response.getBody().discrepancyType()).isEqualTo("MATCH");
    }

    @Test
    @DisplayName("Should throw when authentication is null")
    void shouldThrowWhenAuthNull() {
        assertThatThrownBy(() -> controller.openRegister(null, new OpenCashRegisterRequest(BigDecimal.ZERO)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Should throw when principal has no restaurantId")
    void shouldThrowWhenNoRestaurantId() {
        Authentication noRestAuth = mock(Authentication.class);
        when(noRestAuth.getPrincipal()).thenReturn(new AuthPrincipal(1L, "ROLE_MANAGER", null));

        assertThatThrownBy(() -> controller.openRegister(noRestAuth, new OpenCashRegisterRequest(BigDecimal.ZERO)))
                .isInstanceOf(IllegalStateException.class);
    }
}


