package com.meterflow.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.Plan;
import com.meterflow.catalogue.charges.ChargeDefinition;
import com.meterflow.invoicing.BillingTestWorld;
import com.meterflow.invoicing.Invoice;
import com.meterflow.invoicing.InvoiceLine;
import com.meterflow.metering.UsageController;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QA1 accuracy harness. Generates N submitted events (default 100,000; run with
 * -Dqa1.events=1000000 for the full acceptance size) of which 3% are re-submitted duplicates and
 * 2% arrive late (half before the September billing run, half after it). Events go through the
 * real ingestion rules and the real billing run (only the database is in memory), and the result
 * is compared with an independent oracle that prices each customer's de-duplicated usage with its
 * own implementation of the four plan shapes used:
 *
 * <ul>
 *   <li>September charges (September invoice + late-usage line on the October invoice) equal the
 *       oracle price of all unique September events, to the paisa;</li>
 *   <li>every unique event is billed exactly once (source event ids are disjoint and complete).</li>
 * </ul>
 */
class Qa1AccuracyTest {
    private static final YearMonth SEP = YearMonth.of(2026, 9);
    private static final Instant SEP_START = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant SEP_CLOSE = Instant.parse("2026-10-01T00:00:00Z");

    record Generated(String key, String customer, BigDecimal quantity, Instant at) { }

    // ---------------- independent oracle (does not use PricingService or the charge strategies) ----------------
    static BigDecimal r2(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    static BigDecimal oracle(int shape, BigDecimal q) {
        switch (shape) {
            case 0: { // legacy: 299 + 0.05 per unit over 1000, minimum 350
                BigDecimal sum = r2(new BigDecimal("299")).add(r2(q.subtract(new BigDecimal("1000")).max(BigDecimal.ZERO).multiply(new BigDecimal("0.05"))));
                return sum.max(new BigDecimal("350.00"));
            }
            case 1: { // graduated: first 1000 free, next 9000 at 0.02, rest at 0.01
                BigDecimal band1 = q.min(new BigDecimal("10000")).subtract(new BigDecimal("1000")).max(BigDecimal.ZERO);
                BigDecimal band2 = q.subtract(new BigDecimal("10000")).max(BigDecimal.ZERO);
                return r2(band1.multiply(new BigDecimal("0.02")).add(band2.multiply(new BigDecimal("0.01"))));
            }
            case 2: { // volume: <=2000 at 0.03, <=20000 at 0.025, else 0.02; plus 3 seats x 12.5
                BigDecimal rate = q.compareTo(new BigDecimal("2000")) <= 0 ? new BigDecimal("0.03")
                        : q.compareTo(new BigDecimal("20000")) <= 0 ? new BigDecimal("0.025") : new BigDecimal("0.02");
                return r2(q.multiply(rate)).add(r2(new BigDecimal("37.5")));
            }
            default: { // flat 50 + 4.75 per started 1000 units after 500 free
                BigDecimal billable = q.subtract(new BigDecimal("500")).max(BigDecimal.ZERO);
                BigDecimal packs = billable.divide(new BigDecimal("1000"), 0, RoundingMode.CEILING);
                return r2(new BigDecimal("50")).add(r2(packs.multiply(new BigDecimal("4.75"))));
            }
        }
    }

    // ---------------- system under test configuration ----------------
    private static Plan plan(int shape) throws Exception {
        var m = new ObjectMapper();
        return switch (shape) {
            case 0 -> new Plan("legacy", 1, "Legacy", "BDT", new BigDecimal("299"), new BigDecimal("0.05"), "api_calls",
                    new BigDecimal("1000"), new BigDecimal("350"));
            case 1 -> new Plan("graduated", 1, "Graduated", "BDT", BigDecimal.ZERO, BigDecimal.ZERO, "api_calls", BigDecimal.ZERO, BigDecimal.ZERO,
                    m.writeValueAsString(List.of(new ChargeDefinition("graduated_tier", "API calls", BigDecimal.ZERO, null, null, List.of(
                            new ChargeDefinition.Tier(new BigDecimal("1000"), BigDecimal.ZERO),
                            new ChargeDefinition.Tier(new BigDecimal("10000"), new BigDecimal("0.02")),
                            new ChargeDefinition.Tier(null, new BigDecimal("0.01")))))));
            case 2 -> new Plan("volume", 1, "Volume", "USD", BigDecimal.ZERO, BigDecimal.ZERO, "api_calls", BigDecimal.ZERO, BigDecimal.ZERO,
                    m.writeValueAsString(List.of(
                            new ChargeDefinition("volume_tier", "API calls", BigDecimal.ZERO, null, null, List.of(
                                    new ChargeDefinition.Tier(new BigDecimal("2000"), new BigDecimal("0.03")),
                                    new ChargeDefinition.Tier(new BigDecimal("20000"), new BigDecimal("0.025")),
                                    new ChargeDefinition.Tier(null, new BigDecimal("0.02")))),
                            new ChargeDefinition("per_seat", "Seats", new BigDecimal("12.5"), null, null, null))));
            default -> new Plan("package", 1, "Package", "BDT", BigDecimal.ZERO, BigDecimal.ZERO, "api_calls", BigDecimal.ZERO, BigDecimal.ZERO,
                    m.writeValueAsString(List.of(
                            new ChargeDefinition("flat_fee", "Platform", new BigDecimal("50"), null, null, null),
                            new ChargeDefinition("package", "Per 1,000 calls", new BigDecimal("4.75"), new BigDecimal("500"), new BigDecimal("1000"), null))));
        };
    }

    @Test
    void invoicesMatchIndependentRecomputationAndNoEventIsBilledTwice() throws Exception {
        int submitted = Integer.getInteger("qa1.events", 100_000);
        int duplicates = submitted * 3 / 100;
        int unique = submitted - duplicates;
        int late = submitted * 2 / 100;
        int customers = Math.max(40, submitted / 1000);
        Random random = new Random(20261116L);

        var world = new BillingTestWorld(false);
        Map<String, Integer> shapeOf = new HashMap<>();
        Plan[] plans = new Plan[4];
        for (int s = 0; s < 4; s++) plans[s] = world.addPlan(plan(s));
        for (int c = 0; c < customers; c++) {
            String customer = "cust-" + c;
            int shape = c % 4;
            shapeOf.put(customer, shape);
            var sub = world.subscribe(customer, plans[shape], LocalDate.of(2026, 1, 1));
            if (shape == 2) sub.setSeatCount(3);
        }

        // Unique events; the last `late` of them are the late arrivals.
        List<Generated> events = new ArrayList<>(unique);
        long monthSeconds = Duration.between(SEP_START, SEP_CLOSE).getSeconds();
        for (int i = 0; i < unique; i++) {
            String customer = "cust-" + random.nextInt(customers);
            BigDecimal quantity = random.nextInt(10) == 0
                    ? BigDecimal.valueOf(random.nextInt(4000) + 1, 2)          // fractional, e.g. 12.37
                    : BigDecimal.valueOf(random.nextInt(60) + 1);
            Instant at = SEP_START.plusSeconds((long) (random.nextDouble() * (monthSeconds - 1)));
            events.add(new Generated("k-" + i, customer, quantity, at));
        }
        List<Generated> onTime = new ArrayList<>(events.subList(0, unique - late));
        List<Generated> lateA = new ArrayList<>(events.subList(unique - late, unique - late / 2));
        List<Generated> lateB = new ArrayList<>(events.subList(unique - late / 2, unique));

        // Duplicates are re-submissions of earlier events (same key and payload), spread over all phases.
        List<Generated> onTimeSubmissions = new ArrayList<>(onTime);
        List<Generated> lateASubmissions = new ArrayList<>(lateA);
        List<Generated> lateBSubmissions = new ArrayList<>(lateB);
        for (int d = 0; d < duplicates; d++) {
            int phase = random.nextInt(100);
            if (phase < 90) onTimeSubmissions.add(onTime.get(random.nextInt(onTime.size())));
            else if (phase < 95) lateASubmissions.add(lateA.get(random.nextInt(lateA.size())));
            else lateBSubmissions.add(lateB.get(random.nextInt(lateB.size())));
        }
        Collections.shuffle(onTimeSubmissions, random);
        Collections.shuffle(lateASubmissions, random);
        Collections.shuffle(lateBSubmissions, random);
        assertEquals(submitted, onTimeSubmissions.size() + lateASubmissions.size() + lateBSubmissions.size());

        long t0 = System.nanoTime();
        int accepted = 0, dupes = 0;
        for (var batch : batches(onTimeSubmissions)) { var r = world.usage.ingest(inputs(batch), SEP_CLOSE.minusSeconds(60)); accepted += (int) r.get("accepted"); dupes += (int) r.get("duplicates"); }
        for (var batch : batches(lateASubmissions)) { var r = world.usage.ingest(inputs(batch), SEP_CLOSE.plusSeconds(600)); accepted += (int) r.get("accepted"); dupes += (int) r.get("duplicates"); }

        // September billing run (after the first late events, before the rest).
        for (String customer : shapeOf.keySet()) world.billing.run(customer, SEP);

        for (var batch : batches(lateBSubmissions)) { var r = world.usage.ingest(inputs(batch), SEP_CLOSE.plus(Duration.ofHours(48))); accepted += (int) r.get("accepted"); dupes += (int) r.get("duplicates"); }
        // October billing run: carries the September late-usage adjustments.
        for (String customer : shapeOf.keySet()) world.billing.run(customer, SEP.plusMonths(1));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertEquals(unique, accepted, "accepted = unique events");
        assertEquals(duplicates, dupes, "every duplicate ignored");

        // ---------- oracle comparison ----------
        Map<String, BigDecimal> qtyAll = new HashMap<>();
        Map<String, BigDecimal> qtyAtBilling = new HashMap<>();
        for (var e : onTime) { qtyAll.merge(e.customer(), e.quantity(), BigDecimal::add); qtyAtBilling.merge(e.customer(), e.quantity(), BigDecimal::add); }
        for (var e : lateA) { qtyAll.merge(e.customer(), e.quantity(), BigDecimal::add); qtyAtBilling.merge(e.customer(), e.quantity(), BigDecimal::add); }
        for (var e : lateB) qtyAll.merge(e.customer(), e.quantity(), BigDecimal::add);

        Set<UUID> billedIds = new HashSet<>();
        long billedIdCount = 0;
        BigDecimal grand = BigDecimal.ZERO;
        int mismatches = 0;
        for (var entry : shapeOf.entrySet()) {
            String customer = entry.getKey();
            int shape = entry.getValue();
            Invoice sep = world.invoices.get(customer + "|2026-09");
            Invoice oct = world.invoices.get(customer + "|2026-10");
            assertNotNull(sep, "September invoice for " + customer);
            assertNotNull(oct, "October invoice for " + customer);
            BigDecimal expectedSep = oracle(shape, qtyAtBilling.getOrDefault(customer, BigDecimal.ZERO));
            BigDecimal expectedAll = oracle(shape, qtyAll.getOrDefault(customer, BigDecimal.ZERO));
            BigDecimal lateCharges = BigDecimal.ZERO;
            for (InvoiceLine line : world.linesOf(oct)) {
                if (!InvoiceLine.LATE_USAGE.equals(line.getKind())) continue;
                assertEquals("2026-09", line.getReference());
                lateCharges = lateCharges.add(line.getAmount());
                for (UUID id : InvoiceLine.idsFromJson(line.getSourceEventIdsJson())) { billedIds.add(id); billedIdCount++; }
            }
            for (InvoiceLine line : world.linesOf(sep))
                for (UUID id : InvoiceLine.idsFromJson(line.getSourceEventIdsJson())) { billedIds.add(id); billedIdCount++; }
            if (sep.getSubtotal().compareTo(expectedSep) != 0 || sep.getSubtotal().add(lateCharges).compareTo(expectedAll) != 0) mismatches++;
            grand = grand.add(expectedAll);
        }
        assertEquals(0, mismatches, "customers whose September charges differ from the oracle");
        assertEquals(unique, billedIds.size(), "every unique event billed");
        assertEquals((long) unique, billedIdCount, "no event billed twice");
        assertTrue(world.ledger.values().stream().flatMap(List::stream).noneMatch(r -> r.isPending()), "no late event left pending");

        System.out.printf("QA1 submitted=%d unique=%d duplicates=%d late=%d customers=%d invoices=%d septemberTotalCharges=%s elapsedMs=%d%n",
                submitted, unique, duplicates, late, customers, world.invoices.size(), grand.toPlainString(), elapsedMs);
    }

    private static List<List<Generated>> batches(List<Generated> all) {
        List<List<Generated>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += 1000) out.add(all.subList(i, Math.min(all.size(), i + 1000)));
        return out;
    }

    private static List<UsageController.EventInput> inputs(List<Generated> batch) {
        return batch.stream().map(g -> new UsageController.EventInput(g.customer(), "api_calls", g.quantity(), g.at(), g.key(), Map.of())).toList();
    }
}
