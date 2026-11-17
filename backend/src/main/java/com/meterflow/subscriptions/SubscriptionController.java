package com.meterflow.subscriptions;

import com.meterflow.catalogue.PlanRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    public SubscriptionController(SubscriptionRepository repo, PlanRepository plans, PlanChangeRepository changes,
                                  PlanChangeService planChanges) {
        this.repo = repo;
        this.plans = plans;
        this.changes = changes;
        this.planChanges = planChanges;
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
        plans.findById(i.planId()).orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        if (repo.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(i.customerId(), SubscriptionState.ACTIVE.name()).isPresent())
            throw new IllegalStateException("Customer already has an active subscription; change its plan instead");
        var sub = new Subscription(i.customerId(), i.planId(), i.periodStart(), i.periodEnd());
        if (i.seatCount() != null) sub.setSeatCount(i.seatCount());
        if (i.trialEnd() != null) sub.startTrial(i.trialEnd(), i.trialAutoConvert() == null || i.trialAutoConvert());
        return repo.save(sub);
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
        boolean live = repo.findByCustomerIdOrderByPeriodStartDesc(auth.getName()).stream()
                .anyMatch(s -> !SubscriptionState.CANCELLED.name().equals(s.getStatus()));
        if (live) throw new IllegalStateException("You already have a subscription; change its plan instead");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var sub = new Subscription(auth.getName(), plan.getId(), today, today.plusYears(1));
        if (in.seatCount() != null) sub.setSeatCount(in.seatCount());
        return repo.save(sub);
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
