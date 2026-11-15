package com.meterflow.budgets;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class BudgetService {
    private final UsageBudgetRepository repo;
    private final com.meterflow.metering.UsageService usage;
    private final com.meterflow.notifications.WebhookDeliveryService webhooks;

    public BudgetService(UsageBudgetRepository r, com.meterflow.metering.UsageService u,
                         com.meterflow.notifications.WebhookDeliveryService w) {
        repo = r; usage = u; webhooks = w;
    }

    @Transactional
    public UsageBudget set(String customer, String period, BigDecimal amount, String currency) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Budget must be positive");
        YearMonth.parse(period);
        if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a three-letter ISO code");
        var budget = repo.findByCustomerIdAndPeriod(customer, period).orElseGet(() -> new UsageBudget(customer, period, amount, currency));
        if (!budget.getCurrency().equals(currency)) throw new IllegalArgumentException("Budget currency cannot change within a period");
        budget.update(amount);
        return repo.save(budget);
    }

    @Transactional
    public Map<String, Object> evaluateIfConfigured(String customer, String period) {
        if (repo.findByCustomerIdAndPeriod(customer, period).isEmpty()) return Map.of("status", "NO_BUDGET");
        return evaluate(customer, period);
    }

    @Transactional
    public Map<String, Object> evaluate(String customer, String period) {
        YearMonth.parse(period);
        var budget = repo.findByCustomerIdAndPeriod(customer, period).orElseThrow(() -> new IllegalArgumentException("Budget not configured"));
        var estimate = usage.estimate(customer, period);
        BigDecimal amount = new BigDecimal(estimate.get("estimatedAmount").toString());
        if (!budget.getCurrency().equals(estimate.get("currency"))) throw new IllegalStateException("Budget and plan currency mismatch");
        int percent = amount.multiply(BigDecimal.valueOf(100)).divide(budget.getAmount(), 0, RoundingMode.DOWN).intValue();
        List<Integer> fired = new ArrayList<>();
        for (int threshold : new int[]{50, 80, 100}) {
            if (percent >= threshold && !budget.wasNotified(threshold)) {
                budget.mark(threshold); fired.add(threshold);
                webhooks.publish(customer, "usage.threshold_reached", Map.of("customerId", customer, "period", period,
                        "threshold", threshold, "estimatedAmount", amount, "budget", budget.getAmount()));
            }
        }
        repo.save(budget);
        return Map.of("customerId", customer, "period", period, "budget", budget.getAmount(),
                "estimatedAmount", amount, "percentUsed", percent, "newAlerts", fired);
    }

    /** Re-evaluate current-period budgets every five minutes; persisted threshold flags prevent duplicate alerts. */
    @Scheduled(fixedDelayString = "${meterflow.budgets.refresh-ms:300000}", initialDelayString = "${meterflow.budgets.initial-delay-ms:30000}")
    public void refreshCurrentPeriodBudgets() {
        String period = YearMonth.now(ZoneOffset.UTC).toString();
        for (var budget : repo.findByPeriod(period)) {
            try { evaluateIfConfigured(budget.getCustomerId(), period); }
            catch (RuntimeException ignored) { /* One misconfigured customer must not block other budget checks. */ }
        }
    }
}
