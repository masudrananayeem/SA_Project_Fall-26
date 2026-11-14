package com.meterflow.invoicing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InvoiceRepository extends JpaRepository<Invoice,UUID> {
 Optional<Invoice> findByCustomerIdAndPeriod(String customerId,String period);
 List<Invoice> findByCustomerIdOrderByCreatedAtDesc(String customerId);
 Page<Invoice> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);
}
