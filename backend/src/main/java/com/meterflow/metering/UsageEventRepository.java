package com.meterflow.metering;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface UsageEventRepository extends JpaRepository<UsageEvent, UUID> {
    boolean existsByIdempotencyKey(String key);
    List<UsageEvent> findByCustomerIdAndTimestampGreaterThanEqualAndTimestampLessThan(String customerId, Instant from, Instant to);
    List<UsageEvent> findByCustomerIdAndMetricAndTimestampGreaterThanEqualAndTimestampLessThan(String customerId, String metric, Instant from, Instant to);
    @Query("select e from UsageEvent e where e.customerId=:customer and e.timestamp >= :from and e.timestamp < :to")
    List<UsageEvent> findWindow(@Param("customer") String customerId, @Param("from") Instant from, @Param("to") Instant to);
    @Query("select e from UsageEvent e where e.customerId=:customer and e.metric=:metric and e.timestamp >= :from and e.timestamp < :to")
    List<UsageEvent> findMetricWindow(@Param("customer") String customerId, @Param("metric") String metric, @Param("from") Instant from, @Param("to") Instant to);
}
