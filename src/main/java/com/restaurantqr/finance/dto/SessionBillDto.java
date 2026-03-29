package com.restaurantqr.finance.dto;

import com.restaurantqr.finance.BillStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Complete session bill with itemized charges")
public record SessionBillDto(
        @Schema(description = "Bill ID")
        Long billId,

        @Schema(description = "Table number", example = "5")
        Integer tableNumber,

        @Schema(description = "Session code", example = "1234")
        String sessionCode,

        @Schema(description = "Sum of all ordered items (excl. cancelled)", example = "10000.00")
        BigDecimal itemsSubtotal,

        @Schema(description = "Sum of all applied charges", example = "2075.00")
        BigDecimal chargesTotal,

        @Schema(description = "Final total = items + charges", example = "12075.00")
        BigDecimal grandTotal,

        @Schema(description = "Currency code", example = "SYP")
        String currency,

        @Schema(description = "Bill status: DRAFT or FINALIZED")
        BillStatus status,

        @Schema(description = "Itemized charges breakdown")
        List<BillChargeDto> charges,

        @Schema(description = "When the bill was finalized (null if DRAFT)")
        Instant finalizedAt
) {}

