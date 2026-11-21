package com.meterflow.invoicing;

import com.meterflow.catalogue.Plan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

/** A subscription cancelled or paused after a month still gets that month's invoice, exactly once. */
class LeftActiveBillingTest {
    private static final YearMonth SEP = YearMonth.of(2026, 9);

    private Plan plan(BillingTestWorld w) {
        return w.addPlan(new Plan("basic", 1, "Basic", "BDT", new BigDecimal("250"), BigDecimal.ZERO, "api_calls",
                BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void cancelledCustomerIsInvoicedForTheMonthItUsedAndOnlyOnce() {
        var w = new BillingTestWorld(false);
        var sub = w.subscribe("acme", plan(w), LocalDate.of(2026, 1, 1));
        sub.cancel();                                   // cancelled today, after September
        var first = w.billing.run("acme", SEP);
        assertEquals(BillingOutcome.CREATED, first.status());
        assertEquals(0, new BigDecimal("250.00").compareTo(w.invoicesById.get(first.invoiceId()).getSubtotal()));
        assertEquals(BillingOutcome.EXISTING, w.billing.run("acme", SEP).status());
    }

    @Test
    void pausedCustomerIsInvoicedForTheMonthBeforeThePause() {
        var w = new BillingTestWorld(false);
        var sub = w.subscribe("acme", plan(w), LocalDate.of(2026, 1, 1));
        sub.pause();
        assertEquals(BillingOutcome.CREATED, w.billing.run("acme", SEP).status());
    }
}
