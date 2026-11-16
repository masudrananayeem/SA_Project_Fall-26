package com.meterflow.payments;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dunning_cases")
public class DunningCase {
    public static final Duration DEFAULT_GRACE = Duration.ofDays(7);

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true) private UUID invoiceId;
    @Column(nullable = false) private String customerId;
    @Column(nullable = false) private String status = DunningState.RETRYING.name();
    /** Number of failed collection attempts after the initial failure. */
    @Column(nullable = false) private int attempt = 0;
    @Column private Instant nextAttemptAt;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "past_due_at") private Instant pastDueAt;
    @Column(name = "suspended_at") private Instant suspendedAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @org.hibernate.annotations.ColumnDefault("0") @Version private long version;

    protected DunningCase() { }

    public DunningCase(UUID invoiceId, String customerId) { this(invoiceId, customerId, Instant.now()); }

    public DunningCase(UUID invoiceId, String customerId, Instant failedAt) {
        this.invoiceId = invoiceId;
        this.customerId = customerId;
        this.createdAt = failedAt;
        this.nextAttemptAt = failedAt.plus(Duration.ofDays(DunningState.RETRY_DAYS[0]));
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public DunningState state() { return DunningState.valueOf(status); }
    public int getAttempt() { return attempt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPastDueAt() { return pastDueAt; }
    public Instant getSuspendedAt() { return suspendedAt; }
    public Instant getResolvedAt() { return resolvedAt; }

    public boolean isDue(Instant now) {
        return state().scheduledRetries() && nextAttemptAt != null && !nextAttemptAt.isAfter(now);
    }

    /** A collection attempt failed. Returns the new state. */
    public DunningState attemptFailed(Instant now) { return attemptFailed(now, DEFAULT_GRACE); }

    public DunningState attemptFailed(Instant now, Duration grace) {
        status = state().onAttemptFailed(this, now, grace).name();
        return state();
    }

    /** Backwards-compatible alias used by older callers. */
    public void failed() { attemptFailed(Instant.now()); }

    public DunningState paymentSucceeded(Instant now) {
        status = state().onPaymentSucceeded(this, now).name();
        return state();
    }

    public void paid() { paymentSucceeded(Instant.now()); }

    // --- mutators used by the states ---
    int incrementAttempt() { return ++attempt; }
    void scheduleNext(Instant at) { nextAttemptAt = at; }
    void markPastDue(Instant now, Instant finalAttemptAt) { pastDueAt = now; nextAttemptAt = finalAttemptAt; }
    void markSuspended(Instant now) { suspendedAt = now; nextAttemptAt = null; }
    void markResolved(Instant now) { resolvedAt = now; nextAttemptAt = null; }
}
