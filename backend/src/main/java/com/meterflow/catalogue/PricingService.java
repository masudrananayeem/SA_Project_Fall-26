package com.meterflow.catalogue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.charges.ChargeDefinition;
import com.meterflow.catalogue.charges.ChargeStrategyRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Prices a plan version. A plan either carries a list of configured charges (FR3: flat, per unit,
 * per seat, package, volume and graduated tiers, plus plug-in types) or uses the legacy
 * flat-fee + per-unit-over-allowance fields. The plan-level minimum commitment is applied as
 * an explicit top-up line so every amount on the invoice is explainable.
 */
@Service
public class PricingService {
    private static final TypeReference<List<ChargeDefinition>> DEFINITIONS = new TypeReference<>() { };

    private final ObjectMapper mapper;
    private final ChargeStrategyRegistry registry;

    public PricingService() { this(new ObjectMapper(), ChargeStrategyRegistry.builtIn()); }

    public PricingService(ObjectMapper mapper) { this(mapper, ChargeStrategyRegistry.builtIn()); }

    @Autowired
    public PricingService(ObjectMapper mapper, ChargeStrategyRegistry registry) {
        this.mapper = mapper;
        this.registry = registry;
    }

    public Set<String> supportedChargeTypes() { return registry.types(); }

    public void validate(List<ChargeDefinition> definitions) { registry.validate(definitions); }

    public List<ChargeDefinition> definitions(Plan plan) {
        String json = plan.getChargeDefinitionsJson();
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, DEFINITIONS);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid charge definitions for plan " + plan.getPlanKey(), e);
        }
    }

    /** Full-period price for a quantity of the plan's billable metric and a seat count. */
    public PriceQuote quote(Plan plan, BigDecimal quantity, BigDecimal seats) {
        if (quantity == null || quantity.signum() < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        if (seats == null || seats.signum() < 0) throw new IllegalArgumentException("Seats cannot be negative");
        List<PricedCharge> lines = new ArrayList<>();
        List<ChargeDefinition> definitions = definitions(plan);
        if (!definitions.isEmpty()) {
            lines.addAll(registry.price(definitions, quantity, seats));
        } else {
            if (plan.getFlatFee().signum() > 0)
                lines.add(new PricedCharge("Platform fee", "flat_fee", BigDecimal.ONE, plan.getFlatFee(), plan.getFlatFee(),
                        "Flat fee: " + plan.getFlatFee().toPlainString() + " per period"));
            BigDecimal billable = quantity.subtract(plan.getFreeAllowance()).max(BigDecimal.ZERO);
            lines.add(new PricedCharge("Usage: " + plan.getMetric(), "per_unit", quantity, plan.getUnitPrice(),
                    billable.multiply(plan.getUnitPrice()),
                    "Per unit: (" + quantity.stripTrailingZeros().toPlainString() + " used - "
                            + plan.getFreeAllowance().stripTrailingZeros().toPlainString() + " free) x "
                            + plan.getUnitPrice().stripTrailingZeros().toPlainString()));
        }
        BigDecimal charged = lines.stream().map(PricedCharge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal minimum = plan.getMinimumCommitment() == null ? BigDecimal.ZERO : plan.getMinimumCommitment().setScale(2, RoundingMode.HALF_UP);
        if (charged.compareTo(minimum) < 0) {
            BigDecimal topUp = minimum.subtract(charged);
            lines.add(new PricedCharge("Minimum commitment top-up", "minimum_commitment", BigDecimal.ONE, topUp, topUp,
                    "Minimum commitment " + minimum.toPlainString() + " - charges " + charged.toPlainString()));
        }
        return new PriceQuote(plan.getPlanKey(), plan.getVersion(), plan.getCurrency(), lines);
    }

    /**
     * Price for one calendar billing month, applying the trial rule: a month fully inside the
     * trial is free, and the month in which the trial ends is charged for the paid days only.
     * Shared by the live estimate and the billing run.
     */
    public PriceQuote quoteForPeriod(Plan plan, BigDecimal quantity, BigDecimal seats, LocalDate trialEnd, YearMonth period) {
        PriceQuote full = quote(plan, quantity, seats);
        if (trialEnd == null) return full;
        LocalDate start = period.atDay(1);
        LocalDate next = period.plusMonths(1).atDay(1);
        if (!trialEnd.isAfter(start)) return full;
        long paidDays = trialEnd.isBefore(next) ? ChronoUnit.DAYS.between(trialEnd, next) : 0;
        return full.prorated(paidDays, period.lengthOfMonth(),
                "trial until " + trialEnd + ": " + paidDays + "/" + period.lengthOfMonth() + " paid days");
    }

    /** True when the whole billing month is covered by the trial (no invoice is produced). */
    public static boolean fullyInTrial(LocalDate trialEnd, YearMonth period) {
        return trialEnd != null && !trialEnd.isBefore(period.plusMonths(1).atDay(1));
    }

    /** Recurring (zero-usage) amount, used for mid-cycle plan-change proration. */
    public BigDecimal recurringAmount(Plan plan, BigDecimal seats) { return quote(plan, BigDecimal.ZERO, seats).subtotal(); }

    public BigDecimal calculate(Plan plan, BigDecimal quantity) { return calculate(plan, quantity, BigDecimal.ONE); }

    public BigDecimal calculate(Plan plan, BigDecimal quantity, BigDecimal seats) {
        return quote(plan, quantity, seats).subtotal().setScale(2, RoundingMode.HALF_UP);
    }
}
