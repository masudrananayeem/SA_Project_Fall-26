package com.meterflow.invoicing;

import java.math.BigDecimal;
import java.util.UUID;

/** Visitor that maps invoice lines to persistent {@link InvoiceLine} rows. */
final class InvoiceLineMapper implements BillingLineVisitor<InvoiceLine> {
    private final UUID invoiceId;
    private final String taxRule;

    InvoiceLineMapper(UUID invoiceId, String taxRule) {
        this.invoiceId = invoiceId;
        this.taxRule = taxRule;
    }

    @Override
    public InvoiceLine visitUsage(BillingLine.UsageCharge l) {
        return new InvoiceLine(invoiceId, l.metric(), l.quantity(), l.unitPrice(), l.amount(), l.rule(), taxRule,
                InvoiceLine.idsToJson(l.sourceEventIds()), l.sourceEventCount())
                .kind(InvoiceLine.USAGE, l.chargeName(), l.chargeType());
    }

    @Override
    public InvoiceLine visitProration(BillingLine.Proration l) {
        return new InvoiceLine(invoiceId, "proration", BigDecimal.ONE, l.amount(), l.amount(), l.rule(), taxRule)
                .kind(InvoiceLine.PRORATION, l.description(), l.planChangeId() == null ? null : l.planChangeId().toString());
    }

    @Override
    public InvoiceLine visitLateUsage(BillingLine.LateUsage l) {
        return new InvoiceLine(invoiceId, l.metric(), l.lateQuantity(), BigDecimal.ZERO, l.amount(), l.rule(), taxRule,
                InvoiceLine.idsToJson(l.sourceEventIds()), l.sourceEventIds().size())
                .kind(InvoiceLine.LATE_USAGE, l.description(), l.originalPeriod());
    }
}
