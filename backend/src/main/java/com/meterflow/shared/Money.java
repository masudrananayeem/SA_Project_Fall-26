package com.meterflow.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public record Money(BigDecimal amount, Currency currency) {
  public Money {
    if (amount == null || currency == null) throw new IllegalArgumentException("Amount and currency are required");
    amount = amount.setScale(currency.getCurrencyCode().equals("BDT") ? 2 : 2, RoundingMode.HALF_UP);
  }
  public Money add(Money other) { same(other); return new Money(amount.add(other.amount), currency); }
  public Money subtract(Money other) { same(other); return new Money(amount.subtract(other.amount), currency); }
  public Money multiply(BigDecimal factor) { return new Money(amount.multiply(factor), currency); }
  private void same(Money other) { if (!currency.equals(other.currency)) throw new IllegalArgumentException("Currency mismatch"); }
}
