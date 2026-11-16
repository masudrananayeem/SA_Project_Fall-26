package com.meterflow.catalogue.charges;

import com.meterflow.catalogue.PricedCharge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static convenience facade over the built-in {@link ChargeStrategyRegistry}. Production code
 * uses the Spring-managed registry (built-ins plus plug-in beans) through PricingService.
 */
public final class ChargeStrategies {
    private ChargeStrategies() { }

    public static Map<String, ChargeStrategy> defaults() {
        Map<String, ChargeStrategy> map = new LinkedHashMap<>();
        BuiltInCharges.all().forEach(s -> map.put(s.type(), s));
        return Map.copyOf(map);
    }

    public static BigDecimal calculate(List<ChargeDefinition> definitions, BigDecimal quantity, BigDecimal seats) {
        return ChargeStrategyRegistry.builtIn().price(definitions, quantity, seats).stream()
                .map(PricedCharge::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
