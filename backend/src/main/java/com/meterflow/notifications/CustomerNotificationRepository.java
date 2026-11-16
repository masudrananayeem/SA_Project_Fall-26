package com.meterflow.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerNotificationRepository extends JpaRepository<CustomerNotification, UUID> {
    List<CustomerNotification> findTop50ByCustomerIdOrderByCreatedAtDesc(String customerId);
}
