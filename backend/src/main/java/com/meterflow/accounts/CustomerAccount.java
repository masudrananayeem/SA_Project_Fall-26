package com.meterflow.accounts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A self-service (or admin-created) customer login. The username is the customer ID used by every
 * customer-scoped endpoint (usage, invoices, budgets…), so a customer only ever sees its own data.
 */
@Entity
@Table(name = "customer_accounts", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class CustomerAccount {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 60) private String username;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;
    @Column(nullable = false, length = 200) private String email;
    @Column(length = 160) private String company;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected CustomerAccount() { }

    public CustomerAccount(String username, String passwordHash, String displayName, String email, String company) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.email = email;
        this.company = company;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    /** The customer ID is the username. */
    public String getCustomerId() { return username; }
    @JsonIgnore public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getCompany() { return company; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void updateProfile(String displayName, String email, String company) {
        this.displayName = displayName;
        this.email = email;
        this.company = company;
    }
}
