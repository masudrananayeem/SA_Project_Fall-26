package com.meterflow.subscriptions;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionLifecycleTest {
    private static LocalDate today() { return LocalDate.now(ZoneOffset.UTC); }

    private Subscription sub() { return new Subscription("acme", UUID.randomUUID(), today(), today().plusDays(30)); }

    @Test
    void supportsSeatsAndTrial() {
        var s = sub();
        s.setSeatCount(5);
        s.startTrial(today().plusDays(7));
        assertEquals(5, s.getSeatCount());
        assertTrue(s.isTrialing());
    }

    @Test
    void scheduledPlanChangeActivatesOnEffectiveDate() {
        var next = UUID.randomUUID();
        var s = sub();
        s.schedulePlanChange(next, today().plusDays(3));
        assertFalse(s.applyScheduledChange(today().plusDays(2)));
        assertTrue(s.applyScheduledChange(today().plusDays(3)));
        assertEquals(next, s.getPlanId());
        assertNull(s.getScheduledPlanId());
    }

    @Test
    void trialConvertsExactlyOnItsEndDateAndKeepsTheDateForProration() {
        var s = sub();
        var end = today().plusDays(7);
        s.startTrial(end);
        assertFalse(s.expireTrial(end.minusDays(1)));
        assertTrue(s.expireTrial(end));
        assertEquals(Subscription.TRIAL_CONVERTED, s.getTrialStatus());
        assertEquals(end, s.getTrialEnd());
        assertEquals("ACTIVE", s.getStatus());
        assertFalse(s.expireTrial(end.plusDays(1)));
    }

    @Test
    void trialWithoutAutoConversionExpiresTheSubscription() {
        var s = sub();
        var end = today().plusDays(7);
        s.startTrial(end, false);
        assertTrue(s.expireTrial(end));
        assertEquals("EXPIRED", s.getStatus());
        assertFalse(s.state().billable());
    }

    @Test
    void dunningPauseDoesNotOverrideManualPause() {
        var s = sub();
        s.pause();
        s.pauseForDunning();
        assertEquals("PAUSED", s.getStatus());
        s.resumeFromDunning();
        assertEquals("PAUSED", s.getStatus());
    }

    @Test
    void dunningSuspensionIsLiftedOnlyByPayment() {
        var s = sub();
        s.pauseForDunning();
        assertEquals("PAUSED_DUNNING", s.getStatus());
        assertThrows(IllegalStateException.class, s::resume);
        s.resumeFromDunning();
        assertEquals("ACTIVE", s.getStatus());
    }

    @Test
    void cancelledSubscriptionIsTerminal() {
        var s = sub();
        s.cancel();
        assertThrows(IllegalStateException.class, s::resume);
        assertThrows(IllegalStateException.class, s::pause);
        assertThrows(IllegalStateException.class, () -> s.changePlan(UUID.randomUUID()));
        s.pauseForDunning(); // ignored, not an error
        assertEquals("CANCELLED", s.getStatus());
    }

    @Test
    void rejectsInvalidSeatCount() {
        assertThrows(IllegalArgumentException.class, () -> sub().setSeatCount(0));
    }

    @Test
    void prorationChargesEachPlanOnlyForTheDaysItWasActive() {
        // November 2026 has 30 days. A (300/month) -> B (600) on day 11 -> C (900) on day 21.
        var a = new BigDecimal("300.00");
        var b = new BigDecimal("600.00");
        var c = new BigDecimal("900.00");
        var d1 = PlanChangeService.prorationDelta(a, b, LocalDate.of(2026, 11, 11)); // 10 days elapsed
        var d2 = PlanChangeService.prorationDelta(b, c, LocalDate.of(2026, 11, 21)); // 20 days elapsed
        assertEquals(new BigDecimal("-100.00"), d1);
        assertEquals(new BigDecimal("-200.00"), d2);
        // Month-end invoice charges C for the whole month plus the deltas:
        // 900 - 100 - 200 = 600 = 300 x 10/30 + 600 x 10/30 + 900 x 10/30
        assertEquals(new BigDecimal("600.00"), c.add(d1).add(d2));
        assertEquals(0, BigDecimal.ZERO.compareTo(PlanChangeService.prorationDelta(a, b, LocalDate.of(2026, 12, 1))));
    }
}
