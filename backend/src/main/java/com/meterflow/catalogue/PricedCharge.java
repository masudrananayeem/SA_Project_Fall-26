package com.meterflow.catalogue;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One explainable result of pricing a single charge. Amounts are rounded to the paisa/cent
 * (2 decimal places, HALF_UP) per charge; the invoice subtotal is the sum of these lines.
 */
public record PricedCharge(String name, String type, BigDecimal quantity, BigDecimal unitPrice,
                           BigDecimal amount, String rule) {
    public PricedCharge {
        if (name == null || type == null || quantity == null || amount == null || rule == null)
            throw new IllegalArgumentException("Priced charge fields are required");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
    }

    /** Applies a factor (for example a partial trial month) and re-rounds the line. */
    public PricedCharge scaled(BigDecimal factor, String note) {
        return new PricedCharge(name, type, quantity, unitPrice, amount.multiply(factor), rule + "; " + note);
    }
}
