package com.meterflow.subscriptions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PlanChangeRepository extends JpaRepository<PlanChange, UUID> {
    List<PlanChange> findBySubscriptionIdOrderByEffectiveDateDesc(UUID subscriptionId);

    /** Uninvoiced proration records that took effect before the given (exclusive) date. */
    List<PlanChange> findBySubscriptionIdAndInvoiceIdIsNullAndEffectiveDateBefore(UUID subscriptionId, LocalDate before);
}
