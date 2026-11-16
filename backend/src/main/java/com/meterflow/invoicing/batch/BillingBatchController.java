package com.meterflow.invoicing.batch;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices/batch")
public class BillingBatchController {
    private final BillingJobLauncher jobs;

    public BillingBatchController(BillingJobLauncher jobs) { this.jobs = jobs; }

    /** Runs (or restarts) the partitioned billing job for a closed month. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/run")
    public Map<String, Object> run(@RequestParam String period) throws Exception {
        YearMonth ym;
        try {
            ym = YearMonth.parse(period);
        } catch (Exception e) {
            throw new IllegalArgumentException("period must be YYYY-MM");
        }
        return jobs.launch(ym);
    }

    /** Manual recovery of executions left STARTED by a killed process (also done on startup). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/recover")
    public Map<String, Object> recover() { return Map.of("recoveredExecutions", jobs.recoverStaleExecutions()); }
}
