package com.meterflow.invoicing;

import java.util.stream.Collectors;

/** Rendering visitors: plain text for humans, CSV for spreadsheets. */
public final class InvoiceRenderers {
    private InvoiceRenderers() { }

    public static final class Text implements BillingLineVisitor<String> {
        @Override public String visitUsage(BillingLine.UsageCharge l) {
            return String.format("%-34s %14s %12s  %s", l.chargeName(), l.quantity().stripTrailingZeros().toPlainString(),
                    l.amount().toPlainString(), l.rule());
        }
        @Override public String visitProration(BillingLine.Proration l) {
            return String.format("%-34s %14s %12s  %s", l.description(), "-", l.amount().toPlainString(), l.rule());
        }
        @Override public String visitLateUsage(BillingLine.LateUsage l) {
            return String.format("%-34s %14s %12s  %s", l.description(), l.lateQuantity().stripTrailingZeros().toPlainString(),
                    l.amount().toPlainString(), l.rule());
        }
    }

    public static final class Csv implements BillingLineVisitor<String> {
        private static String q(String s) { return '"' + (s == null ? "" : s.replace("\"", "\"\"")) + '"'; }
        @Override public String visitUsage(BillingLine.UsageCharge l) {
            return String.join(",", q("USAGE"), q(l.chargeName()), q(l.metric()), l.quantity().toPlainString(),
                    l.amount().toPlainString(), q(l.rule()), String.valueOf(l.sourceEventCount()));
        }
        @Override public String visitProration(BillingLine.Proration l) {
            return String.join(",", q("PRORATION"), q(l.description()), q(""), "", l.amount().toPlainString(), q(l.rule()), "0");
        }
        @Override public String visitLateUsage(BillingLine.LateUsage l) {
            return String.join(",", q("LATE_USAGE"), q(l.description()), q(l.metric()), l.lateQuantity().toPlainString(),
                    l.amount().toPlainString(), q(l.rule()), String.valueOf(l.sourceEventIds().size()));
        }
    }

    public static String text(InvoiceDocument doc) {
        var visitor = new Text();
        String body = doc.lines().stream().map(l -> l.accept(visitor)).collect(Collectors.joining("\n"));
        return "MeterFlow invoice " + doc.customerId() + " " + doc.period() + " (" + doc.planLabel() + ", " + doc.currency() + ")\n"
                + String.format("%-34s %14s %12s  %s", "Line", "Quantity", "Amount", "Price rule") + "\n"
                + body + "\n"
                + String.format("%-34s %14s %12s", "Subtotal", "", doc.subtotal().toPlainString()) + "\n"
                + String.format("%-34s %14s %12s  %s", "Tax", "", doc.tax().toPlainString(), doc.taxRule()) + "\n"
                + String.format("%-34s %14s %12s", "Prepaid credit applied", "", doc.creditApplied().negate().toPlainString()) + "\n"
                + String.format("%-34s %14s %12s", "Total due", "", doc.total().toPlainString()) + "\n";
    }

    public static String csv(InvoiceDocument doc) {
        var visitor = new Csv();
        return "kind,line,metric,quantity,amount,price_rule,source_events\n"
                + doc.lines().stream().map(l -> l.accept(visitor)).collect(Collectors.joining("\n")) + "\n"
                + "\"SUBTOTAL\",,,," + doc.subtotal().toPlainString() + ",,\n"
                + "\"TAX\",,,," + doc.tax().toPlainString() + ",\"" + doc.taxRule() + "\",\n"
                + "\"CREDIT\",,,," + doc.creditApplied().negate().toPlainString() + ",,\n"
                + "\"TOTAL\",,,," + doc.total().toPlainString() + ",,\n";
    }
}
