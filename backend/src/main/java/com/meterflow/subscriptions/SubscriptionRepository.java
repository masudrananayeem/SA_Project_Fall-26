package com.meterflow.subscriptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
 List<Subscription> findByStatus(String status);
 Page<Subscription> findByStatus(String status, Pageable pageable);
 Optional<Subscription> findTopByCustomerIdAndStatusOrderByPeriodStartDesc(String customer, String status);
 List<Subscription> findByCustomerIdOrderByPeriodStartDesc(String customer);
 Page<Subscription> findByCustomerIdOrderByPeriodStartDesc(String customer, Pageable pageable);
}
