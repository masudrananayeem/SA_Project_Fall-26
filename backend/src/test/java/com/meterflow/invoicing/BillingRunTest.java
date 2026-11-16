package com.meterflow.invoicing;

import com.meterflow.catalogue.Plan;
import com.meterflow.metering.LateUsageAdjustment;
import com.meterflow.metering.UsageController;
import com.meterflow.subscriptions.PlanChange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BillingRunTest {
    private static final YearMonth SEP = YearMonth.of(2026, 9);
    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private static final Instant SEP_CLOSE = Instant.parse("2026-10-01T00:00:00Z");

    private UsageController.EventInput event(String customer, String key, long quantity, Instant at) {
        return new UsageController.EventInput(customer, "api_calls", BigDecimal.valueOf(quantity), at, key, Map.of());
    }

    private Plan starter(BillingTestWorld w) {
        var plan = new Plan("starter", 1, "Starter", "BDT", new BigDecimal("299"), new BigDecimal("0.05"), "api_calls",
                new BigDecimal("1000"), BigDecimal.ZERO);
        plan.setTaxRate(new BigDecimal("15"));
        return w.addPlan(plan);
    }

    @Test
    void createsFinalisedExplainableInvoiceAndNeverTwice() {
        var w = new BillingTestWorld(false);
        var plan = starter(w);
        w.subscribe("acme", plan, LocalDate.of(2026, 1, 1));
        w.usage.ingest(List.of(event("acme", "e1", 2000, Instant.parse("2026-09-10T00:00:00Z")),
                event("acme", "e2", 1000, Instant.parse("2026-09-20T00:00:00Z"))), Instant.parse("2026-09-21T00:00:00Z"));

        var first = w.billing.run("acme", SEP);
        assertEquals(BillingOutcome.CREATED, first.status());
        var invoice = w.invoicesById.get(first.invoiceId());
        assertEquals("FINALISED", invoice.getStatus());
        // 299 + (3000 - 1000) x 0.05 = 399.00; VAT 15% = 59.85
        assertEquals(new BigDecimal("399.00"), invoice.getSubtotal());
        assertEquals(new BigDecimal("59.85"), invoice.getTax());
        assertEquals(new BigDecimal("458.85"), invoice.getTotal());
        assertEquals(new BigDecimal("3000"), invoice.getBilledQuantity());
        var lines = w.linesOf(invoice);
        assertEquals(2, lines.size());
        assertEquals(2, InvoiceLine.idsFromJson(lines.get(0).getSourceEventIdsJson()).size());
        assertTrue(lines.get(1).getPriceRule().contains("3000 used - 1000 free"));
        assertTrue(w.published.stream().anyMatch(e -> e instanceof InvoiceFinalised f && f.invoiceId().equals(invoice.getId())));

        var second = w.billing.run("acme", SEP);
        assertEquals(BillingOutcome.EXISTING, second.status());
        assertEquals(1, w.invoices.size());
    }

    @Test
    void openPeriodIsNotBilledUnlessAllowed() {
        var w = new BillingTestWorld(false);
        w.subscribe("acme", starter(w), LocalDate.of(2026, 1, 1));
        var current = YearMonth.now(java.time.ZoneOffset.UTC);
        assertEquals(BillingOutcome.SKIPPED, w.billing.run("acme", current).status());
    }

    @Test
    void lateEventsAreIncludedOnceEitherInTheOriginalOrOnTheNextInvoice() {
        var w = new BillingTestWorld(false);
        var plan = starter(w);
        w.subscribe("acme", plan, LocalDate.of(2026, 1, 1));
        w.usage.ingest(List.of(event("acme", "on-time", 1500, Instant.parse("2026-09-15T00:00:00Z"))), Instant.parse("2026-09-16T00:00:00Z"));
        // Late event that arrives after close but before billing: counted on the September invoice.
        w.usage.ingest(List.of(event("acme", "late-before-billing", 100, Instant.parse("2026-09-30T23:00:00Z"))), SEP_CLOSE.plusSeconds(600));
        var sep = w.invoicesById.get(w.billing.run("acme", SEP).invoiceId());
        assertEquals(new BigDecimal("1600"), sep.getBilledQuantity());
        assertEquals(LateUsageAdjustment.INCLUDED, w.ledger.get("acme").get(0).getStatus());

        // Late events after the September invoice: billed on the October invoice as a LATE_USAGE line.
        w.usage.ingest(List.of(event("acme", "late-after-billing", 400, Instant.parse("2026-09-30T23:30:00Z")),
                event("acme", "late-after-billing", 400, Instant.parse("2026-09-30T23:30:00Z"))), SEP_CLOSE.plusSeconds(86_400));
        var oct = w.invoicesById.get(w.billing.run("acme", OCT).invoiceId());
        var late = w.linesOf(oct).stream().filter(l -> l.getKind().equals(InvoiceLine.LATE_USAGE)).toList();
        assertEquals(1, late.size());
        assertEquals("2026-09", late.get(0).getReference());
        assertEquals(new BigDecimal("400"), late.get(0).getQuantity());
        // price(2000) - price(1600) = (299 + 50) - (299 + 30) = 20.00
        assertEquals(new BigDecimal("20.00"), late.get(0).getAmount());
        assertTrue(w.ledger.get("acme").stream().allMatch(r -> !r.isPending()));

        // September in total = price of all September usage, billed exactly once.
        BigDecimal septemberCharges = sep.getSubtotal().add(late.get(0).getAmount());
        assertEquals(w.pricing.calculate(plan, new BigDecimal("2000")), septemberCharges);
    }

    @Test
    void lateUsageWaitsWhileTheOriginalPeriodIsNotInvoiced() {
        var w = new BillingTestWorld(false);
        w.subscribe("acme", starter(w), LocalDate.of(2026, 1, 1));
        w.usage.ingest(List.of(event("acme", "late", 100, Instant.parse("2026-09-30T23:00:00Z"))), SEP_CLOSE.plusSeconds(3600));
        // October billed first: the September event stays pending for the September invoice.
        var oct = w.invoicesById.get(w.billing.run("acme", OCT).invoiceId());
        assertTrue(w.linesOf(oct).stream().noneMatch(l -> l.getKind().equals(InvoiceLine.LATE_USAGE)));
        assertTrue(w.ledger.get("acme").get(0).isPending());
        var sep = w.invoicesById.get(w.billing.run("acme", SEP).invoiceId());
        assertEquals(new BigDecimal("100"), sep.getBilledQuantity());
        assertEquals(LateUsageAdjustment.INCLUDED, w.ledger.get("acme").get(0).getStatus());
    }

    @Test
    void prorationIsPostedOnceOnTheInvoiceOfTheChangeMonth() {
        var w = new BillingTestWorld(false);
        var plan = starter(w);
        var sub = w.subscribe("acme", plan, LocalDate.of(2026, 1, 1));
        var change = new PlanChange(sub.getId(), plan.getId(), plan.getId(), new BigDecimal("-120.50"), "BDT", LocalDate.of(2026, 9, 11),
                "IMMEDIATE", "test change");
        BillingTestWorld.setId(change, UUID.randomUUID());
        w.planChanges.add(change);
        var sep = w.invoicesById.get(w.billing.run("acme", SEP).invoiceId());
        var proration = w.linesOf(sep).stream().filter(l -> l.getKind().equals(InvoiceLine.PRORATION)).toList();
        assertEquals(1, proration.size());
        assertEquals(new BigDecimal("-120.50"), proration.get(0).getAmount());
        assertEquals(sep.getId(), change.getInvoiceId());
        var oct = w.invoicesById.get(w.billing.run("acme", OCT).invoiceId());
        assertTrue(w.linesOf(oct).stream().noneMatch(l -> l.getKind().equals(InvoiceLine.PRORATION)));
    }

    @Test
    void simulatorPricesAMonthUnderAnyPlanVersionWithoutSideEffects() {
        var w = new BillingTestWorld(false);
        var v1 = starter(w);
        var v2 = w.addPlan(new Plan("starter", 2, "Starter", "BDT", new BigDecimal("199"), new BigDecimal("0.04"), "api_calls",
                new BigDecimal("500"), BigDecimal.ZERO));
        w.subscribe("acme", v1, LocalDate.of(2026, 1, 1));
        w.usage.ingest(List.of(event("acme", "e1", 3000, Instant.parse("2026-09-10T00:00:00Z"))), Instant.parse("2026-09-11T00:00:00Z"));
        var underV2 = w.simulation.simulate("acme", SEP, v2.getId());
        assertEquals(BillingOutcome.SIMULATED, underV2.status());
        assertEquals(new BigDecimal("299.00"), underV2.document().subtotal()); // 199 + 2500 x 0.04
        assertEquals(new BigDecimal("399.00"), w.simulation.simulate("acme", SEP, null).document().subtotal());
        assertTrue(w.invoices.isEmpty());
    }

    @Test
    void renderingAndTaxUseVisitors() {
        var w = new BillingTestWorld(false);
        w.subscribe("acme", starter(w), LocalDate.of(2026, 1, 1));
        var outcome = w.billing.run("acme", SEP);
        String text = InvoiceRenderers.text(outcome.document());
        String csv = InvoiceRenderers.csv(outcome.document());
        assertTrue(text.contains("Platform fee"));
        assertTrue(text.contains("VAT 15% on taxable charges"));
        assertTrue(csv.startsWith("kind,line,metric"));
        assertTrue(csv.contains("\"TOTAL\",,,,343.85"));
    }
}
