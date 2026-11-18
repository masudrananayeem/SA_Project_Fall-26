package com.meterflow.payments;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {
    List<PaymentRecord> findTop200ByOrderByCreatedAtDesc();
    List<PaymentRecord> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    List<PaymentRecord> findByCreatedAtGreaterThanEqual(Instant from);
}
