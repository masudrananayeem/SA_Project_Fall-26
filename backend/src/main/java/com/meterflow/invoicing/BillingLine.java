package com.meterflow.invoicing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Element of the invoice object structure for the Visitor pattern. Tax calculation, rendering and
 * persistence mapping are visitors, so adding a new output format does not change the lines.
 */
public sealed interface BillingLine permits BillingLine.UsageCharge, BillingLine.Proration, BillingLine.LateUsage {

    BigDecimal amount();

    String description();

    <R> R accept(BillingLineVisitor<R> visitor);

    /** One priced charge of the plan for this period's usage (flat fee, per unit, tier, seat, ...). */
    record UsageCharge(String metric, String chargeName, String chargeType, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal amount, String rule, List<UUID> sourceEventIds, int sourceEventCount) implements BillingLine {
        public UsageCharge {
            amount = amount.setScale(2, RoundingMode.HALF_UP);
            sourceEventIds = sourceEventIds == null ? List.of() : List.copyOf(sourceEventIds);
        }
        public String description() { return chargeName; }
        public <R> R accept(BillingLineVisitor<R> visitor) { return visitor.visitUsage(this); }
    }

    /** Mid-cycle plan change adjustment (positive = charge, negative = credit). */
    record Proration(UUID planChangeId, BigDecimal amount, String rule) implements BillingLine {
        public Proration { amount = amount.setScale(2, RoundingMode.HALF_UP); }
        public String description() { return "Plan change proration"; }
        public <R> R accept(BillingLineVisitor<R> visitor) { return visitor.visitProration(this); }
    }

    /** Usage that arrived within 72 hours after an earlier period was invoiced. */
    record LateUsage(String originalPeriod, String metric, BigDecimal lateQuantity, BigDecimal amount, String rule,
                     List<UUID> sourceEventIds) implements BillingLine {
        public LateUsage {
            amount = amount.setScale(2, RoundingMode.HALF_UP);
            sourceEventIds = sourceEventIds == null ? List.of() : List.copyOf(sourceEventIds);
        }
        public String description() { return "Late usage adjustment for " + originalPeriod; }
        public <R> R accept(BillingLineVisitor<R> visitor) { return visitor.visitLateUsage(this); }
    }
}
