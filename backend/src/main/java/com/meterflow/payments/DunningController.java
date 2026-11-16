package com.meterflow.payments;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dunning")
@PreAuthorize("hasRole('ADMIN')")
public class DunningController {
    private final PaymentService payments;

    public DunningController(PaymentService payments) { this.payments = payments; }

    @GetMapping
    public List<DunningCase> all() { return payments.all(); }

    @PostMapping("/{invoiceId}/failed")
    public DunningCase failed(@PathVariable UUID invoiceId) { return payments.recordFailure(invoiceId); }

    @PostMapping("/{invoiceId}/succeeded")
    public Map<String, Object> succeeded(@PathVariable UUID invoiceId) { return payments.recordSuccess(invoiceId); }

    /** Runs due retries now, or "as of" a future instant to demonstrate the day 1/3/7 schedule. */
    @PostMapping("/process")
    public Map<String, Object> process(@RequestParam(required = false) Instant asOf) {
        return payments.processDueRetries(asOf == null ? Instant.now() : asOf);
    }
}
