package com.restaurantqr.finance;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.*;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FinancialRuleServiceImpl implements FinancialRuleService {

    private static final Logger log = LoggerFactory.getLogger(FinancialRuleServiceImpl.class);

    private static final int MAX_ACTIVE_RULES_PER_RESTAURANT = 20;
    private static final BigDecimal MAX_PERCENTAGE_VALUE = new BigDecimal("100.00");

    private final RestaurantFinancialRuleRepository ruleRepository;
    private final RestaurantRepository restaurantRepository;

    public FinancialRuleServiceImpl(RestaurantFinancialRuleRepository ruleRepository,
                                    RestaurantRepository restaurantRepository) {
        this.ruleRepository = ruleRepository;
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialRulesResponse listRules(Long restaurantId) {
        if (restaurantId == null) {
            throw new BusinessRuleException("restaurantId is required");
        }

        log.debug("List financial rules | restaurantId={}", restaurantId);

        List<RestaurantFinancialRule> rules = ruleRepository.findByRestaurantIdOrderByApplyOrderAsc(restaurantId);

        List<FinancialRuleDto> dtos = rules.stream()
                .map(this::toDto)
                .toList();

        return new FinancialRulesResponse(restaurantId, dtos);
    }

    @Override
    @Transactional
    public FinancialRuleDto createRule(Long restaurantId, CreateFinancialRuleRequest request) {
        if (restaurantId == null) {
            throw new BusinessRuleException("restaurantId is required");
        }
        if (request == null) {
            throw new BusinessRuleException("Request body is required");
        }

        log.info("Create financial rule | restaurantId={} | nameAr={} | type={} | value={}",
                restaurantId, request.nameAr(), request.ruleType(), request.value());

        // Validate restaurant exists
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));

        // Enforce maximum active rules limit
        Long activeCount = ruleRepository.countActiveByRestaurantId(restaurantId);
        if (activeCount >= MAX_ACTIVE_RULES_PER_RESTAURANT) {
            throw new BusinessRuleException(
                    "Maximum " + MAX_ACTIVE_RULES_PER_RESTAURANT + " active financial rules allowed per restaurant"
            );
        }

        // Validate percentage range
        validateRuleValue(request.ruleType(), request.value());

        // Auto-assign apply_order to next available slot
        Integer maxOrder = ruleRepository.findMaxApplyOrderByRestaurantId(restaurantId);
        int nextOrder = maxOrder + 1;

        boolean compound = request.isCompound() != null && request.isCompound();
        // Compound only makes sense for PERCENTAGE rules
        if (request.ruleType() == RuleType.FIXED && compound) {
            throw new BusinessRuleException("Compound flag is only applicable to PERCENTAGE rules");
        }

        RestaurantFinancialRule rule = new RestaurantFinancialRule(
                restaurant,
                request.nameAr().trim(),
                request.nameEn() != null ? request.nameEn().trim() : null,
                request.ruleType(),
                request.value(),
                nextOrder,
                compound
        );

        RestaurantFinancialRule saved = ruleRepository.save(rule);

        log.info("Financial rule created | ruleId={} | restaurantId={} | nameAr={} | type={} | value={} | order={}",
                saved.getId(), restaurantId, saved.getNameAr(), saved.getRuleType(), saved.getValue(), saved.getApplyOrder());

        return toDto(saved);
    }

    @Override
    @Transactional
    public FinancialRuleDto updateRule(Long restaurantId, Long ruleId, UpdateFinancialRuleRequest request) {
        if (restaurantId == null || ruleId == null) {
            throw new BusinessRuleException("restaurantId and ruleId are required");
        }
        if (request == null) {
            throw new BusinessRuleException("Request body is required");
        }

        log.info("Update financial rule | restaurantId={} | ruleId={}", restaurantId, ruleId);

        RestaurantFinancialRule rule = ruleRepository.findByIdAndRestaurantId(ruleId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Financial rule not found: " + ruleId));

        // Apply partial updates (only non-null fields)
        boolean changed = false;

        if (request.nameAr() != null && !request.nameAr().isBlank()) {
            rule.setNameAr(request.nameAr().trim());
            changed = true;
        }

        if (request.nameEn() != null) {
            rule.setNameEn(request.nameEn().trim().isEmpty() ? null : request.nameEn().trim());
            changed = true;
        }

        if (request.ruleType() != null) {
            rule.setRuleType(request.ruleType());
            changed = true;
        }

        if (request.value() != null) {
            // Validate against the effective rule type (may have been changed above)
            validateRuleValue(rule.getRuleType(), request.value());
            rule.setValue(request.value());
            changed = true;
        }

        if (request.isCompound() != null) {
            if (rule.getRuleType() == RuleType.FIXED && request.isCompound()) {
                throw new BusinessRuleException("Compound flag is only applicable to PERCENTAGE rules");
            }
            rule.setCompound(request.isCompound());
            changed = true;
        }

        if (request.isActive() != null) {
            // If activating, check limit
            if (request.isActive() && !rule.isActive()) {
                Long activeCount = ruleRepository.countActiveByRestaurantId(restaurantId);
                if (activeCount >= MAX_ACTIVE_RULES_PER_RESTAURANT) {
                    throw new BusinessRuleException(
                            "Maximum " + MAX_ACTIVE_RULES_PER_RESTAURANT + " active financial rules allowed"
                    );
                }
            }
            rule.setActive(request.isActive());
            changed = true;
        }

        if (changed) {
            rule.setUpdatedAt(Instant.now());
            ruleRepository.save(rule);
            log.info("Financial rule updated | ruleId={} | restaurantId={}", ruleId, restaurantId);
        }

        return toDto(rule);
    }

    @Override
    @Transactional
    public void deleteRule(Long restaurantId, Long ruleId) {
        if (restaurantId == null || ruleId == null) {
            throw new BusinessRuleException("restaurantId and ruleId are required");
        }

        log.info("Delete (deactivate) financial rule | restaurantId={} | ruleId={}", restaurantId, ruleId);

        RestaurantFinancialRule rule = ruleRepository.findByIdAndRestaurantId(ruleId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Financial rule not found: " + ruleId));

        if (!rule.isActive()) {
            log.info("Financial rule already inactive (idempotent) | ruleId={}", ruleId);
            return;
        }

        rule.setActive(false);
        rule.setUpdatedAt(Instant.now());
        ruleRepository.save(rule);

        log.info("Financial rule deactivated | ruleId={} | restaurantId={}", ruleId, restaurantId);
    }

    @Override
    @Transactional
    public FinancialRulesResponse reorderRules(Long restaurantId, ReorderFinancialRulesRequest request) {
        if (restaurantId == null) {
            throw new BusinessRuleException("restaurantId is required");
        }
        if (request == null || request.ruleIds() == null || request.ruleIds().isEmpty()) {
            throw new BusinessRuleException("ruleIds list is required and must not be empty");
        }

        log.info("Reorder financial rules | restaurantId={} | count={}", restaurantId, request.ruleIds().size());

        // Load all rules for the restaurant
        List<RestaurantFinancialRule> allRules = ruleRepository.findByRestaurantIdOrderByApplyOrderAsc(restaurantId);
        Map<Long, RestaurantFinancialRule> rulesById = allRules.stream()
                .collect(Collectors.toMap(RestaurantFinancialRule::getId, r -> r));

        // Validate that all provided IDs belong to this restaurant
        for (Long id : request.ruleIds()) {
            if (!rulesById.containsKey(id)) {
                throw new ResourceNotFoundException("Financial rule not found in this restaurant: " + id);
            }
        }

        // Update apply_order based on position in the list
        Instant now = Instant.now();
        for (int i = 0; i < request.ruleIds().size(); i++) {
            Long id = request.ruleIds().get(i);
            RestaurantFinancialRule rule = rulesById.get(id);
            rule.setApplyOrder(i + 1);
            rule.setUpdatedAt(now);
        }

        ruleRepository.saveAll(allRules);

        log.info("Financial rules reordered | restaurantId={} | count={}", restaurantId, request.ruleIds().size());

        // Return updated list
        return listRules(restaurantId);
    }

    // ==================== Private helpers ====================

    private void validateRuleValue(RuleType ruleType, BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("value must be greater than 0");
        }
        if (ruleType == RuleType.PERCENTAGE && value.compareTo(MAX_PERCENTAGE_VALUE) > 0) {
            throw new BusinessRuleException("Percentage value must not exceed 100.00");
        }
    }

    private FinancialRuleDto toDto(RestaurantFinancialRule rule) {
        return new FinancialRuleDto(
                rule.getId(),
                rule.getNameAr(),
                rule.getNameEn(),
                rule.getRuleType(),
                rule.getValue(),
                rule.getApplyOrder(),
                rule.isCompound(),
                rule.isActive(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}

