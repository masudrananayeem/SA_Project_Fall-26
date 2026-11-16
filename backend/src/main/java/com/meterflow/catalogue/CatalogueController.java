package com.meterflow.catalogue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.charges.ChargeDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Plan catalogue. Plans are immutable: "editing" a plan publishes a new version under the same
 * plan key (FR10). Existing subscriptions keep the plan id (version) they were created with until
 * they are migrated (see subscriptions.PlanMigrationController).
 */
@RestController
@RequestMapping("/api/catalogue/plans")
public class CatalogueController {
    private final PlanRepository repo;
    private final ObjectMapper mapper;
    private final PricingService pricing;

    public CatalogueController(PlanRepository repo, ObjectMapper mapper, PricingService pricing) {
        this.repo = repo;
        this.mapper = mapper;
        this.pricing = pricing;
    }

    public record PlanInput(@NotBlank String planKey, @NotBlank String name, @Pattern(regexp = "BDT|USD") String currency,
                            @NotNull @DecimalMin("0") BigDecimal flatFee, @NotNull @DecimalMin("0") BigDecimal unitPrice,
                            @NotBlank String metric, @NotNull @DecimalMin("0") BigDecimal freeAllowance,
                            @NotNull @DecimalMin("0") BigDecimal minimumCommitment, List<@Valid ChargeDefinition> charges,
                            BigDecimal taxRate, String aggregation, String aggregationProperty, Map<String, String> metricFilters) { }

    @GetMapping
    public List<Plan> all() { return repo.findAllByOrderByPlanKeyAscVersionDesc(); }

    @GetMapping("/{planKey}/versions")
    public List<Plan> versions(@PathVariable String planKey) { return repo.findByPlanKeyOrderByVersionDesc(planKey); }

    @GetMapping("/charge-types")
    public Map<String, Object> chargeTypes() { return Map.of("types", pricing.supportedChargeTypes()); }

    /** Publishes a plan; if the key exists this becomes the next version. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @Transactional
    public Plan create(@Valid @RequestBody PlanInput in) { return publish(in.planKey(), in); }

    /** "Edit" a plan: always creates a new immutable version under the path's plan key. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{planKey}")
    @Transactional
    public Plan edit(@PathVariable String planKey, @Valid @RequestBody PlanInput in) {
        if (repo.findTopByPlanKeyOrderByVersionDesc(planKey).isEmpty())
            throw new IllegalArgumentException("Unknown plan key: " + planKey);
        return publish(planKey, in);
    }

    private Plan publish(String planKey, PlanInput in) {
        String currency = in.currency() == null ? "BDT" : in.currency();
        var latest = repo.findTopByPlanKeyOrderByVersionDesc(planKey);
        if (latest.isPresent() && !latest.get().getCurrency().equals(currency))
            throw new IllegalArgumentException("A plan key keeps one currency across versions");
        int version = latest.map(p -> p.getVersion() + 1).orElse(1);
        String chargeJson = null;
        if (in.charges() != null && !in.charges().isEmpty()) {
            pricing.validate(in.charges());
            try {
                chargeJson = mapper.writeValueAsString(in.charges());
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid charge configuration");
            }
        }
        var plan = new Plan(planKey, version, in.name(), currency, in.flatFee(), in.unitPrice(), in.metric(),
                in.freeAllowance(), in.minimumCommitment(), chargeJson);
        if (in.taxRate() != null) plan.setTaxRate(in.taxRate());
        try {
            plan.defineMetric(in.aggregation(), in.aggregationProperty(),
                    in.metricFilters() == null || in.metricFilters().isEmpty() ? null : mapper.writeValueAsString(in.metricFilters()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid metric filters");
        }
        return repo.save(plan);
    }
}
