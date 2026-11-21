package com.meterflow.subscriptions;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {
    public static final String TRIALING = "TRIALING";
    public static final String TRIAL_CONVERTED = "CONVERTED";
    public static final String TRIAL_EXPIRED = "EXPIRED";

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private String customerId;
    @Column(nullable = false) private UUID planId;
    @Column(nullable = false) private String status = SubscriptionState.ACTIVE.name();
    @Column(nullable = false) private LocalDate periodStart;
    @Column(nullable = false) private LocalDate periodEnd;
    @org.hibernate.annotations.ColumnDefault("1") @Column(nullable = false) private int seatCount = 1;
    /** Kept after the trial ends: the billing month that contains it is prorated. */
    @Column private LocalDate trialEnd;
    @Column(name = "trial_status") private String trialStatus;
    @org.hibernate.annotations.ColumnDefault("true") @Column(name = "trial_auto_convert", nullable = false) private boolean trialAutoConvert = true;
    @Column private UUID scheduledPlanId;
    @Column private LocalDate scheduledChangeDate;
    /** Day the status last changed: a subscription that left ACTIVE during a month is still invoiced for that month. */
    @Column(name = "status_changed_at") private LocalDate statusChangedAt;
    @Version private long version;

    protected Subscription() { }

    public Subscription(String customerId, UUID planId, LocalDate periodStart, LocalDate periodEnd) {
        this.customerId = customerId;
        this.planId = planId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public UUID getPlanId() { return planId; }
    public String getStatus() { return status; }
    public SubscriptionState state() { return SubscriptionState.valueOf(status); }
    public LocalDate getStatusChangedAt() { return statusChangedAt; }

    /** Applies a State-pattern transition and remembers the day the status changed. */
    private void moveTo(SubscriptionState next) {
        if (next.name().equals(status)) return;
        status = next.name();
        statusChangedAt = LocalDate.now(java.time.ZoneOffset.UTC);
    }

    /**
     * True when this subscription must be invoiced for {@code period}: it is active, or it left the
     * active state (paused, blocked, suspended, cancelled, waiting for payment) on or after the first
     * day of the period, so usage accepted before that day is billed exactly once. A trial that expired
     * without converting is never invoiced.
     */
    public boolean billableIn(java.time.YearMonth period) {
        if (SubscriptionState.ACTIVE.name().equals(status)) return true;
        if (SubscriptionState.EXPIRED.name().equals(status)) return false;
        return statusChangedAt != null && !statusChangedAt.isBefore(period.atDay(1));
    }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public int getSeatCount() { return seatCount; }
    public LocalDate getTrialEnd() { return trialEnd; }
    public String getTrialStatus() { return trialStatus; }
    public boolean isTrialAutoConvert() { return trialAutoConvert; }
    public UUID getScheduledPlanId() { return scheduledPlanId; }
    public LocalDate getScheduledChangeDate() { return scheduledChangeDate; }

    public boolean isTrialing() {
        return TRIALING.equals(trialStatus) && trialEnd != null && LocalDate.now(ZoneOffset.UTC).isBefore(trialEnd);
    }

    public void setSeatCount(int seats) {
        if (seats < 1) throw new IllegalArgumentException("Seat count must be at least one");
        seatCount = seats;
    }

    public void startTrial(LocalDate end) { startTrial(end, true); }

    public void startTrial(LocalDate end, boolean autoConvert) {
        if (end == null || !end.isAfter(LocalDate.now(ZoneOffset.UTC))) throw new IllegalArgumentException("Trial end must be in the future");
        trialEnd = end;
        trialStatus = TRIALING;
        trialAutoConvert = autoConvert;
    }

    /**
     * Ends a running trial on or after its end date: it converts to a paid subscription, or the
     * subscription expires when auto-conversion is off. Returns true only on the transition day.
     */
    public boolean expireTrial(LocalDate today) {
        if (!TRIALING.equals(trialStatus) || trialEnd == null || today.isBefore(trialEnd)) return false;
        if (trialAutoConvert) {
            trialStatus = TRIAL_CONVERTED;
        } else {
            trialStatus = TRIAL_EXPIRED;
            moveTo(state().expire());
        }
        return true;
    }

    public void schedulePlanChange(UUID planId, LocalDate date) {
        if (!date.isAfter(LocalDate.now(ZoneOffset.UTC))) throw new IllegalArgumentException("Scheduled change date must be in the future");
        if (!state().canChangePlan()) throw state().illegal("schedule a plan change for");
        scheduledPlanId = planId;
        scheduledChangeDate = date;
    }

    public boolean isScheduledChangeDue(LocalDate today) {
        return scheduledPlanId != null && scheduledChangeDate != null && !today.isBefore(scheduledChangeDate);
    }

    public boolean applyScheduledChange(LocalDate today) {
        if (!isScheduledChangeDue(today)) return false;
        planId = scheduledPlanId;
        scheduledPlanId = null;
        scheduledChangeDate = null;
        return true;
    }

    public void changePlan(UUID newPlanId) {
        if (!state().canChangePlan()) throw new IllegalStateException("Only active subscriptions can change plans");
        planId = newPlanId;
    }

    public void pause() { moveTo(state().pause()); }
    public void resume() { moveTo(state().resume()); }
    public void pauseForDunning() { moveTo(state().suspendForDunning()); }
    /** Prepaid: the package for the period is invoiced; usage waits for the payment. */
    public void awaitPayment() { moveTo(state().awaitPayment()); }
    /** Postpaid: blocked for unpaid invoices (no-op in states it does not apply to). */
    public void block() { moveTo(state().block()); }
    public void unblock() { moveTo(state().unblock()); }
    public void resumeFromDunning() { moveTo(state().reactivateAfterPayment()); }
    public void cancel() {
        moveTo(state().cancel());
        scheduledPlanId = null;
        scheduledChangeDate = null;
    }
}
