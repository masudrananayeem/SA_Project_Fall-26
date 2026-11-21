package com.meterflow.subscriptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
 List<Subscription> findByStatus(String status);
 Page<Subscription> findByStatus(String status, Pageable pageable);
 Optional<Subscription> findTopByCustomerIdAndStatusOrderByPeriodStartDesc(String customer, String status);
 List<Subscription> findByCustomerIdOrderByPeriodStartDesc(String customer);
 Page<Subscription> findByCustomerIdOrderByPeriodStartDesc(String customer, Pageable pageable);

 /** Customers to invoice for a month starting on {@code from}: active, or left the active state on/after that day. */
 @Query("select distinct s.customerId from Subscription s where s.status = 'ACTIVE' "
         + "or (s.status <> 'EXPIRED' and s.statusChangedAt >= :from) order by s.customerId")
 List<String> findBillableCustomerIds(@Param("from") LocalDate from);

 /** Every customer that may need an invoice for some month (the batch job narrows it per period). */
 @Query("select distinct s.customerId from Subscription s where s.status = 'ACTIVE' "
         + "or (s.status <> 'EXPIRED' and s.statusChangedAt is not null) order by s.customerId")
 List<String> findBillingCandidateCustomerIds();
}
