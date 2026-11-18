package com.meterflow.subscriptions;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** State pattern: prepaid PENDING_PAYMENT and postpaid BLOCKED. */
class AccountStandingStateTest {
    private Subscription sub() {
        return new Subscription("acme", UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusYears(1));
    }

    @Test
    void prepaidWaitsForPaymentThenActivates() {
        var s = sub();
        s.awaitPayment();
        assertEquals("PENDING_PAYMENT", s.getStatus());
        assertFalse(s.state().acceptsUsage());
        assertFalse(s.state().billable());
        s.resumeFromDunning(); // payment received
        assertEquals("ACTIVE", s.getStatus());
        assertTrue(s.state().acceptsUsage());
    }

    @Test
    void postpaidBlockAndUnblock() {
        var s = sub();
        s.block();
        assertEquals("BLOCKED", s.getStatus());
        assertFalse(s.state().acceptsUsage());
        assertThrows(IllegalStateException.class, () -> s.changePlan(UUID.randomUUID()));
        s.unblock();
        assertEquals("ACTIVE", s.getStatus());
    }

    @Test
    void dunningSuspensionCanEscalateToBlocked() {
        var s = sub();
        s.pauseForDunning();
        assertEquals("PAUSED_DUNNING", s.getStatus());
        assertFalse(s.state().acceptsUsage());
        s.block();
        assertEquals("BLOCKED", s.getStatus());
    }

    @Test
    void blockIsIgnoredWhenNotApplicable() {
        var s = sub();
        s.pause();
        s.block();
        assertEquals("PAUSED", s.getStatus(), "a manual pause is never overridden");
        s.cancel();
        s.unblock();
        assertEquals("CANCELLED", s.getStatus());
        assertThrows(IllegalStateException.class, s::awaitPayment);
    }
}
