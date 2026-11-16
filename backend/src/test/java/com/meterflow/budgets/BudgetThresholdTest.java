package com.meterflow.budgets;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BudgetThresholdTest {
    @Test
    void alertsFireOncePerThresholdInOrder() {
        var budget = new UsageBudget("c", "2026-11", new BigDecimal("1000"), "BDT");
        assertEquals(List.of(), BudgetService.newlyCrossed(new BigDecimal("499.99"), budget.getAmount(), budget));
        assertEquals(List.of(50), BudgetService.newlyCrossed(new BigDecimal("500"), budget.getAmount(), budget));
        budget.mark(50);
        assertEquals(List.of(), BudgetService.newlyCrossed(new BigDecimal("700"), budget.getAmount(), budget));
        assertEquals(List.of(80, 100), BudgetService.newlyCrossed(new BigDecimal("1200"), budget.getAmount(), budget));
        budget.mark(80);
        budget.mark(100);
        assertEquals(List.of(), BudgetService.newlyCrossed(new BigDecimal("5000"), budget.getAmount(), budget));
        assertEquals(500, BudgetService.percentUsed(new BigDecimal("5000"), budget.getAmount()));
    }

    @Test
    void changingTheBudgetResetsAlerts() {
        var budget = new UsageBudget("c", "2026-11", new BigDecimal("100"), "BDT");
        budget.mark(50);
        budget.update(new BigDecimal("200"));
        assertFalse(budget.wasNotified(50));
    }
}
