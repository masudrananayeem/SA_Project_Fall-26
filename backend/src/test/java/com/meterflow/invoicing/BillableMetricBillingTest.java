package com.meterflow.invoicing;

import com.meterflow.catalogue.Plan;
import com.meterflow.metering.UsageController;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** FR2 in billing: the plan's aggregation and filters decide the billed quantity. */
class BillableMetricBillingTest {
    private static final YearMonth SEP = YearMonth.of(2026, 9);

    private UsageController.EventInput event(String key, String metric, long q, String day, Map<String, Object> props) {
        return new UsageController.EventInput("acme", metric, BigDecimal.valueOf(q), Instant.parse("2026-09-" + day + "T10:00:00Z"), key, props);
    }

    @Test
    void uniqueCountBillsActiveUsersAndLateNewUsersOnly() {
        var w = new BillingTestWorld(false);
        var plan = new Plan("seats", 1, "Active users", "BDT", BigDecimal.ZERO, new BigDecimal("10"), "active_user",
                BigDecimal.ZERO, BigDecimal.ZERO);
        plan.defineMetric("unique_count", "user_id", null);
        w.addPlan(plan);
        w.subscribe("acme", plan, LocalDate.of(2026, 1, 1));
        w.usage.ingest(List.of(
                event("a1", "active_user", 1, "02", Map.of("user_id", "u1")),
                event("a2", "active_user", 1, "03", Map.of("user_id", "u1")),
                event("a3", "active_user", 1, "04", Map.of("user_id", "u2"))), Instant.parse("2026-09-05T00:00:00Z"));
        var sep = w.invoicesById.get(w.billing.run("acme", SEP).invoiceId());
        assertEquals(new BigDecimal("2"), sep.getBilledQuantity());
        assertEquals(new BigDecimal("20.00"), sep.getSubtotal());

        // Late: u2 again (no change) and u3 (new user) -> one more active user is billed in October.
        w.usage.ingest(List.of(
                event("late1", "active_user", 1, "30", Map.of("user_id", "u2")),
                event("late2", "active_user", 1, "30", Map.of("user_id", "u3"))), Instant.parse("2026-10-02T00:00:00Z"));
        var oct = w.invoicesById.get(w.billing.run("acme", SEP.plusMonths(1)).invoiceId());
        var late = w.linesOf(oct).stream().filter(l -> l.getKind().equals(InvoiceLine.LATE_USAGE)).toList();
        assertEquals(1, late.size());
        assertEquals(0, BigDecimal.ONE.compareTo(late.get(0).getQuantity()));
        assertEquals(new BigDecimal("10.00"), late.get(0).getAmount());
    }

    @Test
    void filtersRestrictBillableEventsAndCountAggregates() {
        var w = new BillingTestWorld(false);
        var plan = new Plan("sms", 1, "SMS BD", "BDT", BigDecimal.ZERO, new BigDecimal("0.50"), "sms",
                BigDecimal.ZERO, BigDecimal.ZERO);
        plan.defineMetric("count", null, "{\"region\":\"bd\"}");
        w.addPlan(plan);
        w.subscribe("acme", plan, LocalDate.of(2026, 1, 1));
        w.usage.ingest(List.of(
                event("s1", "sms", 5, "02", Map.of("region", "bd")),
                event("s2", "sms", 7, "03", Map.of("region", "bd")),
                event("s3", "sms", 9, "04", Map.of("region", "us"))), Instant.parse("2026-09-05T00:00:00Z"));
        var outcome = w.billing.run("acme", SEP);
        var sep = w.invoicesById.get(outcome.invoiceId());
        assertEquals(new BigDecimal("2"), sep.getBilledQuantity()); // count of matching events, not their quantity
        assertEquals(new BigDecimal("1.00"), sep.getSubtotal());
        var usageLine = w.linesOf(sep).get(0);
        assertEquals(2, InvoiceLine.idsFromJson(usageLine.getSourceEventIdsJson()).size());
    }

    @Test
    void latestValueBillsTheLastReading() {
        var w = new BillingTestWorld(false);
        var plan = new Plan("storage", 1, "Storage GB", "USD", BigDecimal.ZERO, new BigDecimal("0.10"), "storage_gb",
                BigDecimal.ZERO, BigDecimal.ZERO);
        plan.defineMetric("latest_value", null, null);
        w.addPlan(plan);
        w.subscribe("acme", plan, LocalDate.of(2026, 1, 1));
        w.usage.ingest(List.of(event("g1", "storage_gb", 500, "02", Map.of()), event("g2", "storage_gb", 800, "20", Map.of()),
                event("g3", "storage_gb", 650, "10", Map.of())), Instant.parse("2026-09-21T00:00:00Z"));
        var sep = w.invoicesById.get(w.billing.run("acme", SEP).invoiceId());
        assertEquals(new BigDecimal("800"), sep.getBilledQuantity());
        assertEquals(new BigDecimal("80.00"), sep.getSubtotal());
    }
}
