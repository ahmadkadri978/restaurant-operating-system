package com.restaurantqr.finance;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders a session invoice as a professional Arabic RTL PDF document.
 * <p>
 * Uses the Amiri Arabic font (embedded) for full Unicode/Arabic support.
 * Font files must exist at:
 * <ul>
 *   <li>{@code src/main/resources/fonts/Amiri-Regular.ttf}</li>
 *   <li>{@code src/main/resources/fonts/Amiri-Bold.ttf}</li>
 * </ul>
 */
@Service
public class InvoicePdfService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfService.class);

    private static final String TIMEZONE = "Asia/Damascus";
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of(TIMEZONE));
    private static final DecimalFormat NUM_FMT = new DecimalFormat("#,##0.00");

    // ─── Color palette ───
    private static final Color CLR_PRIMARY       = new Color(15, 23, 42);
    private static final Color CLR_ACCENT        = new Color(34, 197, 94);
    private static final Color CLR_TEXT           = new Color(30, 41, 59);
    private static final Color CLR_TEXT_MUTED     = new Color(100, 116, 139);
    private static final Color CLR_WHITE          = Color.WHITE;
    private static final Color CLR_LIGHT_BG       = new Color(248, 250, 252);
    private static final Color CLR_BORDER         = new Color(226, 232, 240);
    private static final Color CLR_CHARGE_BG      = new Color(241, 245, 249);
    private static final Color CLR_TABLE_HEADER   = new Color(51, 65, 85);

    // Currency label
    private static final String CURRENCY_LABEL = "ل.س";

    // ─── Cached base fonts ───
    private volatile BaseFont bfRegular;
    private volatile BaseFont bfBold;

    /**
     * Render a PDF for the given invoice, bill charges, and restaurant name.
     *
     * @param invoice        the invoice entity (with items loaded)
     * @param charges        bill charges (taxes, fees, etc.)
     * @param restaurantName the restaurant display name (Arabic or otherwise)
     * @return PDF bytes
     */
    public byte[] render(SessionInvoice invoice, List<SessionBillCharge> charges, String restaurantName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Document doc = new Document(PageSize.A4, 40, 40, 36, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            BaseFont regular = resolveFont(false);
            BaseFont bold    = resolveFont(true);

            // ─── Font definitions ───
            Font fontRestaurantName = new Font(bold, 26, Font.BOLD, CLR_PRIMARY);
            Font fontTitle          = new Font(bold, 18, Font.BOLD, CLR_WHITE);
            Font fontInvoiceNum     = new Font(regular, 11, Font.NORMAL, new Color(148, 163, 184));
            Font fontSectionTitle   = new Font(bold, 13, Font.BOLD, CLR_PRIMARY);
            Font fontLabel          = new Font(bold, 10, Font.BOLD, CLR_TEXT_MUTED);
            Font fontValue          = new Font(regular, 11, Font.NORMAL, CLR_TEXT);
            Font fontTableHeader    = new Font(bold, 10, Font.BOLD, CLR_WHITE);
            Font fontCell           = new Font(regular, 10, Font.NORMAL, CLR_TEXT);
            Font fontCellBold       = new Font(bold, 10, Font.BOLD, CLR_TEXT);
            Font fontSubtotal       = new Font(bold, 11, Font.BOLD, CLR_TEXT);
            Font fontCharge         = new Font(regular, 10, Font.NORMAL, CLR_TEXT);
            Font fontTotalLabel     = new Font(bold, 14, Font.BOLD, CLR_WHITE);
            Font fontTotalValue     = new Font(bold, 16, Font.BOLD, CLR_WHITE);
            Font fontFooter         = new Font(regular, 8, Font.ITALIC, CLR_TEXT_MUTED);

            // ═══════════════════════════════════════════════════════════
            //  SECTION 1: Restaurant Name (centered, large, bold)
            // ═══════════════════════════════════════════════════════════
            if (restaurantName != null && !restaurantName.isBlank()) {
                PdfPTable nameBlock = new PdfPTable(1);
                nameBlock.setWidthPercentage(100);
                nameBlock.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

                PdfPCell nameCell = new PdfPCell(new Phrase(restaurantName, fontRestaurantName));
                nameCell.setBorder(PdfPCell.NO_BORDER);
                nameCell.setPaddingTop(10);
                nameCell.setPaddingBottom(16);
                nameCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                nameCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                nameBlock.addCell(nameCell);

                doc.add(nameBlock);
            }

            // ═══════════════════════════════════════════════════════════
            //  SECTION 2: "فاتورة" Header Banner
            // ═══════════════════════════════════════════════════════════
            PdfPTable headerBanner = new PdfPTable(1);
            headerBanner.setWidthPercentage(100);
            headerBanner.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

            // Title row — text-mode cell (NOT addElement) for correct Arabic shaping
            PdfPCell titleCell = new PdfPCell(new Phrase("فاتورة", fontTitle));
            titleCell.setBackgroundColor(CLR_PRIMARY);
            titleCell.setPaddingTop(16);
            titleCell.setPaddingBottom(4);
            titleCell.setPaddingLeft(16);
            titleCell.setPaddingRight(16);
            titleCell.setBorder(PdfPCell.NO_BORDER);
            titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            titleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            headerBanner.addCell(titleCell);

            // Invoice number row
            PdfPCell invNumCell = new PdfPCell(new Phrase(invoice.getInvoiceNumber(), fontInvoiceNum));
            invNumCell.setBackgroundColor(CLR_PRIMARY);
            invNumCell.setPaddingTop(4);
            invNumCell.setPaddingBottom(16);
            invNumCell.setPaddingLeft(16);
            invNumCell.setPaddingRight(16);
            invNumCell.setBorder(PdfPCell.NO_BORDER);
            invNumCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            invNumCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            headerBanner.addCell(invNumCell);

            doc.add(headerBanner);

            doc.add(spacer(14));

            // ═══════════════════════════════════════════════════════════
            //  SECTION 3: Invoice Details (table/session/times) — RTL
            // ═══════════════════════════════════════════════════════════
            doc.add(rtlSectionTitle("بيانات الجلسة", fontSectionTitle));
            doc.add(spacer(6));

            PdfPTable details = new PdfPTable(4);
            details.setWidthPercentage(100);
            details.setWidths(new float[]{1.8f, 1.2f, 1.8f, 1.2f});
            details.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

            // Row 1: table number + session code
            addInfoCell(details, String.valueOf(invoice.getTableNumber()), fontValue);
            addInfoCell(details, "رقم الطاولة", fontLabel);
            addInfoCell(details, invoice.getSessionCode(), fontValue);
            addInfoCell(details, "رقم الجلسة", fontLabel);

            // Row 2: opened at + closed at
            addInfoCell(details, formatDateTime(invoice.getSessionOpenedAt()), fontValue);
            addInfoCell(details, "وقت الفتح", fontLabel);
            addInfoCell(details, formatDateTime(invoice.getSessionClosedAt()), fontValue);
            addInfoCell(details, "وقت الإغلاق", fontLabel);

            // Row 3: invoice number + issue date
            addInfoCell(details, invoice.getInvoiceNumber(), fontValue);
            addInfoCell(details, "رقم الفاتورة", fontLabel);
            addInfoCell(details, formatDateTime(invoice.getCreatedAt()), fontValue);
            addInfoCell(details, "تاريخ الإصدار", fontLabel);

            doc.add(details);
            doc.add(spacer(14));

            // ═══════════════════════════════════════════════════════════
            //  SECTION 4: Decorative line
            // ═══════════════════════════════════════════════════════════
            doc.add(horizontalLine());
            doc.add(spacer(10));

            // ═══════════════════════════════════════════════════════════
            //  SECTION 5: Items Table — RTL
            // ═══════════════════════════════════════════════════════════
            doc.add(rtlSectionTitle("الطلبات", fontSectionTitle));
            doc.add(spacer(6));

            PdfPTable itemsTable = new PdfPTable(4);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{1.5f, 1.5f, 1f, 3f});
            itemsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

            // Table headers (RTL: rightmost first = الصنف)
            addTableHeaderCell(itemsTable, "الصنف", fontTableHeader);
            addTableHeaderCell(itemsTable, "الكمية", fontTableHeader);
            addTableHeaderCell(itemsTable, "السعر", fontTableHeader);
            addTableHeaderCell(itemsTable, "المجموع", fontTableHeader);

            // Item rows
            boolean alt = false;
            for (SessionInvoiceItem item : invoice.getItems()) {
                Color rowBg = alt ? CLR_LIGHT_BG : CLR_WHITE;
                addItemRow(itemsTable, item, fontCell, fontCellBold, rowBg);
                alt = !alt;
            }

            doc.add(itemsTable);

            // ═══════════════════════════════════════════════════════════
            //  SECTION 6: Subtotal
            // ═══════════════════════════════════════════════════════════
            doc.add(spacer(4));

            PdfPTable subtotalTbl = new PdfPTable(2);
            subtotalTbl.setWidthPercentage(100);
            subtotalTbl.setWidths(new float[]{2f, 4f});
            subtotalTbl.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

            addSummaryRow(subtotalTbl, "المجموع الفرعي",
                    formatCurrency(invoice.getItemsSubtotal(), invoice.getCurrency()),
                    fontSubtotal, CLR_LIGHT_BG);

            doc.add(subtotalTbl);

            // ═══════════════════════════════════════════════════════════
            //  SECTION 7: Charges (taxes, fees)
            // ═══════════════════════════════════════════════════════════
            if (charges != null && !charges.isEmpty()) {
                doc.add(spacer(6));
                doc.add(rtlSectionTitle("الرسوم والضرائب", fontSectionTitle));
                doc.add(spacer(4));

                PdfPTable chargesTbl = new PdfPTable(2);
                chargesTbl.setWidthPercentage(100);
                chargesTbl.setWidths(new float[]{2f, 4f});
                chargesTbl.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

                for (SessionBillCharge charge : charges) {
                    String label = charge.getRuleNameAr();
                    if (charge.getRuleType() == RuleType.PERCENTAGE) {
                        label += " (" + charge.getRuleValue().stripTrailingZeros().toPlainString() + "%)";
                    }
                    addSummaryRow(chargesTbl, label,
                            formatCurrency(charge.getComputedAmount(), invoice.getCurrency()),
                            fontCharge, CLR_CHARGE_BG);
                }

                doc.add(chargesTbl);
            }

            doc.add(spacer(8));

            // ═══════════════════════════════════════════════════════════
            //  SECTION 8: Grand Total
            // ═══════════════════════════════════════════════════════════
            PdfPTable totalTbl = new PdfPTable(2);
            totalTbl.setWidthPercentage(100);
            totalTbl.setWidths(new float[]{2f, 4f});
            totalTbl.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

            PdfPCell totalLabelCell = new PdfPCell(
                    new Phrase("المجموع الكلي", fontTotalLabel));
            totalLabelCell.setBackgroundColor(CLR_ACCENT);
            totalLabelCell.setPadding(14);
            totalLabelCell.setBorderColor(CLR_ACCENT);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            totalTbl.addCell(totalLabelCell);

            PdfPCell totalValueCell = new PdfPCell(
                    new Phrase(formatCurrency(invoice.getGrandTotal(), invoice.getCurrency()),
                            fontTotalValue));
            totalValueCell.setBackgroundColor(CLR_ACCENT);
            totalValueCell.setPadding(14);
            totalValueCell.setBorderColor(CLR_ACCENT);
            totalValueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            totalValueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            totalTbl.addCell(totalValueCell);

            doc.add(totalTbl);

            doc.add(spacer(28));

            // ═══════════════════════════════════════════════════════════
            //  SECTION 9: Footer
            // ═══════════════════════════════════════════════════════════
            doc.add(horizontalLine());
            doc.add(spacer(6));

            PdfPTable footerTbl = new PdfPTable(1);
            footerTbl.setWidthPercentage(100);
            footerTbl.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

            PdfPCell footerCell = new PdfPCell(
                    new Phrase("تم الإصدار: " + formatDateTime(Instant.now()), fontFooter));
            footerCell.setBorder(PdfPCell.NO_BORDER);
            footerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            footerCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            footerCell.setPaddingBottom(4);
            footerTbl.addCell(footerCell);

            if (restaurantName != null && !restaurantName.isBlank()) {
                PdfPCell thanksCell = new PdfPCell(
                        new Phrase("شكراً لزيارتكم — " + restaurantName, fontFooter));
                thanksCell.setBorder(PdfPCell.NO_BORDER);
                thanksCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                thanksCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                footerTbl.addCell(thanksCell);
            }

            doc.add(footerTbl);

            doc.close();

            log.debug("PDF rendered | invoiceNumber={} | bytes={}", invoice.getInvoiceNumber(), out.size());
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to render PDF | invoiceNumber={} | error={}",
                    invoice.getInvoiceNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }

    // ==================== Font resolution ====================

    /**
     * Resolve and cache an Arabic-capable BaseFont.
     * Looks for Amiri in classpath, then fallback system fonts.
     */
    private BaseFont resolveFont(boolean bold) {
        BaseFont cached = bold ? bfBold : bfRegular;
        if (cached != null) return cached;

        synchronized (this) {
            cached = bold ? bfBold : bfRegular;
            if (cached != null) return cached;

            // Classpath fonts (Amiri)
            String[] classpathPaths = bold
                    ? new String[]{"fonts/Amiri-Bold.ttf", "fonts/NotoSansArabic-Bold.ttf"}
                    : new String[]{"fonts/Amiri-Regular.ttf", "fonts/NotoSansArabic-Regular.ttf"};

            for (String path : classpathPaths) {
                try {
                    var resource = getClass().getClassLoader().getResource(path);
                    if (resource != null) {
                        BaseFont bf = BaseFont.createFont(
                                resource.toExternalForm(),
                                BaseFont.IDENTITY_H,
                                BaseFont.EMBEDDED);
                        if (bold) { bfBold = bf; } else { bfRegular = bf; }
                        log.info("Loaded PDF font ({}) from classpath: {}", bold ? "bold" : "regular", path);
                        return bf;
                    }
                } catch (Exception e) {
                    log.debug("Could not load classpath font '{}': {}", path, e.getMessage());
                }
            }

            // System font fallbacks
            String[] systemPaths = {
                    "C:\\Windows\\Fonts\\arial.ttf",
                    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                    "/usr/share/fonts/truetype/noto/NotoSansArabic-Regular.ttf",
                    "/usr/share/fonts/TTF/DejaVuSans.ttf"
            };
            for (String path : systemPaths) {
                try {
                    BaseFont bf = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    if (bold) { bfBold = bf; } else { bfRegular = bf; }
                    log.info("Loaded PDF font ({}) from system: {}", bold ? "bold" : "regular", path);
                    return bf;
                } catch (Exception e) {
                    log.debug("System font not available '{}': {}", path, e.getMessage());
                }
            }

            // Fallback to Helvetica (Arabic won't render properly)
            log.warn("No Arabic-capable font found ({}). PDF will use Helvetica. " +
                            "To fix: place Amiri-Regular.ttf / Amiri-Bold.ttf in src/main/resources/fonts/",
                    bold ? "bold" : "regular");
            try {
                BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                if (bold) { bfBold = bf; } else { bfRegular = bf; }
                return bf;
            } catch (Exception e) {
                throw new RuntimeException("Cannot create fallback font", e);
            }
        }
    }

    // ==================== Formatting helpers ====================

    private String formatDateTime(Instant instant) {
        if (instant == null) return "—";
        return DT_FMT.format(instant);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return NUM_FMT.format(amount);
    }

    /**
     * Format amount with currency label (e.g. "1,250.00 ل.س")
     */
    private String formatCurrency(BigDecimal amount, String currencyCode) {
        String formatted = formatAmount(amount);
        if ("SYP".equalsIgnoreCase(currencyCode)) {
            return formatted + " " + CURRENCY_LABEL;
        }
        return formatted + " " + (currencyCode != null ? currencyCode : "");
    }

    // ==================== PDF building helpers ====================

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        return p;
    }

    /**
     * Creates a thin horizontal line separator.
     */
    private PdfPTable horizontalLine() {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.BOTTOM);
        cell.setBorderColor(CLR_BORDER);
        cell.setBorderWidth(1f);
        cell.setFixedHeight(1f);
        cell.setPadding(0);
        line.addCell(cell);
        return line;
    }

    /**
     * Creates an RTL section title (e.g. "الطلبات") with an underline.
     */
    private PdfPTable rtlSectionTitle(String text, Font font) {
        PdfPTable tbl = new PdfPTable(1);
        tbl.setWidthPercentage(100);
        tbl.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(PdfPCell.BOTTOM);
        cell.setBorderColor(CLR_BORDER);
        cell.setBorderWidth(1.5f);
        cell.setPadding(6);
        cell.setPaddingBottom(4);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        tbl.addCell(cell);

        return tbl;
    }

    /**
     * Adds an info cell in the details grid (RTL).
     */
    private void addInfoCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPadding(7);
        cell.setBackgroundColor(CLR_LIGHT_BG);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    /**
     * Adds a header cell for the items table.
     */
    private void addTableHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(CLR_TABLE_HEADER);
        cell.setPadding(10);
        cell.setBorderColor(CLR_TABLE_HEADER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    /**
     * Adds one item row to the items table (RTL).
     * Columns in RTL order: الصنف | الكمية | السعر | المجموع
     */
    private void addItemRow(PdfPTable table, SessionInvoiceItem item,
                            Font cellFont, Font boldFont, Color bg) {
        // الصنف (item name) — right-aligned
        PdfPCell nameCell = new PdfPCell(new Phrase(item.getItemName(), boldFont));
        nameCell.setBackgroundColor(bg);
        nameCell.setPadding(9);
        nameCell.setBorderColor(CLR_BORDER);
        nameCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        nameCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(nameCell);

        // الكمية (quantity) — centered
        PdfPCell qtyCell = new PdfPCell(new Phrase(String.valueOf(item.getQuantity()), cellFont));
        qtyCell.setBackgroundColor(bg);
        qtyCell.setPadding(9);
        qtyCell.setBorderColor(CLR_BORDER);
        qtyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        qtyCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(qtyCell);

        // السعر (unit price)
        PdfPCell priceCell = new PdfPCell(new Phrase(formatAmount(item.getUnitPrice()), cellFont));
        priceCell.setBackgroundColor(bg);
        priceCell.setPadding(9);
        priceCell.setBorderColor(CLR_BORDER);
        priceCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        priceCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(priceCell);

        // المجموع (line total)
        PdfPCell totalCell = new PdfPCell(new Phrase(formatAmount(item.getLineTotal()), boldFont));
        totalCell.setBackgroundColor(bg);
        totalCell.setPadding(9);
        totalCell.setBorderColor(CLR_BORDER);
        totalCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        totalCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(totalCell);
    }

    /**
     * Adds a summary row (subtotal / charge / etc.) — RTL 2-column layout.
     */
    private void addSummaryRow(PdfPTable table, String label, String value, Font font, Color bg) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBackgroundColor(bg);
        labelCell.setPadding(10);
        labelCell.setBorderColor(CLR_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setBackgroundColor(bg);
        valueCell.setPadding(10);
        valueCell.setBorderColor(CLR_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        valueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(valueCell);
    }
}

