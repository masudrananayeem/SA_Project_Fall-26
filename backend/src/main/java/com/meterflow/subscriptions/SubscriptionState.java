package com.meterflow.subscriptions;

/**
 * State pattern for the subscription lifecycle (FR4, FR8). Each state decides which transitions
 * are legal; illegal transitions throw instead of silently corrupting the status column.
 */
public enum SubscriptionState {
    ACTIVE {
        @Override SubscriptionState pause() { return PAUSED; }
        @Override SubscriptionState resume() { return ACTIVE; }
        @Override SubscriptionState suspendForDunning() { return PAUSED_DUNNING; }
        @Override SubscriptionState reactivateAfterPayment() { return ACTIVE; }
        @Override SubscriptionState cancel() { return CANCELLED; }
        @Override SubscriptionState expire() { return EXPIRED; }
        @Override public boolean billable() { return true; }
        @Override public boolean canChangePlan() { return true; }
    },
    /** Paused by an administrator. Dunning never overrides a manual pause. */
    PAUSED {
        @Override SubscriptionState pause() { return PAUSED; }
        @Override SubscriptionState resume() { return ACTIVE; }
        @Override SubscriptionState suspendForDunning() { return PAUSED; }
        @Override SubscriptionState reactivateAfterPayment() { return PAUSED; }
        @Override SubscriptionState cancel() { return CANCELLED; }
        @Override SubscriptionState expire() { return EXPIRED; }
    },
    /** Suspended because an invoice stayed unpaid after the retry schedule. */
    PAUSED_DUNNING {
        @Override SubscriptionState pause() { return PAUSED; }
        @Override SubscriptionState resume() {
            throw new IllegalStateException("A subscription suspended for non-payment is reactivated by a successful payment");
        }
        @Override SubscriptionState suspendForDunning() { return PAUSED_DUNNING; }
        @Override SubscriptionState reactivateAfterPayment() { return ACTIVE; }
        @Override SubscriptionState cancel() { return CANCELLED; }
        @Override SubscriptionState expire() { return EXPIRED; }
    },
    CANCELLED,
    /** Trial ended without conversion. */
    EXPIRED;

    SubscriptionState pause() { throw illegal("pause"); }
    SubscriptionState resume() { throw illegal("resume"); }
    /** Terminal states ignore dunning signals instead of failing the payment workflow. */
    SubscriptionState suspendForDunning() { return this; }
    SubscriptionState reactivateAfterPayment() { return this; }
    SubscriptionState cancel() { if (this == CANCELLED) return this; throw illegal("cancel"); }
    SubscriptionState expire() { throw illegal("expire"); }
    public boolean billable() { return false; }
    public boolean canChangePlan() { return false; }

    IllegalStateException illegal(String action) {
        return new IllegalStateException("Cannot " + action + " a subscription in state " + name());
    }
}
