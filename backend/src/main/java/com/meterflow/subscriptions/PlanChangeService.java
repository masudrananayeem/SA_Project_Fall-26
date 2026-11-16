package com.meterflow.subscriptions;

import com.meterflow.catalogue.Plan;
import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mid-cycle plan changes (FR4) and version migration (FR10).
 *
 * <p>Proration rule: the month-end invoice charges the plan that is current at billing time for
 * the whole month, so a change on day d records a delta of
 * {@code (recurring(old) - recurring(new)) x elapsedDays / daysInMonth}. Summed over any number of
 * changes this charges each plan exactly for the days it was active. Usage in the month is priced
 * by the plan current at billing time.</p>
 */
@Service
public class PlanChangeService {
    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final PlanChangeRepository changes;
    private final PricingService pricing;

    public PlanChangeService(SubscriptionRepository subscriptions, PlanRepository plans,
                             PlanChangeRepository changes, PricingService pricing) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.changes = changes;
        this.pricing = pricing;
    }

    /** Pure proration calculation, exposed for tests and the migration dry run. */
    public static BigDecimal prorationDelta(BigDecimal oldRecurring, BigDecimal newRecurring, LocalDate effective) {
        YearMonth month = YearMonth.from(effective);
        long elapsed = ChronoUnit.DAYS.between(month.atDay(1), effective);
        return oldRecurring.subtract(newRecurring)
                .multiply(BigDecimal.valueOf(elapsed))
                .divide(BigDecimal.valueOf(month.lengthOfMonth()), 2, RoundingMode.HALF_UP);
    }

    BigDecimal deltaFor(Subscription sub, Plan from, Plan to, LocalDate effective) {
        if (!from.getCurrency().equals(to.getCurrency())) throw new IllegalArgumentException("Plan change currencies must match");
        // Days inside a trial were never charged, so they are not refunded either.
        if (sub.getTrialEnd() != null && effective.isBefore(sub.getTrialEnd())) return BigDecimal.ZERO.setScale(2);
        BigDecimal seats = BigDecimal.valueOf(sub.getSeatCount());
        return prorationDelta(pricing.recurringAmount(from, seats), pricing.recurringAmount(to, seats), effective);
    }

    private PlanChange record(Subscription sub, Plan from, Plan to, LocalDate effective, String kind) {
        BigDecimal delta = deltaFor(sub, from, to, effective);
        String explanation = kind + " change " + from.getPlanKey() + " v" + from.getVersion() + " -> " + to.getPlanKey()
                + " v" + to.getVersion() + " on " + effective + ": (old recurring - new recurring) x elapsed days / days in month";
        return changes.save(new PlanChange(sub.getId(), from.getId(), to.getId(), delta, to.getCurrency(), effective, kind, explanation));
    }

    /** Upgrade/downgrade now, with proration posted to this month's invoice. */
    @Transactional
    public PlanChange changeNow(UUID subscriptionId, UUID planId) {
        var sub = subscriptions.findById(subscriptionId).orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        var from = plans.findById(sub.getPlanId()).orElseThrow();
        var to = plans.findById(planId).orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate effective = today.isBefore(sub.getPeriodStart()) ? sub.getPeriodStart() : today;
        if (!effective.isBefore(sub.getPeriodEnd())) throw new IllegalStateException("Subscription period ended");
        var audit = record(sub, from, to, effective, "IMMEDIATE");
        sub.changePlan(to.getId());
        subscriptions.save(sub);
        return audit;
    }

    /** Change at the end of the current billing month: no proration is needed. */
    @Transactional
    public Subscription changeAtPeriodEnd(UUID subscriptionId, UUID planId) {
        var sub = subscriptions.findById(subscriptionId).orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        var to = plans.findById(planId).orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        var from = plans.findById(sub.getPlanId()).orElseThrow();
        if (!from.getCurrency().equals(to.getCurrency())) throw new IllegalArgumentException("Plan change currencies must match");
        sub.schedulePlanChange(to.getId(), YearMonth.now(ZoneOffset.UTC).plusMonths(1).atDay(1));
        return subscriptions.save(sub);
    }

    /** Applies a due scheduled change, recording its proration (zero on a month boundary). */
    @Transactional
    public boolean applyScheduled(Subscription sub, LocalDate today) {
        if (!sub.isScheduledChangeDue(today) || !sub.state().canChangePlan()) return false;
        var from = plans.findById(sub.getPlanId()).orElseThrow();
        var to = plans.findById(sub.getScheduledPlanId()).orElseThrow();
        record(sub, from, to, sub.getScheduledChangeDate(), "SCHEDULED");
        return sub.applyScheduledChange(today);
    }

    /**
     * Moves active subscriptions pinned to one version of a plan to another version (latest by
     * default). With dryRun=true nothing is changed and the per-subscription impact is returned.
     */
    @Transactional
    public Map<String, Object> migrate(String planKey, int fromVersion, Integer toVersion, boolean dryRun) {
        var from = plans.findByPlanKeyAndVersion(planKey, fromVersion)
                .orElseThrow(() -> new IllegalArgumentException("Source plan version not found"));
        var to = toVersion == null
                ? plans.findTopByPlanKeyOrderByVersionDesc(planKey).orElseThrow()
                : plans.findByPlanKeyAndVersion(planKey, toVersion).orElseThrow(() -> new IllegalArgumentException("Target plan version not found"));
        if (to.getId().equals(from.getId())) throw new IllegalArgumentException("Source and target versions are the same");
        if (!from.getCurrency().equals(to.getCurrency())) throw new IllegalArgumentException("Migration currencies must match");
        LocalDate effective = LocalDate.now(ZoneOffset.UTC);
        List<Map<String, Object>> impact = new ArrayList<>();
        for (var sub : subscriptions.findByStatus(SubscriptionState.ACTIVE.name())) {
            if (!sub.getPlanId().equals(from.getId())) continue;
            BigDecimal seats = BigDecimal.valueOf(sub.getSeatCount());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subscriptionId", sub.getId());
            row.put("customerId", sub.getCustomerId());
            row.put("oldRecurring", pricing.recurringAmount(from, seats));
            row.put("newRecurring", pricing.recurringAmount(to, seats));
            row.put("prorationDelta", deltaFor(sub, from, to, effective));
            impact.add(row);
            if (!dryRun) {
                record(sub, from, to, effective, "MIGRATION");
                sub.changePlan(to.getId());
                subscriptions.save(sub);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planKey", planKey);
        result.put("fromVersion", from.getVersion());
        result.put("toVersion", to.getVersion());
        result.put("dryRun", dryRun);
        result.put("affectedSubscriptions", impact.size());
        result.put("impact", impact);
        return result;
    }
}
