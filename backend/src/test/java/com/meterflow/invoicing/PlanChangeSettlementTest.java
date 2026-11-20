package com.meterflow.invoicing;

import com.meterflow.catalogue.Plan;
import com.meterflow.subscriptions.PlanChange;
import com.meterflow.subscriptions.PlanChangeService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Mid-month plan changes: balance due after an upgrade, and the plan that prices a closed month. */
class PlanChangeSettlementTest {
    private static final YearMonth SEP = YearMonth.of(2026, 9);

    @Test
    void upgradeHalfwayThroughTheMonthOwesHalfTheDifference() {
        // September has 30 days; a change on the 16th leaves 15 days (16..30).
        var due = UpgradeChargeService.remainingDifference(new BigDecimal("1000.00"), new BigDecimal("2000.00"), LocalDate.of(2026, 9, 16));
        assertEquals(new BigDecimal("500.00"), due);
    }

    @Test
    void upgradeOnTheFirstOwesTheWholeDifferenceAndDowngradeIsNegative() {
        assertEquals(new BigDecimal("1000.00"),
                UpgradeChargeService.remainingDifference(new BigDecimal("1000.00"), new BigDecimal("2000.00"), LocalDate.of(2026, 9, 1)));
        assertEquals(new BigDecimal("-500.00"),
                UpgradeChargeService.remainingDifference(new BigDecimal("2000.00"), new BigDecimal("1000.00"), LocalDate.of(2026, 9, 16)));
    }

    @Test
    void balanceDueMatchesThePostpaidProrationRule() {
        // Prepaid customer paid the old plan for the whole month. What is still owed must equal what a
        // postpaid month-end invoice would add: (new - old) + proration delta.
        var oldRec = new BigDecimal("1500.00");
        var newRec = new BigDecimal("2750.00");
        for (int day = 1; day <= 30; day++) {
            var date = LocalDate.of(2026, 9, day);
            var postpaid = newRec.subtract(oldRec).add(PlanChangeService.prorationDelta(oldRec, newRec, date));
            var prepaid = UpgradeChargeService.remainingDifference(oldRec, newRec, date);
            assertTrue(postpaid.subtract(prepaid).abs().compareTo(new BigDecimal("0.01")) <= 0, "day " + day);
        }
    }

    @Test
    void closedMonthIsBilledWithThePlanInForceOnItsLastDay() {
        var w = new BillingTestWorld(false);
        var basic = w.addPlan(new Plan("basic", 1, "Basic", "BDT", new BigDecimal("100"), BigDecimal.ZERO, "api_calls",
                BigDecimal.ZERO, BigDecimal.ZERO));
        var pro = w.addPlan(new Plan("pro", 1, "Pro", "BDT", new BigDecimal("900"), BigDecimal.ZERO, "api_calls",
                BigDecimal.ZERO, BigDecimal.ZERO));
        var sub = w.subscribe("acme", basic, LocalDate.of(2026, 1, 1));
        // A period-end change applied on 1 October, before September is billed.
        var change = new PlanChange(sub.getId(), basic.getId(), pro.getId(), BigDecimal.ZERO.setScale(2), "BDT",
                LocalDate.of(2026, 10, 1), "SCHEDULED", "period-end upgrade");
        BillingTestWorld.setId(change, UUID.randomUUID());
        w.planChanges.add(change);
        sub.changePlan(pro.getId());

        var sep = w.invoicesById.get(w.billing.run("acme", SEP).invoiceId());
        assertEquals(basic.getId(), sep.getPlanId(), "September must be priced by the plan of September");
        assertEquals(0, new BigDecimal("100.00").compareTo(sep.getSubtotal()));

        var oct = w.invoicesById.get(w.billing.run("acme", SEP.plusMonths(1)).invoiceId());
        assertEquals(pro.getId(), oct.getPlanId());
        assertEquals(0, new BigDecimal("900.00").compareTo(oct.getSubtotal()));
    }
}
