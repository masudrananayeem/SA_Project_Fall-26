package com.meterflow.subscriptions;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/** Applies date-driven transitions (scheduled plan changes, trial conversion/expiry) once a day. Idempotent. */
@Service
public class SubscriptionLifecycleService {
    private final SubscriptionRepository repo;
    private final PlanChangeService planChanges;

    public SubscriptionLifecycleService(SubscriptionRepository repo, PlanChangeService planChanges) {
        this.repo = repo;
        this.planChanges = planChanges;
    }

    @Scheduled(cron = "${meterflow.subscriptions.lifecycle-cron:0 10 0 * * *}", zone = "UTC")
    public void scheduledRun() { applyDateTransitions(LocalDate.now(ZoneOffset.UTC)); }

    @Transactional
    public Map<String, Integer> applyDateTransitions(LocalDate today) {
        int planChanges = 0, trials = 0;
        for (var s : repo.findByStatus(SubscriptionState.ACTIVE.name())) {
            boolean changed = false;
            if (this.planChanges.applyScheduled(s, today)) { planChanges++; changed = true; }
            if (s.expireTrial(today)) { trials++; changed = true; }
            if (changed) repo.save(s);
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("scheduledPlanChangesApplied", planChanges);
        result.put("trialsEnded", trials);
        return result;
    }
}
