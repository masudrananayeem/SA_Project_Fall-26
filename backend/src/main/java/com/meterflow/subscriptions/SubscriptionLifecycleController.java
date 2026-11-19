package com.meterflow.subscriptions;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Runs the daily date-based transitions (scheduled plan changes, trial conversion or expiry) on
 * demand. The scheduler does this at 00:10 UTC; on Cloudflare the container may be asleep then, so
 * the Worker's daily cron trigger calls this endpoint instead. Idempotent.
 */
@RestController
@RequestMapping("/api/subscriptions/lifecycle")
public class SubscriptionLifecycleController {
    private final SubscriptionLifecycleService lifecycle;

    public SubscriptionLifecycleController(SubscriptionLifecycleService lifecycle) { this.lifecycle = lifecycle; }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/run")
    public Map<String, Integer> run(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return lifecycle.applyDateTransitions(today == null ? LocalDate.now(ZoneOffset.UTC) : today);
    }
}
