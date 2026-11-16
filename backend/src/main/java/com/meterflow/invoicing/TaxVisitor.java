package com.meterflow.invoicing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Visitor that computes the taxable base of each line under the configured tax rule (for example
 * VAT 15%). Usage charges, prorations and late-usage adjustments are taxable supplies; a negative
 * proration (downgrade credit) reduces the base. Prepaid credit is a means of payment and is applied
 * after tax, so it does not appear here.
 */
public final class TaxVisitor implements BillingLineVisitor<BigDecimal> {
    private final BigDecimal ratePercent;
    private final String ruleName;

    public TaxVisitor(BigDecimal ratePercent, String ruleName) {
        this.ratePercent = ratePercent == null ? BigDecimal.ZERO : ratePercent;
        this.ruleName = ruleName == null ? "VAT" : ruleName;
    }

    public static TaxVisitor vat(BigDecimal ratePercent) { return new TaxVisitor(ratePercent, "VAT"); }

    @Override public BigDecimal visitUsage(BillingLine.UsageCharge line) { return line.amount(); }
    @Override public BigDecimal visitProration(BillingLine.Proration line) { return line.amount(); }
    @Override public BigDecimal visitLateUsage(BillingLine.LateUsage line) { return line.amount(); }

    public BigDecimal taxableBase(List<BillingLine> lines) {
        return lines.stream().map(l -> l.accept(this)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** rate% of the taxable base, rounded HALF_UP to 2 places once per invoice. Never negative. */
    public BigDecimal tax(List<BillingLine> lines) {
        BigDecimal base = taxableBase(lines).max(BigDecimal.ZERO);
        return base.multiply(ratePercent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    public String describe() { return ruleName + " " + ratePercent.stripTrailingZeros().toPlainString() + "% on taxable charges"; }

    public BigDecimal ratePercent() { return ratePercent; }
}
