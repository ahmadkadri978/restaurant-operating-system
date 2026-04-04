package com.restaurantqr.finance;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.config.OpenApiConfig;
import com.restaurantqr.exception.ApiErrorResponse;
import com.restaurantqr.finance.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Owner - Financial Rules", description = "Manage restaurant financial rules (taxes, fees, charges)")
@RestController
@RequestMapping("/api/v1/owner/financial-rules")
@PreAuthorize("hasRole('OWNER')")
public class FinancialRuleController {

    private static final Logger log = LoggerFactory.getLogger(FinancialRuleController.class);

    private final FinancialRuleService financialRuleService;

    public FinancialRuleController(FinancialRuleService financialRuleService) {
        this.financialRuleService = financialRuleService;
    }

    @Operation(
            summary = "List all financial rules for the restaurant",
            description = "Returns all financial rules (active and inactive) ordered by apply_order.",
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rules retrieved successfully",
                    content = @Content(schema = @Schema(implementation = FinancialRulesResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not OWNER role",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<FinancialRulesResponse> listRules(Authentication authentication) {
        Long restaurantId = extractRestaurantId(authentication);
        log.info("LIST_FINANCIAL_RULES | restaurantId={}", restaurantId);

        FinancialRulesResponse response = financialRuleService.listRules(restaurantId);

        log.info("LIST_FINANCIAL_RULES_SUCCESS | restaurantId={} | count={}", restaurantId, response.rules().size());
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Create a new financial rule",
            description = """
                    Creates a new financial rule for the restaurant.
                    
                    **Rule types:**
                    - FIXED: a fixed monetary amount added to every bill
                    - PERCENTAGE: a percentage of the items subtotal
                    
                    **Limits:**
                    - Maximum 20 active rules per restaurant
                    - Percentage value must be between 0.01 and 100.00
                    - Compound flag only applies to PERCENTAGE rules
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rule created",
                    content = @Content(schema = @Schema(implementation = FinancialRuleDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<FinancialRuleDto> createRule(
            Authentication authentication,
            @Valid @RequestBody CreateFinancialRuleRequest request
    ) {
        Long restaurantId = extractRestaurantId(authentication);
        log.info("CREATE_FINANCIAL_RULE | restaurantId={} | nameAr={}", restaurantId, request.nameAr());

        FinancialRuleDto dto = financialRuleService.createRule(restaurantId, request);

        log.info("CREATE_FINANCIAL_RULE_SUCCESS | restaurantId={} | ruleId={}", restaurantId, dto.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(
            summary = "Update an existing financial rule",
            description = "Partial update — only provided fields are changed.",
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule updated",
                    content = @Content(schema = @Schema(implementation = FinancialRuleDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Rule not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{ruleId}")
    public ResponseEntity<FinancialRuleDto> updateRule(
            Authentication authentication,
            @PathVariable Long ruleId,
            @Valid @RequestBody UpdateFinancialRuleRequest request
    ) {
        Long restaurantId = extractRestaurantId(authentication);
        log.info("UPDATE_FINANCIAL_RULE | restaurantId={} | ruleId={}", restaurantId, ruleId);

        FinancialRuleDto dto = financialRuleService.updateRule(restaurantId, ruleId, request);

        log.info("UPDATE_FINANCIAL_RULE_SUCCESS | restaurantId={} | ruleId={}", restaurantId, ruleId);
        return ResponseEntity.ok(dto);
    }

    @Operation(
            summary = "Delete (deactivate) a financial rule",
            description = "Soft-deletes the rule by setting is_active = false. Past bills are not affected.",
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rule deactivated"),
            @ApiResponse(responseCode = "404", description = "Rule not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(
            Authentication authentication,
            @PathVariable Long ruleId
    ) {
        Long restaurantId = extractRestaurantId(authentication);
        log.info("DELETE_FINANCIAL_RULE | restaurantId={} | ruleId={}", restaurantId, ruleId);

        financialRuleService.deleteRule(restaurantId, ruleId);

        log.info("DELETE_FINANCIAL_RULE_SUCCESS | restaurantId={} | ruleId={}", restaurantId, ruleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Reorder financial rules",
            description = """
                    Updates the apply_order of all specified rules based on their position in the list.
                    First item gets apply_order=1, second gets 2, etc.
                    
                    The order matters for bill calculation — especially for compound percentage rules.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rules reordered",
                    content = @Content(schema = @Schema(implementation = FinancialRulesResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Rule not found in restaurant",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/reorder")
    public ResponseEntity<FinancialRulesResponse> reorderRules(
            Authentication authentication,
            @Valid @RequestBody ReorderFinancialRulesRequest request
    ) {
        Long restaurantId = extractRestaurantId(authentication);
        log.info("REORDER_FINANCIAL_RULES | restaurantId={} | count={}", restaurantId, request.ruleIds().size());

        FinancialRulesResponse response = financialRuleService.reorderRules(restaurantId, request);

        log.info("REORDER_FINANCIAL_RULES_SUCCESS | restaurantId={}", restaurantId);
        return ResponseEntity.ok(response);
    }

    // ==================== Private helpers ====================

    private Long extractRestaurantId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("Authentication principal is required");
        }
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        if (principal.restaurantId() == null) {
            throw new IllegalStateException("Restaurant ID not found in authentication principal");
        }
        return principal.restaurantId();
    }
}

