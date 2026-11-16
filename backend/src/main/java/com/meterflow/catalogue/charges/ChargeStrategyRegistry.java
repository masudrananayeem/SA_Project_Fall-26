package com.meterflow.catalogue.charges;

import com.meterflow.catalogue.PricedCharge;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Open/closed registry of charge strategies. Built-in strategies are always present; extra
 * strategies (Spring beans implementing {@link ChargeStrategy}) are added without touching
 * any existing class.
 */
public final class ChargeStrategyRegistry {
    private final Map<String, ChargeStrategy> strategies;

    public ChargeStrategyRegistry(Collection<? extends ChargeStrategy> strategies) {
        Map<String, ChargeStrategy> map = new LinkedHashMap<>();
        for (ChargeStrategy strategy : strategies) {
            if (strategy.type() == null || strategy.type().isBlank())
                throw new IllegalArgumentException("Charge strategy type is required");
            if (map.putIfAbsent(strategy.type(), strategy) != null)
                throw new IllegalArgumentException("Duplicate charge strategy type: " + strategy.type());
        }
        this.strategies = Map.copyOf(map);
    }

    public static ChargeStrategyRegistry builtIn() { return new ChargeStrategyRegistry(BuiltInCharges.all()); }

    /** Returns a new registry with additional strategies, for example plug-in beans. */
    public ChargeStrategyRegistry with(Collection<? extends ChargeStrategy> extra) {
        List<ChargeStrategy> all = new ArrayList<>(strategies.values());
        all.addAll(extra);
        return new ChargeStrategyRegistry(all);
    }

    public Set<String> types() { return strategies.keySet(); }

    public ChargeStrategy require(String type) {
        ChargeStrategy strategy = type == null ? null : strategies.get(type);
        if (strategy == null) throw new IllegalArgumentException("Unsupported charge type: " + type);
        return strategy;
    }

    /** Validates a plan's charge configuration at plan creation time. */
    public void validate(List<ChargeDefinition> definitions) {
        if (definitions == null || definitions.isEmpty())
            throw new IllegalArgumentException("At least one charge definition is required");
        for (ChargeDefinition d : definitions) {
            if (d == null || d.type() == null || d.name() == null || d.name().isBlank() || d.amount() == null || d.amount().signum() < 0)
                throw new IllegalArgumentException("Charge definition requires a type, a name and a non-negative amount");
            if (d.includedUnits().signum() < 0) throw new IllegalArgumentException("includedUnits cannot be negative");
            require(d.type()).validate(d);
        }
    }

    /** Composite of all configured charges (Composite pattern). */
    public ChargeComponent compose(List<ChargeDefinition> definitions) {
        validate(definitions);
        return new ChargeComposite(definitions.stream()
                .map(d -> (ChargeComponent) new ConfiguredCharge(d, require(d.type())))
                .toList());
    }

    public List<PricedCharge> price(List<ChargeDefinition> definitions, BigDecimal quantity, BigDecimal seats) {
        if (quantity == null || quantity.signum() < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        if (seats == null || seats.signum() < 0) throw new IllegalArgumentException("Seats cannot be negative");
        return compose(definitions).price(quantity, seats);
    }
}
