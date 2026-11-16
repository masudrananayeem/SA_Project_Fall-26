package com.meterflow.invoicing;

import com.meterflow.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceDocumentTest {
    private BillingLine.UsageCharge usage(String amount) {
        return new BillingLine.UsageCharge("api_calls", "Usage", "per_unit", new BigDecimal("10"), new BigDecimal("1"),
                new BigDecimal(amount), "rule", List.of(), 0);
    }

    @Test
    void builderComputesSubtotalTaxAndCredit() {
        var doc = new InvoiceBuilder().customer("c").period("2026-10").currency("BDT").tax(TaxVisitor.vat(new BigDecimal("15")))
                .line(usage("100.00"))
                .line(new BillingLine.Proration(UUID.randomUUID(), new BigDecimal("-20.00"), "downgrade"))
                .line(new BillingLine.LateUsage("2026-09", "api_calls", BigDecimal.ONE, new BigDecimal("3.33"), "late", List.of()))
                .build();
        assertEquals(new BigDecimal("83.33"), doc.subtotal());
        assertEquals(new BigDecimal("12.50"), doc.tax()); // 83.33 x 15% = 12.4995 -> 12.50
        assertEquals(new BigDecimal("95.83"), doc.total());
        var paid = doc.withCreditApplied(new BigDecimal("50.00"));
        assertEquals(new BigDecimal("45.83"), paid.total());
        assertThrows(IllegalArgumentException.class, () -> doc.withCreditApplied(new BigDecimal("100.00")));
    }

    @Test
    void taxIsNeverNegative() {
        var doc = new InvoiceBuilder().customer("c").period("2026-10").currency("USD").tax(TaxVisitor.vat(new BigDecimal("10")))
                .line(new BillingLine.Proration(null, new BigDecimal("-50.00"), "credit")).build();
        assertEquals(new BigDecimal("0.00"), doc.tax());
        assertEquals(new BigDecimal("-50.00"), doc.total());
    }

    @Test
    void moneyRejectsCurrencyMixing() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(BigDecimal.ONE, "BDT").add(Money.of(BigDecimal.ONE, "USD")));
        assertEquals(new BigDecimal("1.01"), Money.of(new BigDecimal("1.005"), "BDT").amount());
    }

    @Test
    void persistedLinesRoundTripToVisitorElements() {
        var id = UUID.randomUUID();
        var line = new InvoiceLineMapper(UUID.randomUUID(), "VAT 5%").visitLateUsage(
                new BillingLine.LateUsage("2026-09", "api_calls", new BigDecimal("4"), new BigDecimal("1.20"), "late rule", List.of(id)));
        var back = (BillingLine.LateUsage) line.toBillingLine();
        assertEquals("2026-09", back.originalPeriod());
        assertEquals(List.of(id), back.sourceEventIds());
        assertEquals(InvoiceLine.LATE_USAGE, line.getKind());
    }
}
