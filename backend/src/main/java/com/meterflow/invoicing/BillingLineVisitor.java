package com.meterflow.invoicing;

/** Visitor over invoice lines (tax, rendering, persistence mapping). */
public interface BillingLineVisitor<R> {
    R visitUsage(BillingLine.UsageCharge line);

    R visitProration(BillingLine.Proration line);

    R visitLateUsage(BillingLine.LateUsage line);
}
