package com.meterflow.invoicing.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Starts, restarts and schedules the billing job.
 *
 * <p>Restart after a crash (QA3): a killed JVM leaves its job execution marked STARTED in the job
 * repository, which would block a restart. On startup (single scheduler instance assumed) those
 * stale executions are marked FAILED, so launching the same period again restarts the job from its
 * last committed chunk instead of starting over.</p>
 */
@Component
public class BillingJobLauncher {
    private static final Logger log = LoggerFactory.getLogger(BillingJobLauncher.class);

    private final JobLauncher launcher;
    private final Job billingJob;
    private final JobExplorer explorer;
    private final JobRepository repository;
    private final boolean recoverOnStartup;

    public BillingJobLauncher(JobLauncher launcher, Job billingJob, JobExplorer explorer, JobRepository repository,
                              @Value("${meterflow.billing.recover-stale-on-startup:true}") boolean recoverOnStartup) {
        this.launcher = launcher;
        this.billingJob = billingJob;
        this.explorer = explorer;
        this.repository = repository;
        this.recoverOnStartup = recoverOnStartup;
    }

    public Map<String, Object> launch(YearMonth period) throws Exception {
        var parameters = new JobParametersBuilder().addString("period", period.toString()).toJobParameters();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobName", billingJob.getName());
        result.put("period", period.toString());
        try {
            JobExecution execution = launcher.run(billingJob, parameters);
            result.put("executionId", execution.getId());
            result.put("status", execution.getStatus().toString());
            result.put("exitStatus", execution.getExitStatus().getExitCode());
            result.put("message", "Partitioned billing job ran. Re-run the same period to restart a failed execution.");
        } catch (JobInstanceAlreadyCompleteException done) {
            result.put("status", "COMPLETED");
            result.put("message", "Billing for this period already completed; invoices are not created twice.");
        }
        return result;
    }

    /** Marks executions left running by a crashed process as FAILED so they can be restarted. */
    public int recoverStaleExecutions() {
        int recovered = 0;
        for (JobExecution execution : explorer.findRunningJobExecutions(billingJob.getName())) {
            for (StepExecution step : execution.getStepExecutions()) {
                if (step.getStatus().isRunning()) {
                    step.setStatus(BatchStatus.FAILED);
                    step.setExitStatus(ExitStatus.FAILED.addExitDescription("Recovered after unclean shutdown"));
                    step.setEndTime(LocalDateTime.now());
                    repository.update(step);
                }
            }
            execution.setStatus(BatchStatus.FAILED);
            execution.setExitStatus(ExitStatus.FAILED.addExitDescription("Recovered after unclean shutdown"));
            execution.setEndTime(LocalDateTime.now());
            repository.update(execution);
            recovered++;
        }
        return recovered;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        if (!recoverOnStartup) return;
        int recovered = recoverStaleExecutions();
        if (recovered > 0) log.warn("Marked {} interrupted billing job execution(s) as FAILED; re-launch to resume", recovered);
    }

    /** Billing date: by default 00:30 UTC on the 1st, for the month that just closed. */
    @Scheduled(cron = "${meterflow.billing.cron:0 30 0 1 * *}", zone = "UTC")
    public void monthlyRun() {
        YearMonth previous = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        try {
            log.info("Scheduled billing run for {}: {}", previous, launch(previous));
        } catch (Exception e) {
            log.error("Scheduled billing run for {} failed; it can be restarted with POST /api/invoices/batch/run", previous, e);
        }
    }
}
