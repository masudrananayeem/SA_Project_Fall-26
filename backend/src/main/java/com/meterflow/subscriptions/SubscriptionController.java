package com.meterflow.subscriptions;

import com.meterflow.catalogue.PlanRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final SubscriptionRepository repo;
    private final PlanRepository plans;
    private final PlanChangeRepository changes;
    private final PlanChangeService planChanges;
    private final ApplicationEventPublisher events;

    public SubscriptionController(SubscriptionRepository repo, PlanRepository plans, PlanChangeRepository changes,
                                  PlanChangeService planChanges, ApplicationEventPublisher events) {
        this.repo = repo;
        this.plans = plans;
        this.changes = changes;
        this.planChanges = planChanges;
        this.events = events;
    }

    /** True while the customer holds a subscription that is not cancelled or expired. */
    private boolean hasLiveSubscription(String customerId) {
        return repo.findByCustomerIdOrderByPeriodStartDesc(customerId).stream()
                .anyMatch(s -> !SubscriptionState.CANCELLED.name().equals(s.getStatus()) && !SubscriptionState.EXPIRED.name().equals(s.getStatus()));
    }

    /** Saves a new subscription; a prepaid plan starts in PENDING_PAYMENT and gets its package invoice. */
    private Subscription start(Subscription sub, com.meterflow.catalogue.Plan plan) {
        if (plan.isPrepaid()) sub.awaitPayment();
        var saved = repo.save(sub);
        events.publishEvent(new SubscriptionStarted(saved.getId(), saved.getCustomerId(), plan.getId(), plan.isPrepaid()));
        return saved;
    }

    public record Input(@NotBlank String customerId, @NotNull UUID planId, @NotNull LocalDate periodStart,
                        @NotNull LocalDate periodEnd, Integer seatCount, LocalDate trialEnd, Boolean trialAutoConvert) { }
    public record ScheduleInput(@NotNull UUID planId, @NotNull LocalDate effectiveDate) { }
    /** timing: IMMEDIATE (prorated now) or PERIOD_END (first day of next billing month). */
    public record ChangeInput(@NotNull UUID planId, String timing) { }
    /** Customer self-service: pick a plan from the catalogue. */
    public record SelfInput(@NotNull UUID planId, @Min(1) Integer seatCount) { }

    @GetMapping
    public List<Subscription> all(Authentication auth) {
        return isAdmin(auth) ? repo.findAll() : repo.findByCustomerIdOrderByPeriodStartDesc(auth.getName());
    }

    @GetMapping("/page")
    public Page<Subscription> page(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                   Authentication auth) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be >= 0 and size must be 1..100");
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "periodStart"));
        return isAdmin(auth) ? repo.findAll(pageable) : repo.findByCustomerIdOrderByPeriodStartDesc(auth.getName(), pageable);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @Transactional
    public Subscription create(@Valid @RequestBody Input i) {
        if (!i.periodEnd().isAfter(i.periodStart())) throw new IllegalArgumentException("Period end must be after start");
        var plan = plans.findById(i.planId()).orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        if (hasLiveSubscription(i.customerId()))
            throw new IllegalStateException("Customer already has an active subscription; change its plan instead");
        var sub = new Subscription(i.customerId(), i.planId(), i.periodStart(), i.periodEnd());
        if (i.seatCount() != null) sub.setSeatCount(i.seatCount());
        if (i.trialEnd() != null) sub.startTrial(i.trialEnd(), i.trialAutoConvert() == null || i.trialAutoConvert());
        return start(sub, plan);
    }

    /**
     * Self-service subscription for the signed-in customer (customer portal). Starts today for one
     * year; a customer can hold one live (not cancelled) subscription and changes its plan afterwards.
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/self")
    @Transactional
    public Subscription subscribeSelf(@Valid @RequestBody SelfInput in, Authentication auth) {
        var plan = plans.findById(in.planId()).orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        if (!plan.isActive()) throw new IllegalArgumentException("This plan is not available");
        if (hasLiveSubscription(auth.getName())) throw new IllegalStateException("You already have a subscription; change its plan instead");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var sub = new Subscription(auth.getName(), plan.getId(), today, today.plusYears(1));
        if (in.seatCount() != null) sub.setSeatCount(in.seatCount());
        return start(sub, plan);
    }

    /**
     * Admin analytics: every live subscription with its plan, how long the customer has been on that
     * plan (since the last change to it, or since the subscription started) and as a customer at all.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/analytics")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> analytics() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Subscription> all = repo.findAll();
        Map<String, LocalDate> firstStart = new LinkedHashMap<>();
        for (var s : all) firstStart.merge(s.getCustomerId(), s.getPeriodStart(), (a, b) -> a.isBefore(b) ? a : b);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var s : all) {
            if (SubscriptionState.CANCELLED.name().equals(s.getStatus()) || SubscriptionState.EXPIRED.name().equals(s.getStatus())) continue;
            LocalDate since = s.getPeriodStart();
            for (var c : changes.findBySubscriptionIdOrderByEffectiveDateDesc(s.getId())) {
                if (c.getToPlanId().equals(s.getPlanId())) { since = c.getEffectiveDate(); break; }
            }
            var plan = plans.findById(s.getPlanId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subscriptionId", s.getId());
            row.put("customerId", s.getCustomerId());
            row.put("planId", s.getPlanId());
            row.put("planKey", plan == null ? null : plan.getPlanKey());
            row.put("planName", plan == null ? null : plan.getName() + " v" + plan.getVersion());
            row.put("billingMode", plan == null ? null : plan.getBillingMode());
            row.put("status", s.getStatus());
            row.put("onPlanSince", since);
            row.put("daysOnPlan", Math.max(0, ChronoUnit.DAYS.between(since, today)));
            row.put("customerSince", firstStart.get(s.getCustomerId()));
            row.put("daysAsCustomer", Math.max(0, ChronoUnit.DAYS.between(firstStart.get(s.getCustomerId()), today)));
            rows.add(row);
        }
        return rows;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/schedule-plan-change")
    @Transactional
    public Subscription schedule(@PathVariable UUID id, @Valid @RequestBody ScheduleInput input) {
        var sub = repo.findById(id).orElseThrow();
        var current = plans.findById(sub.getPlanId()).orElseThrow();
        var next = plans.findById(input.planId()).orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        if (!current.getCurrency().equals(next.getCurrency())) throw new IllegalArgumentException("Plan change currencies must match");
        sub.schedulePlanChange(input.planId(), input.effectiveDate());
        return repo.save(sub);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/seats")
    @Transactional
    public Subscription seats(@PathVariable UUID id, @RequestParam @Min(1) int count) {
        var sub = repo.findById(id).orElseThrow();
        sub.setSeatCount(count);
        return repo.save(sub);
    }

    /** Admin for any subscription; a customer for its own (customer portal "change plan"). */
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @PostMapping("/{id}/change-plan")
    public Map<String, Object> changePlan(@PathVariable UUID id, @Valid @RequestBody ChangeInput input, Authentication auth) {
        guard(repo.findById(id).orElseThrow(), auth);
        Map<String, Object> result = new LinkedHashMap<>();
        if ("PERIOD_END".equalsIgnoreCase(input.timing())) {
            var sub = planChanges.changeAtPeriodEnd(id, input.planId());
            result.put("subscription", sub);
            result.put("timing", "PERIOD_END");
            result.put("effectiveDate", sub.getScheduledChangeDate());
            return result;
        }
        var audit = planChanges.changeNow(id, input.planId());
        result.put("subscription", repo.findById(id).orElseThrow());
        result.put("timing", "IMMEDIATE");
        result.put("change", audit);
        result.put("prorationDelta", audit.getProratedDelta());
        result.put("currency", audit.getCurrency());
        return result;
    }

    @GetMapping("/{id}/plan-changes")
    public List<PlanChange> history(@PathVariable UUID id, Authentication auth) {
        var sub = repo.findById(id).orElseThrow();
        guard(sub, auth);
        return changes.findBySubscriptionIdOrderByEffectiveDateDesc(id);
    }

    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/{id}/pause") @Transactional
    public Subscription pause(@PathVariable UUID id) { var s = repo.findById(id).orElseThrow(); s.pause(); return repo.save(s); }

    @PreAuthorize("hasRole('ADMIN')") @PostMapping("/{id}/resume") @Transactional
    public Subscription resume(@PathVariable UUID id) { var s = repo.findById(id).orElseThrow(); s.resume(); return repo.save(s); }

    /** Admin for any subscription; a customer may cancel its own. */
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')") @PostMapping("/{id}/cancel") @Transactional
    public Subscription cancel(@PathVariable UUID id, Authentication auth) {
        var s = repo.findById(id).orElseThrow();
        guard(s, auth);
        s.cancel();
        return repo.save(s);
    }

    private boolean isAdmin(Authentication a) { return a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN")); }

    private void guard(Subscription s, Authentication a) {
        if (!isAdmin(a) && !a.getName().equals(s.getCustomerId()))
            throw new AccessDeniedException("Customers may only access their own subscription.");
    }
}
