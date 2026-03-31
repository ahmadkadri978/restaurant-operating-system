package com.restaurantqr.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for InvoicePdfService — validates PDF rendering
 * without Spring context. Exercises the real OpenPDF library.
 */
@DisplayName("InvoicePdfService Unit Tests")
class InvoicePdfServiceTest {

    private InvoicePdfService pdfService;

    private SessionInvoice baseInvoice;

    @BeforeEach
    void setUp() {
        pdfService = new InvoicePdfService();

        baseInvoice = new SessionInvoice();
        baseInvoice.setInvoiceNumber("INV-20260331-001");
        baseInvoice.setTableNumber(3);
        baseInvoice.setSessionCode("5678");
        baseInvoice.setSessionOpenedAt(Instant.now().minusSeconds(3600));
        baseInvoice.setSessionClosedAt(Instant.now());
        baseInvoice.setItemsSubtotal(new BigDecimal("15000.00"));
        baseInvoice.setChargesTotal(new BigDecimal("1500.00"));
        baseInvoice.setGrandTotal(new BigDecimal("16500.00"));
        baseInvoice.setCurrency("SYP");
        baseInvoice.setCreatedAt(Instant.now());

        // Add sample items
        SessionInvoiceItem item1 = new SessionInvoiceItem();
        item1.setItemName("برغر");
        item1.setQuantity(2);
        item1.setUnitPrice(new BigDecimal("5000.00"));
        item1.setLineTotal(new BigDecimal("10000.00"));

        SessionInvoiceItem item2 = new SessionInvoiceItem();
        item2.setItemName("كولا");
        item2.setQuantity(3);
        item2.setUnitPrice(new BigDecimal("1000.00"));
        item2.setLineTotal(new BigDecimal("3000.00"));

        baseInvoice.setItems(List.of(item1, item2));
    }

    @Test
    @DisplayName("Should produce non-empty PDF bytes")
    void shouldProduceNonEmptyPdf() {
        byte[] pdf = pdfService.render(baseInvoice, Collections.emptyList(), "مطعم اختبار", null);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
    }

    @Test
    @DisplayName("Output should start with PDF magic bytes (%PDF-)")
    void shouldStartWithPdfMagicBytes() {
        byte[] pdf = pdfService.render(baseInvoice, Collections.emptyList(), "مطعم اختبار", null);

        // PDF files always start with %PDF-
        String header = new String(pdf, 0, Math.min(5, pdf.length));
        assertThat(header).startsWith("%PDF-");
    }

    @Test
    @DisplayName("Should render with null payment without error")
    void shouldRenderWithNullPayment() {
        assertThatCode(() ->
                pdfService.render(baseInvoice, Collections.emptyList(), "Test Restaurant", null)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should render with payment section included")
    void shouldRenderWithPaymentSection() {
        SessionPayment payment = new SessionPayment();
        payment.setAmountReceived(new BigDecimal("20000.00"));
        payment.setTipAmount(new BigDecimal("1500.00"));
        payment.setAmountReturned(new BigDecimal("2000.00"));

        byte[] pdfWithPayment = pdfService.render(baseInvoice, Collections.emptyList(), "مطعم", payment);
        byte[] pdfWithout = pdfService.render(baseInvoice, Collections.emptyList(), "مطعم", null);

        assertThat(pdfWithPayment).isNotNull();
        assertThat(pdfWithPayment.length).isGreaterThan(pdfWithout.length);
    }

    @Test
    @DisplayName("Should render with bill charges")
    void shouldRenderWithCharges() {
        SessionBillCharge tax = new SessionBillCharge();
        tax.setRuleNameAr("ضريبة القيمة المضافة");
        tax.setRuleNameEn("VAT");
        tax.setRuleType(RuleType.PERCENTAGE);
        tax.setRuleValue(new BigDecimal("10.0000"));
        tax.setComputedAmount(new BigDecimal("1500.00"));
        tax.setApplyOrder(1);

        byte[] pdfWithCharges = pdfService.render(baseInvoice, List.of(tax), "مطعم", null);
        byte[] pdfNoCharges = pdfService.render(baseInvoice, Collections.emptyList(), "مطعم", null);

        assertThat(pdfWithCharges.length).isGreaterThan(pdfNoCharges.length);
    }

    @Test
    @DisplayName("Should render with empty items list")
    void shouldRenderWithEmptyItems() {
        baseInvoice.setItems(new ArrayList<>());

        byte[] pdf = pdfService.render(baseInvoice, Collections.emptyList(), "مطعم", null);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
    }

    @Test
    @DisplayName("Should render with null/blank restaurant name")
    void shouldRenderWithBlankRestaurantName() {
        byte[] pdf = pdfService.render(baseInvoice, Collections.emptyList(), "", null);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
    }

    @Test
    @DisplayName("Should render with FIXED charge type")
    void shouldRenderWithFixedCharge() {
        SessionBillCharge fee = new SessionBillCharge();
        fee.setRuleNameAr("رسم خدمة");
        fee.setRuleNameEn("Service Fee");
        fee.setRuleType(RuleType.FIXED);
        fee.setRuleValue(new BigDecimal("2000.0000"));
        fee.setComputedAmount(new BigDecimal("2000.00"));
        fee.setApplyOrder(1);

        assertThatCode(() ->
                pdfService.render(baseInvoice, List.of(fee), "مطعم", null)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should render with zero-tip payment")
    void shouldRenderWithZeroTipPayment() {
        SessionPayment payment = new SessionPayment();
        payment.setAmountReceived(new BigDecimal("16500.00"));
        payment.setTipAmount(BigDecimal.ZERO);
        payment.setAmountReturned(BigDecimal.ZERO);

        assertThatCode(() ->
                pdfService.render(baseInvoice, Collections.emptyList(), "مطعم", payment)
        ).doesNotThrowAnyException();
    }
}

