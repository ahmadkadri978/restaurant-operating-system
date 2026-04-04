package com.restaurantqr.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Paginated cash register history response")
public record CashRegisterPageResponse(

        @Schema(description = "Register sessions for the current page")
        List<CashRegisterDto> content,

        @Schema(description = "Current page number (0-based)")
        int number,

        @Schema(description = "Page size requested")
        int size,

        @Schema(description = "Total number of closed register sessions")
        long totalElements,

        @Schema(description = "Total number of pages")
        int totalPages,

        @Schema(description = "True if this is the first page")
        boolean first,

        @Schema(description = "True if this is the last page")
        boolean last

) {
    /** Build from a Spring Data Page. */
    public static CashRegisterPageResponse from(Page<CashRegisterDto> page) {
        return new CashRegisterPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}

