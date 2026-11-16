package com.meterflow.metering;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Ledger row for an event accepted after its billing month closed (within the 72-hour window).
 *
 * <ul>
 *   <li>PENDING - not billed yet.</li>
 *   <li>INCLUDED - the event arrived before that month's invoice was generated, so the original
 *       invoice already counted it (no adjustment needed).</li>
 *   <li>INVOICED - billed as a late-usage adjustment line on a later invoice.</li>
 *   <li>IGNORED - the event's metric is not billable on the customer's plan.</li>
 * </ul>
 */
@Entity
@Table(name = "late_usage_adjustments", uniqueConstraints = @UniqueConstraint(columnNames = "usage_event_id"))
public class LateUsageAdjustment {
    public static final String PENDING = "PENDING";
    public static final String INCLUDED = "INCLUDED";
    public static final String INVOICED = "INVOICED";
    public static final String IGNORED = "IGNORED";

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "usage_event_id", nullable = false, unique = true) private UUID usageEventId;
    @Column(nullable = false) private String customerId;
    @Column(nullable = false) private String billingPeriod;
    @Column(nullable = false) private String status = PENDING;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "invoice_id") private UUID invoiceId;
    @Column(name = "resolved_at") private Instant resolvedAt;

    protected LateUsageAdjustment() { }

    public LateUsageAdjustment(UUID usageEventId, String customerId, String billingPeriod) {
        this.usageEventId = usageEventId;
        this.customerId = customerId;
        this.billingPeriod = billingPeriod;
    }

    public UUID getId() { return id; }
    public UUID getUsageEventId() { return usageEventId; }
    public String getCustomerId() { return customerId; }
    public String getBillingPeriod() { return billingPeriod; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getInvoiceId() { return invoiceId; }
    public Instant getResolvedAt() { return resolvedAt; }
    public boolean isPending() { return PENDING.equals(status); }

    private void resolve(String next, UUID invoice) {
        if (!isPending()) throw new IllegalStateException("Late usage entry " + id + " is already " + status);
        status = next;
        invoiceId = invoice;
        resolvedAt = Instant.now();
    }

    public void markIncludedInOriginal(UUID originalInvoiceId) { resolve(INCLUDED, originalInvoiceId); }
    public void markInvoiced(UUID adjustmentInvoiceId) { resolve(INVOICED, adjustmentInvoiceId); }
    public void markIgnored() { resolve(IGNORED, null); }
}
