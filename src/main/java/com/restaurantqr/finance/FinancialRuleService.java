package com.restaurantqr.finance;

import com.restaurantqr.finance.dto.*;

public interface FinancialRuleService {

    /**
     * List all financial rules for a restaurant (including inactive).
     */
    FinancialRulesResponse listRules(Long restaurantId);

    /**
     * Create a new financial rule for a restaurant.
     */
    FinancialRuleDto createRule(Long restaurantId, CreateFinancialRuleRequest request);

    /**
     * Update an existing financial rule.
     */
    FinancialRuleDto updateRule(Long restaurantId, Long ruleId, UpdateFinancialRuleRequest request);

    /**
     * Soft-delete (deactivate) a financial rule.
     */
    void deleteRule(Long restaurantId, Long ruleId);

    /**
     * Reorder financial rules (batch update apply_order).
     */
    FinancialRulesResponse reorderRules(Long restaurantId, ReorderFinancialRulesRequest request);
}

