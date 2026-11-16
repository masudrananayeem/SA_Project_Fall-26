package com.meterflow.invoicing;

import com.meterflow.shared.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Immutable invoice before persistence (built by {@link InvoiceBuilder}). Amounts are combined
 * through the {@link Money} value object, so a currency mix-up fails instead of being summed.
 */
public record InvoiceDocument(String customerId, String period, String currency, UUID planId, String planLabel,
                              BigDecimal billedQuantity, int seatCount, List<BillingLine> lines,
                              BigDecimal subtotal, BigDecimal tax, String taxRule, BigDecimal creditApplied, BigDecimal total) {
    public InvoiceDocument {
        lines = List.copyOf(lines);
    }

    /** Subtotal + tax, before prepaid credit. */
    public BigDecimal grossTotal() { return Money.of(subtotal, currency).add(Money.of(tax, currency)).amount(); }

    public InvoiceDocument withCreditApplied(BigDecimal credit) {
        if (credit.signum() < 0) throw new IllegalArgumentException("Credit applied cannot be negative");
        if (credit.compareTo(grossTotal().max(BigDecimal.ZERO)) > 0) throw new IllegalArgumentException("Credit cannot exceed the amount due");
        BigDecimal due = Money.of(grossTotal(), currency).subtract(Money.of(credit, currency)).amount();
        return new InvoiceDocument(customerId, period, currency, planId, planLabel, billedQuantity, seatCount, lines,
                subtotal, tax, taxRule, Money.of(credit, currency).amount(), due);
    }
}
