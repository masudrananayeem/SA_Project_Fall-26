package com.meterflow.metering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LateUsageAdjustmentRepository extends JpaRepository<LateUsageAdjustment, UUID> {
    List<LateUsageAdjustment> findByCustomerIdAndBillingPeriodAndStatus(String customer, String period, String status);
    List<LateUsageAdjustment> findByCustomerIdAndStatus(String customer, String status);
    List<LateUsageAdjustment> findByUsageEventIdIn(Collection<UUID> usageEventIds);
    List<LateUsageAdjustment> findByCustomerIdOrderByCreatedAtDesc(String customer);
}
