package com.meterflow.payments;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Payment ledger (admin live feed, customer payment history) and account-standing control. */
@RestController
@RequestMapping("/api/payments")
public class PaymentLedgerController {
    private final PaymentService payments;

    public PaymentLedgerController(PaymentService payments) { this.payments = payments; }

    /** Admin: latest 200 (or one customer's) payments. Customer: own payments only. */
    @GetMapping
    public List<PaymentRecord> list(@RequestParam(required = false) String customerId, Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (admin) return payments.recent(customerId);
        if (customerId != null && !customerId.equals(auth.getName()))
            throw new AccessDeniedException("Customers may only see their own payments");
        return payments.recent(auth.getName());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/summary")
    public Map<String, Object> summary() { return payments.summary(); }

    /** Runs the automatic block/unblock check now (normally hourly). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/standing/enforce")
    public Map<String, Object> enforce() { return payments.enforceStanding(null); }
}
