package com.meterflow.budgets;

import java.math.BigDecimal;

/** Published once per customer, period and threshold (50, 80 or 100 percent). */
public record BudgetThresholdReached(String customerId, String period, int thresholdPercent,
                                     BigDecimal estimatedAmount, BigDecimal budget, String currency) { }
