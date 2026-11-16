package com.meterflow.notifications;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** In-app notification: every billing event is recorded for the customer even without a webhook endpoint. */
@Entity
@Table(name = "customer_notifications", indexes = @Index(name = "idx_customer_notifications_customer", columnList = "customer_id, created_at"))
public class CustomerNotification {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "customer_id", nullable = false) private String customerId;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(nullable = false, length = 1000) private String message;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected CustomerNotification() { }

    public CustomerNotification(String customerId, String eventType, String message) {
        this.customerId = customerId;
        this.eventType = eventType;
        this.message = message.length() > 1000 ? message.substring(0, 999) : message;
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
