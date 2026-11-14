package com.meterflow.invoicing;

import jakarta.persistence.*;
import java.util.*;
import java.math.*;

/**
 * An explainable invoice line. eventIdsJson/eventCount preserve the source usage
 * records used for the charge so an auditor can trace a billed quantity.
 */
@Entity
@Table(name = "invoice_lines", indexes = @Index(name = "idx_invoice_line_invoice", columnList = "invoice_id"))
public class InvoiceLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID invoiceId;
    @Column(nullable = false) private String metric;
    @Column(nullable = false, precision = 24, scale = 8) private BigDecimal quantity;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 500) private String priceRule;
    @Column(nullable = false, length = 500) private String taxRule = "VAT 0% (demo default)";
    @Column(name = "source_event_ids_json", nullable = false, columnDefinition = "text") private String sourceEventIdsJson = "[]";
    @Column(name = "source_event_count", nullable = false) private int sourceEventCount = 0;

    protected InvoiceLine() {}

    public InvoiceLine(UUID invoiceId, String metric, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal amount, String priceRule, String taxRule) {
        this(invoiceId, metric, quantity, unitPrice, amount, priceRule, taxRule, "[]", 0);
    }

    public InvoiceLine(UUID invoiceId, String metric, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal amount, String priceRule, String taxRule,
                       String sourceEventIdsJson, int sourceEventCount) {
        this.invoiceId = invoiceId;
        this.metric = metric;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = amount;
        this.priceRule = priceRule;
        this.taxRule = taxRule;
        this.sourceEventIdsJson = sourceEventIdsJson == null ? "[]" : sourceEventIdsJson;
        this.sourceEventCount = Math.max(0, sourceEventCount);
    }

    public UUID getId(){ return id; }
    public UUID getInvoiceId(){ return invoiceId; }
    public String getMetric(){ return metric; }
    public BigDecimal getQuantity(){ return quantity; }
    public BigDecimal getUnitPrice(){ return unitPrice; }
    public BigDecimal getAmount(){ return amount; }
    public String getPriceRule(){ return priceRule; }
    public String getTaxRule(){ return taxRule; }
    public String getSourceEventIdsJson(){ return sourceEventIdsJson; }
    public int getSourceEventCount(){ return sourceEventCount; }
}
