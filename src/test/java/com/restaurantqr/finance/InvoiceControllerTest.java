package com.restaurantqr.finance;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.InvoiceListDto;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InvoiceController Unit Tests")
class InvoiceControllerTest {

    @Mock private InvoiceService invoiceService;
    @Mock private Authentication authentication;

    private InvoiceController controller;

    private static final Long RESTAURANT_ID = 1L;

    @BeforeEach
    void setUp() {
        controller = new InvoiceController(invoiceService);
        AuthPrincipal principal = new AuthPrincipal(10L, "MANAGER", RESTAURANT_ID);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    // ==================== List Invoices ====================

    @Nested
    @DisplayName("List Invoices")
    class ListInvoicesTests {

        @Test
        @DisplayName("Should list invoices by date")
        void shouldListByDate() {
            LocalDate date = LocalDate.of(2026, 3, 29);
            InvoiceListDto dto = buildInvoiceDto();
            when(invoiceService.listByDate(RESTAURANT_ID, date, "Asia/Damascus"))
                    .thenReturn(List.of(dto));

            ResponseEntity<List<InvoiceListDto>> response =
                    controller.listInvoices(authentication, date, null, "Asia/Damascus");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).invoiceNumber()).isEqualTo("INV-20260329-001");
        }

        @Test
        @DisplayName("Should search by invoice number when provided")
        void shouldSearchByInvoiceNumber() {
            InvoiceListDto dto = buildInvoiceDto();
            when(invoiceService.searchByNumber(RESTAURANT_ID, "INV-20260329-001"))
                    .thenReturn(List.of(dto));

            ResponseEntity<List<InvoiceListDto>> response =
                    controller.listInvoices(authentication, null, "INV-20260329-001", "Asia/Damascus");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
            verify(invoiceService).searchByNumber(RESTAURANT_ID, "INV-20260329-001");
            verify(invoiceService, never()).listByDate(any(), any(), any());
        }

        @Test
        @DisplayName("Should prioritize invoice number over date")
        void shouldPrioritizeInvoiceNumber() {
            when(invoiceService.searchByNumber(RESTAURANT_ID, "INV-001"))
                    .thenReturn(List.of());

            controller.listInvoices(authentication, LocalDate.now(), "INV-001", "Asia/Damascus");

            verify(invoiceService).searchByNumber(RESTAURANT_ID, "INV-001");
            verify(invoiceService, never()).listByDate(any(), any(), any());
        }

        @Test
        @DisplayName("Should throw when neither date nor invoice number provided")
        void shouldThrowWhenNoParams() {
            assertThatThrownBy(() ->
                    controller.listInvoices(authentication, null, null, "Asia/Damascus"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Either 'date' or 'invoiceNumber' parameter is required");
        }

        @Test
        @DisplayName("Should return empty list when no invoices found")
        void shouldReturnEmptyList() {
            LocalDate date = LocalDate.of(2026, 1, 1);
            when(invoiceService.listByDate(RESTAURANT_ID, date, "Asia/Damascus"))
                    .thenReturn(List.of());

            ResponseEntity<List<InvoiceListDto>> response =
                    controller.listInvoices(authentication, date, null, "Asia/Damascus");

            assertThat(response.getBody()).isEmpty();
        }
    }

    // ==================== Download PDF ====================

    @Nested
    @DisplayName("Download PDF")
    class DownloadPdfTests {

        @Test
        @DisplayName("Should return PDF bytes with correct headers")
        void shouldReturnPdfWithHeaders() {
            byte[] pdfBytes = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF
            when(invoiceService.generatePdf(RESTAURANT_ID, 42L)).thenReturn(pdfBytes);

            ResponseEntity<byte[]> response = controller.downloadPdf(authentication, 42L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo(pdfBytes);
            assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
            assertThat(response.getHeaders().getContentLength()).isEqualTo(4);
            assertThat(response.getHeaders().getFirst("Content-Disposition"))
                    .contains("attachment")
                    .contains("invoice-42.pdf");
        }

        @Test
        @DisplayName("Should propagate exception when invoice not found")
        void shouldPropagateNotFound() {
            when(invoiceService.generatePdf(RESTAURANT_ID, 999L))
                    .thenThrow(new ResourceNotFoundException("Invoice not found"));

            assertThatThrownBy(() -> controller.downloadPdf(authentication, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Auth Extraction ====================

    @Nested
    @DisplayName("Auth Extraction")
    class AuthExtractionTests {

        @Test
        @DisplayName("Should throw when authentication is null")
        void shouldThrowWhenAuthNull() {
            assertThatThrownBy(() ->
                    controller.listInvoices(null, LocalDate.now(), null, "Asia/Damascus"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Should throw when restaurantId is null in principal")
        void shouldThrowWhenNoRestaurantId() {
            Authentication noRestAuth = mock(Authentication.class);
            when(noRestAuth.getPrincipal()).thenReturn(new AuthPrincipal(1L, "MANAGER", null));

            assertThatThrownBy(() ->
                    controller.listInvoices(noRestAuth, LocalDate.now(), null, "Asia/Damascus"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ==================== Helpers ====================

    private InvoiceListDto buildInvoiceDto() {
        return new InvoiceListDto(
                1L, "INV-20260329-001", 3, "1234",
                Instant.now().minusSeconds(3600), Instant.now(),
                new BigDecimal("100.00"), new BigDecimal("10.00"),
                new BigDecimal("110.00"), BigDecimal.ZERO,
                "SYP", Instant.now()
        );
    }
}


