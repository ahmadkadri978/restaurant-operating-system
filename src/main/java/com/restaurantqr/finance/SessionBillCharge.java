package com.restaurantqr.finance;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "session_bill_charges")
public class SessionBillCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_bill_id")
    private SessionBill sessionBill;

    @Column(name = "rule_name_ar", nullable = false)
    private String ruleNameAr;

    @Column(name = "rule_name_en")
    private String ruleNameEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @Column(name = "rule_value", nullable = false, precision = 10, scale = 4)
    private BigDecimal ruleValue;

    @Column(name = "is_compound", nullable = false)
    private boolean isCompound = false;

    @Column(name = "apply_order", nullable = false)
    private Integer applyOrder = 0;

    @Column(name = "computed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal computedAmount;

    @Column(name = "source_rule_id")
    private Long sourceRuleId;

    public SessionBillCharge() {
    }

    /**
     * Create a charge from a financial rule snapshot.
     */
    public static SessionBillCharge fromRule(SessionBill bill, RestaurantFinancialRule rule, BigDecimal computedAmount) {
        SessionBillCharge charge = new SessionBillCharge();
        charge.setSessionBill(bill);
        charge.setRuleNameAr(rule.getNameAr());
        charge.setRuleNameEn(rule.getNameEn());
        charge.setRuleType(rule.getRuleType());
        charge.setRuleValue(rule.getValue());
        charge.setCompound(rule.isCompound());
        charge.setApplyOrder(rule.getApplyOrder());
        charge.setComputedAmount(computedAmount);
        charge.setSourceRuleId(rule.getId());
        return charge;
    }

    // ==================== Getters & Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SessionBill getSessionBill() { return sessionBill; }
    public void setSessionBill(SessionBill sessionBill) { this.sessionBill = sessionBill; }

    public String getRuleNameAr() { return ruleNameAr; }
    public void setRuleNameAr(String ruleNameAr) { this.ruleNameAr = ruleNameAr; }

    public String getRuleNameEn() { return ruleNameEn; }
    public void setRuleNameEn(String ruleNameEn) { this.ruleNameEn = ruleNameEn; }

    public RuleType getRuleType() { return ruleType; }
    public void setRuleType(RuleType ruleType) { this.ruleType = ruleType; }

    public BigDecimal getRuleValue() { return ruleValue; }
    public void setRuleValue(BigDecimal ruleValue) { this.ruleValue = ruleValue; }

    public boolean isCompound() { return isCompound; }
    public void setCompound(boolean compound) { isCompound = compound; }

    public Integer getApplyOrder() { return applyOrder; }
    public void setApplyOrder(Integer applyOrder) { this.applyOrder = applyOrder; }

    public BigDecimal getComputedAmount() { return computedAmount; }
    public void setComputedAmount(BigDecimal computedAmount) { this.computedAmount = computedAmount; }

    public Long getSourceRuleId() { return sourceRuleId; }
    public void setSourceRuleId(Long sourceRuleId) { this.sourceRuleId = sourceRuleId; }
}

