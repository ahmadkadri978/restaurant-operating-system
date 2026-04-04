package com.restaurantqr.finance;

import com.restaurantqr.finance.dto.InvoiceListDto;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceService {

    /**
     * Generate an invoice for a closed session.
     * Idempotent: if an invoice already exists for the session, returns the existing one.
     *
     * @param tableSessionId the table_sessions.id
     * @return the persisted invoice
     */
    SessionInvoice generateInvoice(Long tableSessionId);

    /**
     * List invoices for a restaurant on a given date, ordered newest first.
     *
     * @param restaurantId the restaurant ID (from JWT)
     * @param date         the local date to filter by
     * @param zone         timezone for date conversion (e.g. "Asia/Damascus")
     * @return list of invoice summaries
     */
    List<InvoiceListDto> listByDate(Long restaurantId, LocalDate date, String zone);

    /**
     * Search for an invoice by its invoice number.
     *
     * @param restaurantId  the restaurant ID (from JWT)
     * @param invoiceNumber the invoice number to search for
     * @return list containing the matching invoice (or empty list)
     */
    List<InvoiceListDto> searchByNumber(Long restaurantId, String invoiceNumber);

    /**
     * Generate a PDF for an invoice.
     *
     * @param restaurantId the restaurant ID (from JWT — security boundary)
     * @param invoiceId    the invoice ID
     * @return PDF bytes
     */
    byte[] generatePdf(Long restaurantId, Long invoiceId);
}

