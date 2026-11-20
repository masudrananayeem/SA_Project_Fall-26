package com.meterflow.invoicing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UpgradeChargeRepository extends JpaRepository<UpgradeCharge, UUID> {
    Optional<UpgradeCharge> findByPlanChangeId(UUID planChangeId);
    List<UpgradeCharge> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    List<UpgradeCharge> findByCustomerIdAndStatus(String customerId, String status);
    List<UpgradeCharge> findByStatusOrderByCreatedAtDesc(String status);
    List<UpgradeCharge> findTop200ByOrderByCreatedAtDesc();
}
