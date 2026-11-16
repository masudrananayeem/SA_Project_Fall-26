package com.meterflow.budgets;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/** Customers set their own monthly budget and see how much of it is used. */
@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetService service;

    public BudgetController(BudgetService service) { this.service = service; }

    public record Input(@NotBlank String customerId, @NotBlank String period, @NotNull @DecimalMin("0.01") BigDecimal amount,
                        @NotBlank String currency) { }

    @PostMapping
    public UsageBudget set(@Valid @RequestBody Input i, Authentication a) {
        guard(i.customerId(), a);
        return service.set(i.customerId(), i.period(), i.amount(), i.currency());
    }

    @GetMapping
    public Map<String, Object> get(@RequestParam String customerId, @RequestParam String period, Authentication a) {
        guard(customerId, a);
        return service.find(customerId, period).<Map<String, Object>>map(b -> Map.of(
                "customerId", b.getCustomerId(), "period", b.getPeriod(), "amount", b.getAmount(),
                "currency", b.getCurrency(), "notifiedThresholds", b.getNotified())).orElse(Map.of("status", "NO_BUDGET"));
    }

    @GetMapping("/evaluate")
    public Map<String, Object> evaluate(@RequestParam String customerId, @RequestParam String period, Authentication a) {
        guard(customerId, a);
        return service.evaluate(customerId, period);
    }

    private void guard(String customer, Authentication a) {
        if (a.getAuthorities().stream().noneMatch(x -> x.getAuthority().equals("ROLE_ADMIN")) && !a.getName().equals(customer))
            throw new AccessDeniedException("Customer access denied");
    }
}
