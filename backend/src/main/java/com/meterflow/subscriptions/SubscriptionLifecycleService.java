package com.meterflow.subscriptions;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Applies date-driven subscription transitions in an idempotent daily job. */
@Service
public class SubscriptionLifecycleService {
    private final SubscriptionRepository repo;
    public SubscriptionLifecycleService(SubscriptionRepository repo) { this.repo = repo; }

    @Scheduled(cron = "0 10 0 * * *")
    @Transactional
    public void applyScheduledPlanChanges() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (var s : repo.findByStatus("ACTIVE")) {
            boolean changed = s.applyScheduledChange(today);
            changed |= s.expireTrial(today);
            if (changed) repo.save(s);
        }
    }
}
