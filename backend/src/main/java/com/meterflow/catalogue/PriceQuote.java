package com.meterflow.catalogue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pricing result for one plan version and one quantity. Used by both the live estimate and the
 * billing run, so the estimate equals the invoice charges for the same events (QA5).
 */
public record PriceQuote(String planKey, int planVersion, String currency, List<PricedCharge> lines, BigDecimal subtotal) {
    public PriceQuote {
        lines = List.copyOf(lines);
        subtotal = lines.stream().map(PricedCharge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public PriceQuote(String planKey, int planVersion, String currency, List<PricedCharge> lines) {
        this(planKey, planVersion, currency, lines, BigDecimal.ZERO);
    }

    public String planLabel() { return planKey + " v" + planVersion; }

    /** Scales every line by numerator/denominator, for example billable days / days in month. */
    public PriceQuote prorated(long numerator, long denominator, String note) {
        if (denominator <= 0) throw new IllegalArgumentException("denominator must be positive");
        BigDecimal factor = BigDecimal.valueOf(Math.max(0, numerator))
                .divide(BigDecimal.valueOf(denominator), new MathContext(20, RoundingMode.HALF_UP));
        return new PriceQuote(planKey, planVersion, currency, lines.stream().map(l -> l.scaled(factor, note)).toList());
    }
}
