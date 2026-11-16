package com.meterflow.payments;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DunningCaseTest {
    private final Instant failedAt = Instant.parse("2026-11-01T02:00:00Z");

    private Instant day(int d) { return failedAt.plus(Duration.ofDays(d)); }

    @Test
    void retriesOnDaysOneThreeAndSevenThenPastDueThenSuspended() {
        var c = new DunningCase(UUID.randomUUID(), "customer-1", failedAt);
        assertEquals("RETRYING", c.getStatus());
        assertEquals(day(1), c.getNextAttemptAt());
        assertFalse(c.isDue(day(1).minusSeconds(1)));
        assertTrue(c.isDue(day(1)));

        assertEquals(DunningState.RETRYING, c.attemptFailed(day(1)));
        assertEquals(day(3), c.getNextAttemptAt());
        assertEquals(DunningState.RETRYING, c.attemptFailed(day(3)));
        assertEquals(day(7), c.getNextAttemptAt());

        assertEquals(DunningState.PAST_DUE, c.attemptFailed(day(7)));
        assertEquals(3, c.getAttempt());
        assertEquals(day(7), c.getPastDueAt());
        assertEquals(day(14), c.getNextAttemptAt());

        assertEquals(DunningState.SUSPENDED, c.attemptFailed(day(14)));
        assertNull(c.getNextAttemptAt());
        assertFalse(c.isDue(day(30)));
    }

    @Test
    void paymentReactivatesFromAnyOpenState() {
        var c = new DunningCase(UUID.randomUUID(), "customer-1", failedAt);
        c.attemptFailed(day(1));
        c.attemptFailed(day(3));
        c.attemptFailed(day(7));
        c.attemptFailed(day(14));
        assertEquals("SUSPENDED", c.getStatus());
        assertEquals(DunningState.REACTIVATED, c.paymentSucceeded(day(15)));
        assertEquals(day(15), c.getResolvedAt());
        assertFalse(c.state().open());
        // Further failures or payments do not reopen a resolved case.
        assertEquals(DunningState.REACTIVATED, c.attemptFailed(day(16)));
    }
}
