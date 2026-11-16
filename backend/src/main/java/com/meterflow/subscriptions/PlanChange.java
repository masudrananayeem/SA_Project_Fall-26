package com.meterflow.subscriptions;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Audit + proration record of a plan change. proratedDelta is posted as a line on the invoice of
 * the month in which the change took effect; invoiceId is set when it has been invoiced.
 */
@Entity
@Table(name = "subscription_plan_changes")
public class PlanChange {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID subscriptionId;
    @Column(nullable = false) private UUID fromPlanId;
    @Column(nullable = false) private UUID toPlanId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal proratedDelta;
    @Column(nullable = false) private String currency;
    @Column(nullable = false) private LocalDate effectiveDate;
    @org.hibernate.annotations.ColumnDefault("'IMMEDIATE'") @Column(nullable = false, length = 40) private String kind = "IMMEDIATE";
    @Column(length = 500) private String explanation;
    @Column(name = "invoice_id") private UUID invoiceId;

    protected PlanChange() { }

    public PlanChange(UUID subscriptionId, UUID fromPlanId, UUID toPlanId, BigDecimal delta, String currency, LocalDate effectiveDate) {
        this(subscriptionId, fromPlanId, toPlanId, delta, currency, effectiveDate, "IMMEDIATE", null);
    }

    public PlanChange(UUID subscriptionId, UUID fromPlanId, UUID toPlanId, BigDecimal delta, String currency,
                      LocalDate effectiveDate, String kind, String explanation) {
        this.subscriptionId = subscriptionId;
        this.fromPlanId = fromPlanId;
        this.toPlanId = toPlanId;
        this.proratedDelta = delta;
        this.currency = currency;
        this.effectiveDate = effectiveDate;
        this.kind = kind;
        this.explanation = explanation;
    }

    public UUID getId() { return id; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getFromPlanId() { return fromPlanId; }
    public UUID getToPlanId() { return toPlanId; }
    public BigDecimal getProratedDelta() { return proratedDelta; }
    public String getCurrency() { return currency; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public String getKind() { return kind; }
    public String getExplanation() { return explanation; }
    public UUID getInvoiceId() { return invoiceId; }

    public void markInvoiced(UUID invoice) {
        if (invoiceId != null && !invoiceId.equals(invoice)) throw new IllegalStateException("Plan change already invoiced");
        invoiceId = invoice;
    }
}
