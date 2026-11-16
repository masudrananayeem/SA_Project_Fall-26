package com.meterflow.metering;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Read-only view of the late-usage ledger. Late usage is billed automatically on the next invoice. */
@RestController
@RequestMapping("/api/late-adjustments")
public class LateAdjustmentController {
    private final LateUsageAdjustmentRepository repo;

    public LateAdjustmentController(LateUsageAdjustmentRepository repo) { this.repo = repo; }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<LateUsageAdjustment> list(@RequestParam String customerId, @RequestParam(required = false) String period,
                                          @RequestParam(required = false) String status) {
        if (period != null && status != null) return repo.findByCustomerIdAndBillingPeriodAndStatus(customerId, period, status);
        if (status != null) return repo.findByCustomerIdAndStatus(customerId, status);
        return repo.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }
}
