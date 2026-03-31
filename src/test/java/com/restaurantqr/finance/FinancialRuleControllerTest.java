package com.restaurantqr.finance;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FinancialRuleController Unit Tests")
class FinancialRuleControllerTest {

    @Mock private FinancialRuleService financialRuleService;
    @Mock private Authentication authentication;

    private FinancialRuleController controller;

    private static final Long RESTAURANT_ID = 1L;

    @BeforeEach
    void setUp() {
        controller = new FinancialRuleController(financialRuleService);
        AuthPrincipal principal = new AuthPrincipal(10L, "OWNER", RESTAURANT_ID);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    private FinancialRuleDto sampleDto() {
        return new FinancialRuleDto(1L, "ضريبة", "VAT", RuleType.PERCENTAGE,
                new BigDecimal("10.0000"), 1, false, true, Instant.now(), Instant.now());
    }

    // ==================== listRules ====================

    @Nested
    @DisplayName("List Rules")
    class ListRulesTests {

        @Test
        @DisplayName("Should return 200 with rules list")
        void shouldReturn200WithRules() {
            FinancialRulesResponse expected = new FinancialRulesResponse(RESTAURANT_ID, List.of(sampleDto()));
            when(financialRuleService.listRules(RESTAURANT_ID)).thenReturn(expected);

            ResponseEntity<FinancialRulesResponse> response = controller.listRules(authentication);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().rules()).hasSize(1);
            assertThat(response.getBody().restaurantId()).isEqualTo(RESTAURANT_ID);
        }
    }

    // ==================== createRule ====================

    @Nested
    @DisplayName("Create Rule")
    class CreateRuleTests {

        @Test
        @DisplayName("Should return 201 CREATED on success")
        void shouldReturn201OnSuccess() {
            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "ضريبة", "VAT", RuleType.PERCENTAGE, new BigDecimal("10.0000"), false);
            FinancialRuleDto dto = sampleDto();
            when(financialRuleService.createRule(RESTAURANT_ID, request)).thenReturn(dto);

            ResponseEntity<FinancialRuleDto> response = controller.createRule(authentication, request);

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should delegate to service with correct restaurantId")
        void shouldDelegateToService() {
            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "رسم خدمة", "Service", RuleType.FIXED, new BigDecimal("500"), null);
            when(financialRuleService.createRule(RESTAURANT_ID, request)).thenReturn(sampleDto());

            controller.createRule(authentication, request);

            verify(financialRuleService).createRule(RESTAURANT_ID, request);
        }
    }

    // ==================== updateRule ====================

    @Nested
    @DisplayName("Update Rule")
    class UpdateRuleTests {

        @Test
        @DisplayName("Should return 200 on success")
        void shouldReturn200OnSuccess() {
            UpdateFinancialRuleRequest request = new UpdateFinancialRuleRequest(
                    "ضريبة معدلة", null, null, null, null, null);
            FinancialRuleDto dto = sampleDto();
            when(financialRuleService.updateRule(RESTAURANT_ID, 1L, request)).thenReturn(dto);

            ResponseEntity<FinancialRuleDto> response = controller.updateRule(authentication, 1L, request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("Should propagate ResourceNotFoundException from service")
        void shouldPropagateNotFound() {
            UpdateFinancialRuleRequest request = new UpdateFinancialRuleRequest(
                    null, null, null, null, null, null);
            when(financialRuleService.updateRule(eq(RESTAURANT_ID), eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException("Rule not found"));

            assertThatThrownBy(() -> controller.updateRule(authentication, 999L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== deleteRule ====================

    @Nested
    @DisplayName("Delete Rule")
    class DeleteRuleTests {

        @Test
        @DisplayName("Should return 204 NO_CONTENT on success")
        void shouldReturn204OnSuccess() {
            doNothing().when(financialRuleService).deleteRule(RESTAURANT_ID, 1L);

            ResponseEntity<Void> response = controller.deleteRule(authentication, 1L);

            assertThat(response.getStatusCode().value()).isEqualTo(204);
            assertThat(response.getBody()).isNull();
            verify(financialRuleService).deleteRule(RESTAURANT_ID, 1L);
        }
    }

    // ==================== reorderRules ====================

    @Nested
    @DisplayName("Reorder Rules")
    class ReorderRulesTests {

        @Test
        @DisplayName("Should return 200 with reordered rules")
        void shouldReturn200WithReorderedRules() {
            ReorderFinancialRulesRequest request = new ReorderFinancialRulesRequest(List.of(3L, 1L, 2L));
            FinancialRulesResponse expected = new FinancialRulesResponse(RESTAURANT_ID, List.of(sampleDto()));
            when(financialRuleService.reorderRules(RESTAURANT_ID, request)).thenReturn(expected);

            ResponseEntity<FinancialRulesResponse> response = controller.reorderRules(authentication, request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            verify(financialRuleService).reorderRules(RESTAURANT_ID, request);
        }
    }

    // ==================== Auth Extraction Edge Cases ====================

    @Nested
    @DisplayName("Auth Extraction")
    class AuthExtractionTests {

        @Test
        @DisplayName("Should throw when authentication is null")
        void shouldThrowWhenAuthNull() {
            assertThatThrownBy(() -> controller.listRules(null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Should throw when principal is null")
        void shouldThrowWhenPrincipalNull() {
            when(authentication.getPrincipal()).thenReturn(null);

            assertThatThrownBy(() -> controller.listRules(authentication))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}

