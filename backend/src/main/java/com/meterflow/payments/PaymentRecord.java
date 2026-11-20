package com.meterflow.payments;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment ledger: one row per payment attempt (invoice payment, card charge, retry) or prepaid
 * top-up, successful or not. The admin console shows it as a live feed.
 */
@Entity
@Table(name = "payment_records", indexes = {
        @Index(name = "idx_payment_records_customer", columnList = "customer_id, created_at"),
        @Index(name = "idx_payment_records_created", columnList = "created_at")})
public class PaymentRecord {
    public static final String INVOICE = "INVOICE";
    public static final String TOP_UP = "TOP_UP";
    /** Balance paid after a mid-month plan upgrade (invoiceId holds the month's invoice). */
    public static final String UPGRADE = "UPGRADE";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "invoice_id") private UUID invoiceId;
    @Column(name = "customer_id", nullable = false) private String customerId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, length = 30) private String method;
    @Column(nullable = false, length = 20) private String kind;
    @Column(nullable = false, length = 20) private String status;
    @Column(length = 120) private String reference;
    @Column(length = 300) private String message;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected PaymentRecord() { }

    public PaymentRecord(UUID invoiceId, String customerId, BigDecimal amount, String currency, String method, String kind,
                         String status, String reference, String message) {
        this.invoiceId = invoiceId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.kind = kind;
        this.status = status;
        this.reference = reference == null || reference.length() <= 120 ? reference : reference.substring(0, 120);
        this.message = message == null || message.length() <= 300 ? message : message.substring(0, 300);
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getMethod() { return method; }
    public String getKind() { return kind; }
    public String getStatus() { return status; }
    public String getReference() { return reference; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
