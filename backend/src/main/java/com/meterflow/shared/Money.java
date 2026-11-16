package com.meterflow.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Money value object: an amount in a single currency, rounded to the currency's minor unit
 * (paisa for BDT, cent for USD). Arithmetic across currencies is rejected.
 */
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if (amount == null || currency == null) throw new IllegalArgumentException("Amount and currency are required");
        int digits = Math.max(0, currency.getDefaultFractionDigits());
        amount = amount.setScale(digits, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, String currencyCode) { return new Money(amount, Currency.getInstance(currencyCode)); }

    public static Money zero(String currencyCode) { return of(BigDecimal.ZERO, currencyCode); }

    public Money add(Money other) { same(other); return new Money(amount.add(other.amount), currency); }

    public Money subtract(Money other) { same(other); return new Money(amount.subtract(other.amount), currency); }

    public Money multiply(BigDecimal factor) { return new Money(amount.multiply(factor), currency); }

    public boolean isNegative() { return amount.signum() < 0; }

    private void same(Money other) {
        if (!currency.equals(other.currency)) throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
    }
}
