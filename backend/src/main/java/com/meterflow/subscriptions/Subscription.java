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
            status = state().expire().name();
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

    public void pause() { status = state().pause().name(); }
    public void resume() { status = state().resume().name(); }
    public void pauseForDunning() { status = state().suspendForDunning().name(); }
    /** Prepaid: the package for the period is invoiced; usage waits for the payment. */
    public void awaitPayment() { status = state().awaitPayment().name(); }
    /** Postpaid: blocked for unpaid invoices (no-op in states it does not apply to). */
    public void block() { status = state().block().name(); }
    public void unblock() { status = state().unblock().name(); }
    public void resumeFromDunning() { status = state().reactivateAfterPayment().name(); }
    public void cancel() {
        status = state().cancel().name();
        scheduledPlanId = null;
        scheduledChangeDate = null;
    }
}
