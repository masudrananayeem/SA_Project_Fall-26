package com.meterflow.invoicing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted invoice. The unique (customer_id, period) constraint is the final guard against
 * double invoicing, even if a billing run is restarted or two runs overlap (QA3).
 */
@Entity
@Table(name = "invoices", uniqueConstraints = @UniqueConstraint(name = "uk_invoice_customer_period", columnNames = {"customer_id", "period"}))
public class Invoice {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "customer_id", nullable = false) private String customerId;
    @Column(nullable = false) private String period;
    @Column(nullable = false) private String status = "DRAFT";
    @Column(nullable = false) private String currency;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal subtotal;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal tax;
    @org.hibernate.annotations.ColumnDefault("0") @Column(name = "credit_applied", nullable = false, precision = 18, scale = 2)
    private BigDecimal creditApplied = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal total;
    @Column(name = "tax_rule", length = 255) private String taxRule;
    /** Plan version that priced this invoice; used for late-usage adjustments of this period. */
    @Column(name = "plan_id") private UUID planId;
    @Column(name = "billed_quantity", precision = 24, scale = 8) private BigDecimal billedQuantity;
    @Column(name = "seat_count") private Integer seatCount;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "finalised_at") private Instant finalisedAt;
    @Column(name = "paid_at") private Instant paidAt;
    @org.hibernate.annotations.ColumnDefault("0") @Version private long version;

    protected Invoice() { }

    public Invoice(String customerId, String period, String currency, BigDecimal subtotal, BigDecimal tax) {
        this.customerId = customerId;
        this.period = period;
        this.currency = currency;
        this.subtotal = subtotal;
        this.tax = tax;
        this.total = subtotal.add(tax);
    }

    public static Invoice from(InvoiceDocument doc) {
        var invoice = new Invoice(doc.customerId(), doc.period(), doc.currency(), doc.subtotal(), doc.tax());
        invoice.creditApplied = doc.creditApplied();
        invoice.total = doc.total();
        invoice.taxRule = doc.taxRule();
        invoice.planId = doc.planId();
        invoice.billedQuantity = doc.billedQuantity();
        invoice.seatCount = doc.seatCount();
        return invoice;
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getPeriod() { return period; }
    public String getStatus() { return status; }
    public String getCurrency() { return currency; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getCreditApplied() { return creditApplied; }
    public BigDecimal getTotal() { return total; }
    public String getTaxRule() { return taxRule; }
    public UUID getPlanId() { return planId; }
    public BigDecimal getBilledQuantity() { return billedQuantity; }
    public Integer getSeatCount() { return seatCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinalisedAt() { return finalisedAt; }
    public Instant getPaidAt() { return paidAt; }

    public void finalise() {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only draft invoices can be finalised");
        status = "FINALISED";
        finalisedAt = Instant.now();
    }

    public void markPaid() {
        if ("PAID".equals(status)) return;
        if (!"FINALISED".equals(status)) throw new IllegalStateException("Only finalised invoices can be paid");
        status = "PAID";
        paidAt = Instant.now();
    }

    public boolean amountDue() { return total.signum() > 0 && "FINALISED".equals(status); }
}
