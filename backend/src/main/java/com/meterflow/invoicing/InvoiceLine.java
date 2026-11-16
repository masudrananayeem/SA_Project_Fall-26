package com.meterflow.invoicing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An explainable invoice line: quantity, the price rule that produced the amount, the tax rule and
 * the usage events behind it (sourceEventIdsJson / sourceEventCount) so a line can be traced.
 */
@Entity
@Table(name = "invoice_lines", indexes = @Index(name = "idx_invoice_line_invoice", columnList = "invoice_id"))
public class InvoiceLine {
    public static final String USAGE = "USAGE";
    public static final String PRORATION = "PRORATION";
    public static final String LATE_USAGE = "LATE_USAGE";

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @org.hibernate.annotations.ColumnDefault("'USAGE'") @Column(nullable = false, length = 40) private String kind = USAGE;
    @Column(name = "line_name", length = 255) private String lineName;
    /** USAGE: charge type; PRORATION: plan change id; LATE_USAGE: the original period. */
    @Column(name = "ref_value", length = 255) private String reference;
    @Column(nullable = false) private String metric;
    @Column(nullable = false, precision = 24, scale = 8) private BigDecimal quantity;
    @Column(nullable = false, precision = 18, scale = 8) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 500) private String priceRule;
    @Column(nullable = false, length = 500) private String taxRule = "VAT 0%";
    @Column(name = "source_event_ids_json", nullable = false, columnDefinition = "text") private String sourceEventIdsJson = "[]";
    @Column(name = "source_event_count", nullable = false) private int sourceEventCount = 0;

    protected InvoiceLine() { }

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
        this.priceRule = truncate(priceRule, 500);
        this.taxRule = truncate(taxRule, 500);
        this.sourceEventIdsJson = sourceEventIdsJson == null ? "[]" : sourceEventIdsJson;
        this.sourceEventCount = Math.max(0, sourceEventCount);
    }

    InvoiceLine kind(String kind, String lineName, String reference) {
        this.kind = kind;
        this.lineName = truncate(lineName, 255);
        this.reference = truncate(reference, 255);
        return this;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public static String idsToJson(List<UUID> ids) {
        return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    public static List<UUID> idsFromJson(String json) {
        List<UUID> ids = new ArrayList<>();
        if (json == null) return ids;
        String body = json.trim();
        if (body.startsWith("[")) body = body.substring(1);
        if (body.endsWith("]")) body = body.substring(0, body.length() - 1);
        for (String part : body.split(",")) {
            String token = part.trim().replace("\"", "");
            if (!token.isEmpty()) ids.add(UUID.fromString(token));
        }
        return ids;
    }

    /** Rebuilds the visitor element for rendering. */
    public BillingLine toBillingLine() {
        return switch (kind) {
            case PRORATION -> new BillingLine.Proration(reference == null ? null : UUID.fromString(reference), amount, priceRule);
            case LATE_USAGE -> new BillingLine.LateUsage(reference, metric, quantity, amount, priceRule, idsFromJson(sourceEventIdsJson));
            default -> new BillingLine.UsageCharge(metric, lineName == null ? metric : lineName, reference == null ? "per_unit" : reference,
                    quantity, unitPrice, amount, priceRule, idsFromJson(sourceEventIdsJson), sourceEventCount);
        };
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getKind() { return kind; }
    public String getLineName() { return lineName; }
    public String getReference() { return reference; }
    public String getMetric() { return metric; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getAmount() { return amount; }
    public String getPriceRule() { return priceRule; }
    public String getTaxRule() { return taxRule; }
    public String getSourceEventIdsJson() { return sourceEventIdsJson; }
    public int getSourceEventCount() { return sourceEventCount; }
}
