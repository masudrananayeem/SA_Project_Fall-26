package com.meterflow.payments;

import java.time.Duration;
import java.time.Instant;

/**
 * State pattern for payment recovery (FR8).
 *
 * <pre>
 * payment fails -> RETRYING (retries on day 1, 3 and 7 after the failure)
 * RETRYING  --day-7 retry fails--> PAST_DUE (grace period, one more attempt at its end)
 * PAST_DUE  --final attempt fails--> SUSPENDED (subscription paused)
 * any open state --payment succeeds--> REACTIVATED (subscription resumed immediately)
 * </pre>
 */
public enum DunningState {
    RETRYING {
        @Override DunningState onAttemptFailed(DunningCase c, Instant now, Duration grace) {
            int attempt = c.incrementAttempt();
            if (attempt < RETRY_DAYS.length) {
                c.scheduleNext(c.getCreatedAt().plus(Duration.ofDays(RETRY_DAYS[attempt])));
                return RETRYING;
            }
            c.markPastDue(now, now.plus(grace));
            return PAST_DUE;
        }
    },
    PAST_DUE {
        @Override DunningState onAttemptFailed(DunningCase c, Instant now, Duration grace) {
            c.incrementAttempt();
            c.markSuspended(now);
            return SUSPENDED;
        }
    },
    SUSPENDED {
        @Override public boolean scheduledRetries() { return false; }
    },
    REACTIVATED {
        @Override public boolean open() { return false; }
        @Override public boolean scheduledRetries() { return false; }
        @Override DunningState onPaymentSucceeded(DunningCase c, Instant now) { return REACTIVATED; }
    };

    /** Retry days counted from the failed payment. */
    public static final int[] RETRY_DAYS = {1, 3, 7};

    DunningState onAttemptFailed(DunningCase c, Instant now, Duration grace) { return this; }

    DunningState onPaymentSucceeded(DunningCase c, Instant now) {
        c.markResolved(now);
        return REACTIVATED;
    }

    public boolean open() { return true; }

    public boolean scheduledRetries() { return true; }
}
