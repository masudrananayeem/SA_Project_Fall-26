package com.meterflow.metering;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant; import java.util.*;
public interface UsageEventRepository extends JpaRepository<UsageEvent,UUID>{ boolean existsByIdempotencyKey(String key); List<UsageEvent> findByCustomerIdAndTimestampBetween(String customerId,Instant from,Instant to); List<UsageEvent> findByCustomerIdAndMetricAndTimestampBetween(String customerId,String metric,Instant from,Instant to); }
