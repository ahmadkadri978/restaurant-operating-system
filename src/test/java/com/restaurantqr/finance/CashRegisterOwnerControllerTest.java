package com.restaurantqr.finance;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.CashRegisterDto;
import com.restaurantqr.finance.dto.CashRegisterPageResponse;
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

    private CashRegisterPageResponse pageOf(List<CashRegisterDto> items, int page, int size, long total) {
        int totalPages = (int) Math.ceil((double) total / size);
        return new CashRegisterPageResponse(items, page, size, total, totalPages, page == 0, page >= totalPages - 1);
    }

    // ==================== getHistory ====================

    @Nested
    @DisplayName("Get History (Paginated)")
    class GetHistoryTests {

        @Test
        @DisplayName("Should return 200 with paginated register history")
        void shouldReturn200WithHistory() {
            List<CashRegisterDto> items = List.of(sampleDto(1L), sampleDto(2L));
            CashRegisterPageResponse page = pageOf(items, 0, 10, 2);
            when(cashRegisterService.getRegisterHistoryPaged(RESTAURANT_ID, 0, 10)).thenReturn(page);

            ResponseEntity<CashRegisterPageResponse> response = controller.getHistory(authentication, 0, 10);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().content()).hasSize(2);
            assertThat(response.getBody().totalElements()).isEqualTo(2);
            assertThat(response.getBody().totalPages()).isEqualTo(1);
            assertThat(response.getBody().first()).isTrue();
            assertThat(response.getBody().last()).isTrue();
        }

        @Test
        @DisplayName("Should return 200 with empty content on page 0 when no history")
        void shouldReturn200EmptyWhenNoHistory() {
            CashRegisterPageResponse emptyPage = pageOf(List.of(), 0, 10, 0);
            when(cashRegisterService.getRegisterHistoryPaged(RESTAURANT_ID, 0, 10)).thenReturn(emptyPage);

            ResponseEntity<CashRegisterPageResponse> response = controller.getHistory(authentication, 0, 10);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().content()).isEmpty();
        }

        @Test
        @DisplayName("Should delegate to service with correct page and size")
        void shouldDelegateToServiceWithPageAndSize() {
            CashRegisterPageResponse page = pageOf(List.of(), 2, 5, 0);
            when(cashRegisterService.getRegisterHistoryPaged(RESTAURANT_ID, 2, 5)).thenReturn(page);

            controller.getHistory(authentication, 2, 5);

            verify(cashRegisterService).getRegisterHistoryPaged(RESTAURANT_ID, 2, 5);
        }

        @Test
        @DisplayName("Should clamp size to MAX_PAGE_SIZE (50) when exceeded")
        void shouldClampSizeToMax() {
            CashRegisterPageResponse page = pageOf(List.of(), 0, 50, 0);
            when(cashRegisterService.getRegisterHistoryPaged(RESTAURANT_ID, 0, 50)).thenReturn(page);

            controller.getHistory(authentication, 0, 999);

            verify(cashRegisterService).getRegisterHistoryPaged(RESTAURANT_ID, 0, 50);
        }

        @Test
        @DisplayName("Should clamp negative page to 0")
        void shouldClampNegativePage() {
            CashRegisterPageResponse page = pageOf(List.of(), 0, 10, 0);
            when(cashRegisterService.getRegisterHistoryPaged(RESTAURANT_ID, 0, 10)).thenReturn(page);

            controller.getHistory(authentication, -5, 10);

            verify(cashRegisterService).getRegisterHistoryPaged(RESTAURANT_ID, 0, 10);
        }

        @Test
        @DisplayName("Should reflect multi-page metadata correctly")
        void shouldReflectMultiPageMetadata() {
            List<CashRegisterDto> items = List.of(sampleDto(1L));
            CashRegisterPageResponse page = new CashRegisterPageResponse(items, 1, 10, 25, 3, false, false);
            when(cashRegisterService.getRegisterHistoryPaged(RESTAURANT_ID, 1, 10)).thenReturn(page);

            ResponseEntity<CashRegisterPageResponse> response = controller.getHistory(authentication, 1, 10);

            assertThat(response.getBody().number()).isEqualTo(1);
            assertThat(response.getBody().totalPages()).isEqualTo(3);
            assertThat(response.getBody().totalElements()).isEqualTo(25);
            assertThat(response.getBody().first()).isFalse();
            assertThat(response.getBody().last()).isFalse();
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
            assertThatThrownBy(() -> controller.getHistory(null, 0, 10))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}


