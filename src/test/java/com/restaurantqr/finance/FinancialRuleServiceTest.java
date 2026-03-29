package com.restaurantqr.finance;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.*;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FinancialRuleServiceImpl — financial rule CRUD operations.
 * Covers: create, read, update, delete (soft), reorder, and validation edge cases.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FinancialRuleService Unit Tests")
class FinancialRuleServiceTest {

    @Mock
    private RestaurantFinancialRuleRepository ruleRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private FinancialRuleServiceImpl ruleService;

    @Captor
    private ArgumentCaptor<RestaurantFinancialRule> ruleCaptor;

    private Restaurant testRestaurant;
    private static final Long RESTAURANT_ID = 1L;

    @BeforeEach
    void setUp() {
        testRestaurant = new Restaurant();
        testRestaurant.setId(RESTAURANT_ID);
        testRestaurant.setName("Test Restaurant");
    }

    // ==================== Helper Methods ====================

    private RestaurantFinancialRule createRule(Long id, String nameAr, String nameEn,
                                               RuleType type, BigDecimal value,
                                               int order, boolean compound, boolean active) {
        RestaurantFinancialRule rule = new RestaurantFinancialRule(
                testRestaurant, nameAr, nameEn, type, value, order, compound
        );
        rule.setId(id);
        rule.setActive(active);
        return rule;
    }

    // ==================== List Rules Tests ====================

    @Nested
    @DisplayName("listRules()")
    class ListRulesTests {

        @Test
        @DisplayName("Should return all rules for a restaurant ordered by applyOrder")
        void shouldReturnAllRulesOrdered() {
            // Given
            RestaurantFinancialRule vatRule = createRule(1L, "ضريبة", "VAT",
                    RuleType.PERCENTAGE, new BigDecimal("10.0000"), 1, false, true);
            RestaurantFinancialRule feeRule = createRule(2L, "رسم طاقة", "Energy Fee",
                    RuleType.FIXED, new BigDecimal("500.0000"), 2, false, true);

            when(ruleRepository.findByRestaurantIdOrderByApplyOrderAsc(RESTAURANT_ID))
                    .thenReturn(List.of(vatRule, feeRule));

            // When
            FinancialRulesResponse response = ruleService.listRules(RESTAURANT_ID);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.restaurantId()).isEqualTo(RESTAURANT_ID);
            assertThat(response.rules()).hasSize(2);
            assertThat(response.rules().get(0).nameAr()).isEqualTo("ضريبة");
            assertThat(response.rules().get(0).ruleType()).isEqualTo(RuleType.PERCENTAGE);
            assertThat(response.rules().get(1).nameAr()).isEqualTo("رسم طاقة");
            assertThat(response.rules().get(1).ruleType()).isEqualTo(RuleType.FIXED);
        }

        @Test
        @DisplayName("Should return empty list when restaurant has no rules")
        void shouldReturnEmptyListWhenNoRules() {
            when(ruleRepository.findByRestaurantIdOrderByApplyOrderAsc(RESTAURANT_ID))
                    .thenReturn(Collections.emptyList());

            FinancialRulesResponse response = ruleService.listRules(RESTAURANT_ID);

            assertThat(response.rules()).isEmpty();
            assertThat(response.restaurantId()).isEqualTo(RESTAURANT_ID);
        }

        @Test
        @DisplayName("Should include inactive rules in the list")
        void shouldIncludeInactiveRules() {
            RestaurantFinancialRule activeRule = createRule(1L, "ضريبة", "VAT",
                    RuleType.PERCENTAGE, new BigDecimal("10.0000"), 1, false, true);
            RestaurantFinancialRule inactiveRule = createRule(2L, "رسم قديم", "Old Fee",
                    RuleType.FIXED, new BigDecimal("100.0000"), 2, false, false);

            when(ruleRepository.findByRestaurantIdOrderByApplyOrderAsc(RESTAURANT_ID))
                    .thenReturn(List.of(activeRule, inactiveRule));

            FinancialRulesResponse response = ruleService.listRules(RESTAURANT_ID);

            assertThat(response.rules()).hasSize(2);
            assertThat(response.rules().get(0).isActive()).isTrue();
            assertThat(response.rules().get(1).isActive()).isFalse();
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            assertThatThrownBy(() -> ruleService.listRules(null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("restaurantId is required");
        }
    }

    // ==================== Create Rule Tests ====================

    @Nested
    @DisplayName("createRule()")
    class CreateRuleTests {

        @Test
        @DisplayName("Should create a PERCENTAGE rule successfully")
        void shouldCreatePercentageRule() {
            // Given
            when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
            when(ruleRepository.countActiveByRestaurantId(RESTAURANT_ID)).thenReturn(0L);
            when(ruleRepository.findMaxApplyOrderByRestaurantId(RESTAURANT_ID)).thenReturn(0);
            when(ruleRepository.save(any(RestaurantFinancialRule.class)))
                    .thenAnswer(inv -> {
                        RestaurantFinancialRule r = inv.getArgument(0);
                        r.setId(1L);
                        return r;
                    });

            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "ضريبة القيمة المضافة", "VAT", RuleType.PERCENTAGE, new BigDecimal("10.00"), false
            );

            // When
            FinancialRuleDto result = ruleService.createRule(RESTAURANT_ID, request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.nameAr()).isEqualTo("ضريبة القيمة المضافة");
            assertThat(result.nameEn()).isEqualTo("VAT");
            assertThat(result.ruleType()).isEqualTo(RuleType.PERCENTAGE);
            assertThat(result.value()).isEqualByComparingTo("10.00");
            assertThat(result.applyOrder()).isEqualTo(1);
            assertThat(result.isCompound()).isFalse();
            assertThat(result.isActive()).isTrue();

            verify(ruleRepository).save(ruleCaptor.capture());
            RestaurantFinancialRule saved = ruleCaptor.getValue();
            assertThat(saved.getRestaurant()).isEqualTo(testRestaurant);
        }

        @Test
        @DisplayName("Should create a FIXED rule successfully")
        void shouldCreateFixedRule() {
            when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
            when(ruleRepository.countActiveByRestaurantId(RESTAURANT_ID)).thenReturn(0L);
            when(ruleRepository.findMaxApplyOrderByRestaurantId(RESTAURANT_ID)).thenReturn(3);
            when(ruleRepository.save(any(RestaurantFinancialRule.class)))
                    .thenAnswer(inv -> {
                        RestaurantFinancialRule r = inv.getArgument(0);
                        r.setId(2L);
                        return r;
                    });

            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "رسم طاقة", "Energy Fee", RuleType.FIXED, new BigDecimal("500.00"), null
            );

            FinancialRuleDto result = ruleService.createRule(RESTAURANT_ID, request);

            assertThat(result.ruleType()).isEqualTo(RuleType.FIXED);
            assertThat(result.value()).isEqualByComparingTo("500.00");
            assertThat(result.applyOrder()).isEqualTo(4); // maxOrder(3) + 1
            assertThat(result.isCompound()).isFalse();
        }

        @Test
        @DisplayName("Should create a compound PERCENTAGE rule")
        void shouldCreateCompoundPercentageRule() {
            when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
            when(ruleRepository.countActiveByRestaurantId(RESTAURANT_ID)).thenReturn(0L);
            when(ruleRepository.findMaxApplyOrderByRestaurantId(RESTAURANT_ID)).thenReturn(0);
            when(ruleRepository.save(any())).thenAnswer(inv -> {
                RestaurantFinancialRule r = inv.getArgument(0);
                r.setId(3L);
                return r;
            });

            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "ضريبة مركبة", "Compound Tax", RuleType.PERCENTAGE, new BigDecimal("5.00"), true
            );

            FinancialRuleDto result = ruleService.createRule(RESTAURANT_ID, request);

            assertThat(result.isCompound()).isTrue();
            assertThat(result.ruleType()).isEqualTo(RuleType.PERCENTAGE);
        }

        @Test
        @DisplayName("Should reject compound flag on FIXED rule")
        void shouldRejectCompoundOnFixedRule() {
            when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
            when(ruleRepository.countActiveByRestaurantId(RESTAURANT_ID)).thenReturn(0L);

            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "رسم ثابت", "Fixed Fee", RuleType.FIXED, new BigDecimal("100.00"), true
            );

            assertThatThrownBy(() -> ruleService.createRule(RESTAURANT_ID, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Compound flag is only applicable to PERCENTAGE rules");
        }

        @Test
        @DisplayName("Should enforce maximum 20 active rules per restaurant")
        void shouldEnforceMaxActiveRulesLimit() {
            when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
            when(ruleRepository.countActiveByRestaurantId(RESTAURANT_ID)).thenReturn(20L);

            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "قاعدة جديدة", "New Rule", RuleType.PERCENTAGE, new BigDecimal("5.00"), null
            );

            assertThatThrownBy(() -> ruleService.createRule(RESTAURANT_ID, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Maximum 20 active financial rules");
        }

        @Test
        @DisplayName("Should reject percentage value above 100")
        void shouldRejectPercentageAbove100() {
            when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
            when(ruleRepository.countActiveByRestaurantId(RESTAURANT_ID)).thenReturn(0L);

            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "ضريبة غير صالحة", "Invalid Tax", RuleType.PERCENTAGE, new BigDecimal("150.00"), null
            );

            assertThatThrownBy(() -> ruleService.createRule(RESTAURANT_ID, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Percentage value must not exceed 100.00");
        }

        @Test
        @DisplayName("Should reject zero value")
        void shouldRejectZeroValue() {
            when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
            when(ruleRepository.countActiveByRestaurantId(RESTAURANT_ID)).thenReturn(0L);

            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "صفر", "Zero", RuleType.FIXED, BigDecimal.ZERO, null
            );

            assertThatThrownBy(() -> ruleService.createRule(RESTAURANT_ID, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("value must be greater than 0");
        }

        @Test
        @DisplayName("Should reject negative value")
        void shouldRejectNegativeValue() {
            when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
            when(ruleRepository.countActiveByRestaurantId(RESTAURANT_ID)).thenReturn(0L);

            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "سالب", "Negative", RuleType.PERCENTAGE, new BigDecimal("-5.00"), null
            );

            assertThatThrownBy(() -> ruleService.createRule(RESTAURANT_ID, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("value must be greater than 0");
        }

        @Test
        @DisplayName("Should throw when restaurant not found")
        void shouldThrowWhenRestaurantNotFound() {
            when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "ضريبة", "Tax", RuleType.PERCENTAGE, new BigDecimal("10.00"), null
            );

            assertThatThrownBy(() -> ruleService.createRule(RESTAURANT_ID, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Restaurant not found");
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "ضريبة", "Tax", RuleType.PERCENTAGE, new BigDecimal("10.00"), null
            );

            assertThatThrownBy(() -> ruleService.createRule(null, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("restaurantId is required");
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when request is null")
        void shouldThrowWhenRequestNull() {
            assertThatThrownBy(() -> ruleService.createRule(RESTAURANT_ID, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Request body is required");
        }

        @Test
        @DisplayName("Should trim whitespace from name fields")
        void shouldTrimNameFields() {
            when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
            when(ruleRepository.countActiveByRestaurantId(RESTAURANT_ID)).thenReturn(0L);
            when(ruleRepository.findMaxApplyOrderByRestaurantId(RESTAURANT_ID)).thenReturn(0);
            when(ruleRepository.save(any())).thenAnswer(inv -> {
                RestaurantFinancialRule r = inv.getArgument(0);
                r.setId(1L);
                return r;
            });

            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "  ضريبة  ", "  VAT  ", RuleType.PERCENTAGE, new BigDecimal("10.00"), null
            );

            FinancialRuleDto result = ruleService.createRule(RESTAURANT_ID, request);

            verify(ruleRepository).save(ruleCaptor.capture());
            assertThat(ruleCaptor.getValue().getNameAr()).isEqualTo("ضريبة");
            assertThat(ruleCaptor.getValue().getNameEn()).isEqualTo("VAT");
        }

        @Test
        @DisplayName("Should handle null nameEn gracefully")
        void shouldHandleNullNameEn() {
            when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(testRestaurant));
            when(ruleRepository.countActiveByRestaurantId(RESTAURANT_ID)).thenReturn(0L);
            when(ruleRepository.findMaxApplyOrderByRestaurantId(RESTAURANT_ID)).thenReturn(0);
            when(ruleRepository.save(any())).thenAnswer(inv -> {
                RestaurantFinancialRule r = inv.getArgument(0);
                r.setId(1L);
                return r;
            });

            CreateFinancialRuleRequest request = new CreateFinancialRuleRequest(
                    "ضريبة", null, RuleType.PERCENTAGE, new BigDecimal("10.00"), null
            );

            FinancialRuleDto result = ruleService.createRule(RESTAURANT_ID, request);

            verify(ruleRepository).save(ruleCaptor.capture());
            assertThat(ruleCaptor.getValue().getNameEn()).isNull();
        }
    }

    // ==================== Update Rule Tests ====================

    @Nested
    @DisplayName("updateRule()")
    class UpdateRuleTests {

        @Test
        @DisplayName("Should update nameAr only (partial update)")
        void shouldUpdateNameArOnly() {
            RestaurantFinancialRule existing = createRule(1L, "قديم", "Old",
                    RuleType.PERCENTAGE, new BigDecimal("10.00"), 1, false, true);
            when(ruleRepository.findByIdAndRestaurantId(1L, RESTAURANT_ID))
                    .thenReturn(Optional.of(existing));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateFinancialRuleRequest request = new UpdateFinancialRuleRequest(
                    "جديد", null, null, null, null, null
            );

            FinancialRuleDto result = ruleService.updateRule(RESTAURANT_ID, 1L, request);

            assertThat(result.nameAr()).isEqualTo("جديد");
            assertThat(result.nameEn()).isEqualTo("Old"); // unchanged
            assertThat(result.ruleType()).isEqualTo(RuleType.PERCENTAGE); // unchanged
        }

        @Test
        @DisplayName("Should update value with validation")
        void shouldUpdateValueWithValidation() {
            RestaurantFinancialRule existing = createRule(1L, "ضريبة", "VAT",
                    RuleType.PERCENTAGE, new BigDecimal("10.00"), 1, false, true);
            when(ruleRepository.findByIdAndRestaurantId(1L, RESTAURANT_ID))
                    .thenReturn(Optional.of(existing));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateFinancialRuleRequest request = new UpdateFinancialRuleRequest(
                    null, null, null, new BigDecimal("15.00"), null, null
            );

            FinancialRuleDto result = ruleService.updateRule(RESTAURANT_ID, 1L, request);

            assertThat(result.value()).isEqualByComparingTo("15.00");
        }

        @Test
        @DisplayName("Should reject compound flag when switching to FIXED type")
        void shouldRejectCompoundWhenTypeFixed() {
            RestaurantFinancialRule existing = createRule(1L, "ضريبة", "Tax",
                    RuleType.FIXED, new BigDecimal("100.00"), 1, false, true);
            when(ruleRepository.findByIdAndRestaurantId(1L, RESTAURANT_ID))
                    .thenReturn(Optional.of(existing));

            UpdateFinancialRuleRequest request = new UpdateFinancialRuleRequest(
                    null, null, null, null, true, null
            );

            assertThatThrownBy(() -> ruleService.updateRule(RESTAURANT_ID, 1L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Compound flag is only applicable to PERCENTAGE rules");
        }

        @Test
        @DisplayName("Should activate rule while respecting limit")
        void shouldActivateRuleRespectingLimit() {
            RestaurantFinancialRule existing = createRule(1L, "ضريبة", "Tax",
                    RuleType.PERCENTAGE, new BigDecimal("10.00"), 1, false, false);
            when(ruleRepository.findByIdAndRestaurantId(1L, RESTAURANT_ID))
                    .thenReturn(Optional.of(existing));
            when(ruleRepository.countActiveByRestaurantId(RESTAURANT_ID)).thenReturn(20L);

            UpdateFinancialRuleRequest request = new UpdateFinancialRuleRequest(
                    null, null, null, null, null, true
            );

            assertThatThrownBy(() -> ruleService.updateRule(RESTAURANT_ID, 1L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Maximum 20 active financial rules");
        }

        @Test
        @DisplayName("Should allow deactivation even at max limit")
        void shouldAllowDeactivationAtMaxLimit() {
            RestaurantFinancialRule existing = createRule(1L, "ضريبة", "Tax",
                    RuleType.PERCENTAGE, new BigDecimal("10.00"), 1, false, true);
            when(ruleRepository.findByIdAndRestaurantId(1L, RESTAURANT_ID))
                    .thenReturn(Optional.of(existing));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateFinancialRuleRequest request = new UpdateFinancialRuleRequest(
                    null, null, null, null, null, false
            );

            FinancialRuleDto result = ruleService.updateRule(RESTAURANT_ID, 1L, request);

            assertThat(result.isActive()).isFalse();
        }

        @Test
        @DisplayName("Should throw when rule not found")
        void shouldThrowWhenRuleNotFound() {
            when(ruleRepository.findByIdAndRestaurantId(999L, RESTAURANT_ID))
                    .thenReturn(Optional.empty());

            UpdateFinancialRuleRequest request = new UpdateFinancialRuleRequest(
                    "test", null, null, null, null, null
            );

            assertThatThrownBy(() -> ruleService.updateRule(RESTAURANT_ID, 999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Financial rule not found");
        }

        @Test
        @DisplayName("Should throw when restaurantId or ruleId is null")
        void shouldThrowWhenIdsNull() {
            assertThatThrownBy(() -> ruleService.updateRule(null, 1L,
                    new UpdateFinancialRuleRequest(null, null, null, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("restaurantId and ruleId are required");

            assertThatThrownBy(() -> ruleService.updateRule(RESTAURANT_ID, null,
                    new UpdateFinancialRuleRequest(null, null, null, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("restaurantId and ruleId are required");
        }

        @Test
        @DisplayName("Should throw when request is null")
        void shouldThrowWhenRequestNull() {
            assertThatThrownBy(() -> ruleService.updateRule(RESTAURANT_ID, 1L, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Request body is required");
        }

        @Test
        @DisplayName("Should not save when no fields changed")
        void shouldNotSaveWhenNoFieldsChanged() {
            RestaurantFinancialRule existing = createRule(1L, "ضريبة", "VAT",
                    RuleType.PERCENTAGE, new BigDecimal("10.00"), 1, false, true);
            when(ruleRepository.findByIdAndRestaurantId(1L, RESTAURANT_ID))
                    .thenReturn(Optional.of(existing));

            // All fields null = no changes
            UpdateFinancialRuleRequest request = new UpdateFinancialRuleRequest(
                    null, null, null, null, null, null
            );

            ruleService.updateRule(RESTAURANT_ID, 1L, request);

            verify(ruleRepository, never()).save(any());
        }
    }

    // ==================== Delete (Soft-Delete) Rule Tests ====================

    @Nested
    @DisplayName("deleteRule()")
    class DeleteRuleTests {

        @Test
        @DisplayName("Should soft-delete (deactivate) an active rule")
        void shouldSoftDeleteActiveRule() {
            RestaurantFinancialRule rule = createRule(1L, "ضريبة", "VAT",
                    RuleType.PERCENTAGE, new BigDecimal("10.00"), 1, false, true);
            when(ruleRepository.findByIdAndRestaurantId(1L, RESTAURANT_ID))
                    .thenReturn(Optional.of(rule));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ruleService.deleteRule(RESTAURANT_ID, 1L);

            verify(ruleRepository).save(ruleCaptor.capture());
            assertThat(ruleCaptor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("Should be idempotent for already inactive rule")
        void shouldBeIdempotentForInactiveRule() {
            RestaurantFinancialRule rule = createRule(1L, "ضريبة", "VAT",
                    RuleType.PERCENTAGE, new BigDecimal("10.00"), 1, false, false);
            when(ruleRepository.findByIdAndRestaurantId(1L, RESTAURANT_ID))
                    .thenReturn(Optional.of(rule));

            ruleService.deleteRule(RESTAURANT_ID, 1L);

            // Should NOT call save again for already inactive rule
            verify(ruleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw when rule not found")
        void shouldThrowWhenRuleNotFound() {
            when(ruleRepository.findByIdAndRestaurantId(999L, RESTAURANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> ruleService.deleteRule(RESTAURANT_ID, 999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Financial rule not found");
        }

        @Test
        @DisplayName("Should throw when restaurantId or ruleId is null")
        void shouldThrowWhenIdsNull() {
            assertThatThrownBy(() -> ruleService.deleteRule(null, 1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("restaurantId and ruleId are required");

            assertThatThrownBy(() -> ruleService.deleteRule(RESTAURANT_ID, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("restaurantId and ruleId are required");
        }
    }

    // ==================== Reorder Rules Tests ====================

    @Nested
    @DisplayName("reorderRules()")
    class ReorderRulesTests {

        @Test
        @DisplayName("Should reorder rules based on provided ID list")
        void shouldReorderRules() {
            RestaurantFinancialRule rule1 = createRule(1L, "أول", "First",
                    RuleType.PERCENTAGE, new BigDecimal("10.00"), 1, false, true);
            RestaurantFinancialRule rule2 = createRule(2L, "ثاني", "Second",
                    RuleType.FIXED, new BigDecimal("500.00"), 2, false, true);
            RestaurantFinancialRule rule3 = createRule(3L, "ثالث", "Third",
                    RuleType.PERCENTAGE, new BigDecimal("5.00"), 3, true, true);

            when(ruleRepository.findByRestaurantIdOrderByApplyOrderAsc(RESTAURANT_ID))
                    .thenReturn(List.of(rule1, rule2, rule3));
            when(ruleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            // Reorder: 3, 1, 2
            ReorderFinancialRulesRequest request = new ReorderFinancialRulesRequest(List.of(3L, 1L, 2L));

            ruleService.reorderRules(RESTAURANT_ID, request);

            // Verify the new apply_order values
            assertThat(rule3.getApplyOrder()).isEqualTo(1);
            assertThat(rule1.getApplyOrder()).isEqualTo(2);
            assertThat(rule2.getApplyOrder()).isEqualTo(3);

            verify(ruleRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("Should throw when a ruleId is not found in the restaurant")
        void shouldThrowWhenRuleIdNotInRestaurant() {
            RestaurantFinancialRule rule1 = createRule(1L, "أول", "First",
                    RuleType.PERCENTAGE, new BigDecimal("10.00"), 1, false, true);

            when(ruleRepository.findByRestaurantIdOrderByApplyOrderAsc(RESTAURANT_ID))
                    .thenReturn(List.of(rule1));

            ReorderFinancialRulesRequest request = new ReorderFinancialRulesRequest(List.of(1L, 999L));

            assertThatThrownBy(() -> ruleService.reorderRules(RESTAURANT_ID, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("Should throw when restaurantId is null")
        void shouldThrowWhenRestaurantIdNull() {
            ReorderFinancialRulesRequest request = new ReorderFinancialRulesRequest(List.of(1L));

            assertThatThrownBy(() -> ruleService.reorderRules(null, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("restaurantId is required");
        }

        @Test
        @DisplayName("Should throw when ruleIds list is null or empty")
        void shouldThrowWhenRuleIdsNullOrEmpty() {
            assertThatThrownBy(() -> ruleService.reorderRules(RESTAURANT_ID,
                    new ReorderFinancialRulesRequest(null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ruleIds list is required");

            assertThatThrownBy(() -> ruleService.reorderRules(RESTAURANT_ID,
                    new ReorderFinancialRulesRequest(Collections.emptyList())))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ruleIds list is required");
        }

        @Test
        @DisplayName("Should throw when request is null")
        void shouldThrowWhenRequestNull() {
            assertThatThrownBy(() -> ruleService.reorderRules(RESTAURANT_ID, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ruleIds list is required");
        }
    }

    // ==================== DTO Mapping Tests ====================

    @Nested
    @DisplayName("DTO Mapping")
    class DtoMappingTests {

        @Test
        @DisplayName("Should map all entity fields to DTO correctly")
        void shouldMapAllFieldsToDto() {
            Instant now = Instant.now();
            RestaurantFinancialRule rule = createRule(42L, "ضريبة القيمة المضافة", "VAT",
                    RuleType.PERCENTAGE, new BigDecimal("10.0000"), 3, true, true);
            rule.setCreatedAt(now);
            rule.setUpdatedAt(now);

            when(ruleRepository.findByRestaurantIdOrderByApplyOrderAsc(RESTAURANT_ID))
                    .thenReturn(List.of(rule));

            FinancialRulesResponse response = ruleService.listRules(RESTAURANT_ID);
            FinancialRuleDto dto = response.rules().get(0);

            assertThat(dto.id()).isEqualTo(42L);
            assertThat(dto.nameAr()).isEqualTo("ضريبة القيمة المضافة");
            assertThat(dto.nameEn()).isEqualTo("VAT");
            assertThat(dto.ruleType()).isEqualTo(RuleType.PERCENTAGE);
            assertThat(dto.value()).isEqualByComparingTo("10.0000");
            assertThat(dto.applyOrder()).isEqualTo(3);
            assertThat(dto.isCompound()).isTrue();
            assertThat(dto.isActive()).isTrue();
            assertThat(dto.createdAt()).isEqualTo(now);
            assertThat(dto.updatedAt()).isEqualTo(now);
        }
    }
}

