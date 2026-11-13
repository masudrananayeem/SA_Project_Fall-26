package com.meterflow.subscriptions;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*; import org.springframework.security.access.prepost.PreAuthorize; import java.time.*; import java.util.*;
@RestController @RequestMapping("/api/subscriptions") public class SubscriptionController {
 private final SubscriptionRepository repo; private final com.meterflow.catalogue.PlanRepository plans; public SubscriptionController(SubscriptionRepository r,com.meterflow.catalogue.PlanRepository p){repo=r;plans=p;}
 public record Input(@NotBlank String customerId,@NotNull UUID planId,@NotNull LocalDate periodStart,@NotNull LocalDate periodEnd){}
 @GetMapping public List<Subscription> all(){return repo.findAll();}
 @PreAuthorize("hasRole('ADMIN')") @PostMapping public Subscription create(@Valid @RequestBody Input i){if(!i.periodEnd().isAfter(i.periodStart()))throw new IllegalArgumentException("Period end must be after start");plans.findById(i.planId()).orElseThrow(()->new IllegalArgumentException("Plan not found"));return repo.save(new Subscription(i.customerId(),i.planId(),i.periodStart(),i.periodEnd()));}
 @PreAuthorize("hasRole('ADMIN')") @PostMapping("/{id}/pause") public Subscription pause(@PathVariable UUID id){var s=repo.findById(id).orElseThrow();s.pause();return repo.save(s);}
 @PreAuthorize("hasRole('ADMIN')") @PostMapping("/{id}/resume") public Subscription resume(@PathVariable UUID id){var s=repo.findById(id).orElseThrow();s.resume();return repo.save(s);}
 @PreAuthorize("hasRole('ADMIN')") @PostMapping("/{id}/cancel") public Subscription cancel(@PathVariable UUID id){var s=repo.findById(id).orElseThrow();s.cancel();return repo.save(s);}
}
