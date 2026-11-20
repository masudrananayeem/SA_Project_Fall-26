package com.meterflow.invoicing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Balance due after a mid-month upgrade in a period that was already invoiced (a prepaid package
 * paid up front). It holds what was already invoiced for the month, the new plan's price for the
 * remaining days, and the difference the customer still has to pay. The month's invoice is never
 * changed and never duplicated: this is a separate, traceable charge.
 */
@Entity
@Table(name = "upgrade_charges",
        uniqueConstraints = @UniqueConstraint(name = "uk_upgrade_charge_plan_change", columnNames = "plan_change_id"),
        indexes = @Index(name = "idx_upgrade_charges_customer", columnList = "customer_id, created_at"))
public class UpgradeCharge {
    public static final String DUE = "DUE";
    public static final String PAID = "PAID";
    public static final String WAIVED = "WAIVED";

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "customer_id", nullable = false) private String customerId;
    @Column(name = "subscription_id", nullable = false) private UUID subscriptionId;
    @Column(name = "plan_change_id", nullable = false) private UUID planChangeId;
    @Column(name = "invoice_id") private UUID invoiceId;
    @Column(nullable = false, length = 7) private String period;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "from_plan", nullable = false, length = 160) private String fromPlan;
    @Column(name = "to_plan", nullable = false, length = 160) private String toPlan;
    @Column(name = "effective_date", nullable = false) private LocalDate effectiveDate;
    @Column(name = "remaining_days", nullable = false) private int remainingDays;
    @Column(name = "days_in_period", nullable = false) private int daysInPeriod;
    @Column(name = "already_invoiced", nullable = false, precision = 18, scale = 2) private BigDecimal alreadyInvoiced;
    @Column(name = "already_paid", nullable = false, precision = 18, scale = 2) private BigDecimal alreadyPaid;
    @Column(name = "old_recurring", nullable = false, precision = 18, scale = 2) private BigDecimal oldRecurring;
    @Column(name = "new_recurring", nullable = false, precision = 18, scale = 2) private BigDecimal newRecurring;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal subtotal;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal tax;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal total;
    @Column(name = "tax_rule", length = 60) private String taxRule;
    @Column(length = 500) private String explanation;
    @Column(nullable = false, length = 20) private String status = DUE;
    @Column(length = 120) private String reference;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "paid_at") private Instant paidAt;
    @Version private long version;

    protected UpgradeCharge() { }

    public UpgradeCharge(String customerId, UUID subscriptionId, UUID planChangeId, UUID invoiceId, String period, String currency,
                         String fromPlan, String toPlan, LocalDate effectiveDate, int remainingDays, int daysInPeriod,
                         BigDecimal alreadyInvoiced, BigDecimal alreadyPaid, BigDecimal oldRecurring, BigDecimal newRecurring,
                         BigDecimal subtotal, BigDecimal tax, String taxRule, String explanation) {
        this.customerId = customerId;
        this.subscriptionId = subscriptionId;
        this.planChangeId = planChangeId;
        this.invoiceId = invoiceId;
        this.period = period;
        this.currency = currency;
        this.fromPlan = fromPlan;
        this.toPlan = toPlan;
        this.effectiveDate = effectiveDate;
        this.remainingDays = remainingDays;
        this.daysInPeriod = daysInPeriod;
        this.alreadyInvoiced = alreadyInvoiced;
        this.alreadyPaid = alreadyPaid;
        this.oldRecurring = oldRecurring;
        this.newRecurring = newRecurring;
        this.subtotal = subtotal;
        this.tax = tax;
        this.total = subtotal.add(tax);
        this.taxRule = taxRule;
        this.explanation = explanation;
    }

    public void markPaid(String reference) {
        if (PAID.equals(status)) return;
        if (!DUE.equals(status)) throw new IllegalStateException("Only a due charge can be paid");
        this.status = PAID;
        this.reference = reference;
        this.paidAt = Instant.now();
    }

    public boolean isDue() { return DUE.equals(status); }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getPlanChangeId() { return planChangeId; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getPeriod() { return period; }
    public String getCurrency() { return currency; }
    public String getFromPlan() { return fromPlan; }
    public String getToPlan() { return toPlan; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public int getRemainingDays() { return remainingDays; }
    public int getDaysInPeriod() { return daysInPeriod; }
    public BigDecimal getAlreadyInvoiced() { return alreadyInvoiced; }
    public BigDecimal getAlreadyPaid() { return alreadyPaid; }
    public BigDecimal getOldRecurring() { return oldRecurring; }
    public BigDecimal getNewRecurring() { return newRecurring; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getTotal() { return total; }
    public String getTaxRule() { return taxRule; }
    public String getExplanation() { return explanation; }
    public String getStatus() { return status; }
    public String getReference() { return reference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
}
