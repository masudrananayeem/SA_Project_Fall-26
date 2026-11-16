package com.meterflow.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
    List<Plan> findAllByOrderByPlanKeyAscVersionDesc();
    List<Plan> findByPlanKeyOrderByVersionDesc(String planKey);
    Optional<Plan> findTopByPlanKeyOrderByVersionDesc(String key);
    Optional<Plan> findByPlanKeyAndVersion(String key, int version);
}
