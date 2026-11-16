package com.meterflow.invoicing;

import com.meterflow.shared.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builder pattern: assembles an {@link InvoiceDocument}, computing subtotal and tax (via TaxVisitor). */
public class InvoiceBuilder {
    private String customer;
    private String period;
    private String currency;
    private UUID planId;
    private String planLabel = "";
    private BigDecimal billedQuantity = BigDecimal.ZERO;
    private int seatCount = 1;
    private TaxVisitor tax = TaxVisitor.vat(BigDecimal.ZERO);
    private final List<BillingLine> lines = new ArrayList<>();

    public InvoiceBuilder customer(String value) { customer = value; return this; }
    public InvoiceBuilder period(String value) { period = value; return this; }
    public InvoiceBuilder currency(String value) { currency = value; return this; }
    public InvoiceBuilder plan(UUID id, String label) { planId = id; planLabel = label; return this; }
    public InvoiceBuilder billedQuantity(BigDecimal value) { billedQuantity = value; return this; }
    public InvoiceBuilder seats(int value) { seatCount = value; return this; }
    public InvoiceBuilder tax(TaxVisitor value) { tax = value; return this; }
    public InvoiceBuilder line(BillingLine line) { lines.add(line); return this; }
    public InvoiceBuilder lines(List<? extends BillingLine> more) { lines.addAll(more); return this; }

    public List<BillingLine> currentLines() { return List.copyOf(lines); }

    public InvoiceDocument build() {
        if (customer == null || period == null || currency == null) throw new IllegalStateException("customer, period and currency are required");
        Money subtotal = Money.zero(currency);
        for (BillingLine line : lines) subtotal = subtotal.add(Money.of(line.amount(), currency));
        Money taxAmount = Money.of(tax.tax(lines), currency);
        Money gross = subtotal.add(taxAmount);
        return new InvoiceDocument(customer, period, currency, planId, planLabel, billedQuantity, seatCount, lines,
                subtotal.amount(), taxAmount.amount(), tax.describe(), Money.zero(currency).amount(), gross.amount());
    }
}
