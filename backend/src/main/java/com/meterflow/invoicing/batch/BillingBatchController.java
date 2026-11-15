package com.meterflow.invoicing.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.YearMonth;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices/batch")
public class BillingBatchController {
    private final JobLauncher launcher;
    private final Job billingJob;
    public BillingBatchController(JobLauncher launcher, Job billingJob) { this.launcher = launcher; this.billingJob = billingJob; }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/run")
    public Map<String,Object> run(@RequestParam String period) throws Exception {
        YearMonth.parse(period);
        var parameters = new JobParametersBuilder().addString("period", period).toJobParameters();
        var execution = launcher.run(billingJob, parameters);
        return Map.of("jobName", execution.getJobInstance().getJobName(), "executionId", execution.getId(),
                "status", execution.getStatus().toString(), "period", period,
                "message", "Partitioned customer billing job submitted. Re-run with the same period to restart a failed execution.");
    }
}
