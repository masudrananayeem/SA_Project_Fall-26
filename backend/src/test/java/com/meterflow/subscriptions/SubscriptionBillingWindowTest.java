package com.meterflow.subscriptions;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A subscription that leaves ACTIVE during a month is still invoiced once for that month. */
class SubscriptionBillingWindowTest {
    private final YearMonth thisMonth = YearMonth.now(ZoneOffset.UTC);

    private Subscription fresh() {
        return new Subscription("acme", UUID.randomUUID(), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
    }

    @Test
    void activeIsAlwaysBillable() {
        var s = fresh();
        assertTrue(s.billableIn(thisMonth));
        assertNull(s.getStatusChangedAt());
    }

    @Test
    void cancelledThisMonthIsStillBilledForThisMonthButNotTheNext() {
        var s = fresh();
        s.cancel();
        assertEquals(LocalDate.now(ZoneOffset.UTC), s.getStatusChangedAt());
        assertTrue(s.billableIn(thisMonth), "usage before the cancellation must be invoiced");
        assertFalse(s.billableIn(thisMonth.plusMonths(1)));
    }

    @Test
    void pausedAndBlockedFollowTheSameRule() {
        var paused = fresh();
        paused.pause();
        assertTrue(paused.billableIn(thisMonth));
        assertFalse(paused.billableIn(thisMonth.plusMonths(1)));
        var blocked = fresh();
        blocked.block();
        assertTrue(blocked.billableIn(thisMonth));
        assertFalse(blocked.billableIn(thisMonth.plusMonths(1)));
    }

    @Test
    void expiredTrialIsNeverBilledAndPausedRefusesUsage() {
        var s = fresh();
        s.startTrial(LocalDate.now(ZoneOffset.UTC).plusDays(14), false);
        assertTrue(s.expireTrial(LocalDate.now(ZoneOffset.UTC).plusDays(14)));
        assertEquals("EXPIRED", s.getStatus());
        assertFalse(s.billableIn(thisMonth));
        assertFalse(SubscriptionState.PAUSED.acceptsUsage());
        assertTrue(SubscriptionState.ACTIVE.acceptsUsage());
    }
}
