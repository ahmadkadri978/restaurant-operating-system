package com.restaurantqr.exception;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;



@Schema(
        name = "ApiErrorResponse",
        description = """
                Standard error response returned by the API for all failure cases.

                **Important fields**
                - `code`: Machine-readable error code that frontend can use for UX decisions.
                - `message`: Human-readable message safe to display.
                - `path`: Request path that caused the error.
                """
)
public record ApiErrorResponse(

        @Schema(description = "Error timestamp (UTC)", example = "2026-01-07T21:15:30.123Z")
        Instant timestamp,

        @Schema(description = "HTTP status code", example = "429")
        int status,

        @Schema(description = "HTTP status text", example = "Too Many Requests")
        String error,

        @Schema(description = "Application-specific error code", example = "RATE_LIMIT_EXCEEDED")
        String code,

        @Schema(description = "Human-readable message", example = "Only one order per minute is allowed for this table")
        String message,

        @Schema(description = "Request path", example = "/api/v1/public/tables/DEMO-TABLE-1/orders")
        String path
) {}

