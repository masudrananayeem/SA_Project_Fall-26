package com.meterflow.invoicing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Result of billing (or simulating) one customer for one period. */
public record BillingOutcome(String customerId, String period, String status, String reason, UUID invoiceId,
                             InvoiceDocument document) {
    public static final String CREATED = "CREATED";
    public static final String EXISTING = "EXISTING";
    public static final String SKIPPED = "SKIPPED";
    public static final String SIMULATED = "SIMULATED";

    public static BillingOutcome skipped(String customer, String period, String reason) {
        return new BillingOutcome(customer, period, SKIPPED, reason, null, null);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("customerId", customerId);
        map.put("period", period);
        map.put("status", status);
        map.put("created", CREATED.equals(status) ? 1 : 0);
        if (reason != null) map.put("reason", reason);
        if (invoiceId != null) map.put("invoiceId", invoiceId.toString());
        if (document != null) {
            map.put("plan", document.planLabel());
            map.put("currency", document.currency());
            map.put("billedQuantity", document.billedQuantity());
            map.put("lines", document.lines().stream().map(BillingOutcome::lineMap).toList());
            map.put("subtotal", document.subtotal());
            map.put("tax", document.tax());
            map.put("taxRule", document.taxRule());
            map.put("creditApplied", document.creditApplied());
            map.put("total", document.total());
        }
        if (SIMULATED.equals(status)) map.put("simulationOnly", true);
        return map;
    }

    private static Map<String, Object> lineMap(BillingLine line) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", line.accept(new BillingLineVisitor<String>() {
            public String visitUsage(BillingLine.UsageCharge l) { return InvoiceLine.USAGE; }
            public String visitProration(BillingLine.Proration l) { return InvoiceLine.PRORATION; }
            public String visitLateUsage(BillingLine.LateUsage l) { return InvoiceLine.LATE_USAGE; }
        }));
        row.put("description", line.description());
        row.put("amount", line.amount());
        row.put("rule", line.accept(new BillingLineVisitor<String>() {
            public String visitUsage(BillingLine.UsageCharge l) { return l.rule(); }
            public String visitProration(BillingLine.Proration l) { return l.rule(); }
            public String visitLateUsage(BillingLine.LateUsage l) { return l.rule(); }
        }));
        if (line instanceof BillingLine.UsageCharge u) {
            row.put("quantity", u.quantity());
            row.put("sourceEventCount", u.sourceEventCount());
        }
        if (line instanceof BillingLine.LateUsage l) {
            row.put("quantity", l.lateQuantity());
            row.put("sourceEventCount", l.sourceEventIds().size());
        }
        return row;
    }

    public static Map<String, Object> summary(String period, List<BillingOutcome> outcomes) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("period", period);
        map.put("created", outcomes.stream().filter(o -> CREATED.equals(o.status())).count());
        map.put("alreadyExisted", outcomes.stream().filter(o -> EXISTING.equals(o.status())).count());
        map.put("skipped", outcomes.stream().filter(o -> SKIPPED.equals(o.status())).count());
        map.put("status", "completed");
        return map;
    }
}
