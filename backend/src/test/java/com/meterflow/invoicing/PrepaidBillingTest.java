package com.meterflow.invoicing;

import com.meterflow.catalogue.Plan;
import com.meterflow.subscriptions.SubscriptionState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/** Prepaid plans are invoiced up front (package only, before usage) and never billed twice. */
class PrepaidBillingTest {

    private Plan prepaidPlan(BillingTestWorld w) {
        var plan = new Plan("pro-prepaid", 1, "Pro Prepaid", "BDT", new BigDecimal("1500"), BigDecimal.ZERO, "api_calls",
                new BigDecimal("100000"), BigDecimal.ZERO);
        plan.setTaxRate(new BigDecimal("15"));
        plan.setBillingMode(Plan.PREPAID);
        return w.addPlan(plan);
    }

    @Test
    void prepaidInvoiceIsThePackagePlusTaxAndIsFinalisedImmediately() {
        var w = new BillingTestWorld(true);
        var plan = prepaidPlan(w);
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        var sub = w.subscribe("acme", plan, month.atDay(1));
        sub.awaitPayment();
        assertEquals(SubscriptionState.PENDING_PAYMENT.name(), sub.getStatus());

        var outcome = w.prepaid.run("acme", month);

        assertEquals(BillingOutcome.CREATED, outcome.status());
        var invoice = w.invoicesById.get(outcome.invoiceId());
        assertEquals("FINALISED", invoice.getStatus());
        assertEquals(0, new BigDecimal("1725.00").compareTo(invoice.getTotal()), "1500 + 15% VAT");
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getBilledQuantity()));
        assertTrue(w.published.stream().anyMatch(e -> e instanceof InvoiceFinalised));
    }

    @Test
    void monthEndRunFindsThePrepaidInvoiceAndDoesNotBillAgain() {
        var w = new BillingTestWorld(true);
        var plan = prepaidPlan(w);
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        var sub = w.subscribe("acme", plan, month.atDay(1));
        sub.awaitPayment();
        w.prepaid.run("acme", month);
        sub.resumeFromDunning(); // paid -> ACTIVE

        var again = w.billing.run("acme", month);

        assertEquals(BillingOutcome.EXISTING, again.status());
        assertEquals(BillingOutcome.EXISTING, w.prepaid.run("acme", month).status());
        assertEquals(1, w.invoices.size());
    }

    @Test
    void planBillingModeIsValidated() {
        var plan = new Plan("x", 1, "X", "BDT", BigDecimal.ONE, BigDecimal.ZERO, "api_calls", BigDecimal.ZERO, BigDecimal.ZERO);
        assertFalse(plan.isPrepaid());
        plan.setBillingMode("prepaid");
        assertTrue(plan.isPrepaid());
        assertThrows(IllegalArgumentException.class, () -> plan.setBillingMode("weekly"));
        assertNotNull(LocalDate.now());
    }
}
