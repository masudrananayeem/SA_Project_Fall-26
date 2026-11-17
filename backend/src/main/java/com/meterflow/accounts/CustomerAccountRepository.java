package com.meterflow.accounts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, UUID> {
    Optional<CustomerAccount> findByUsername(String username);
    boolean existsByUsername(String username);
    List<CustomerAccount> findAllByOrderByCreatedAtDesc();
}
