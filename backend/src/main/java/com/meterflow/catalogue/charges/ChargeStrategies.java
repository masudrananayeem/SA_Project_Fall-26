package com.meterflow.catalogue.charges;

import java.math.*;
import java.util.*;

/** Open/closed pricing registry: add a strategy and register it; existing strategies stay untouched. */
public final class ChargeStrategies {
    private ChargeStrategies() {}
    private static BigDecimal money(BigDecimal n) { return n.setScale(2, RoundingMode.HALF_UP); }
    public static Map<String, ChargeStrategy> defaults() {
        List<ChargeStrategy> strategies = List.of(
            new Basic("flat_fee") { public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) { return d.amount(); } },
            new Basic("per_unit") { public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) { return q.subtract(d.includedUnits()).max(BigDecimal.ZERO).multiply(d.amount()); } },
            new Basic("per_seat") { public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) { return s.multiply(d.amount()); } },
            new Basic("package") { public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) { if (d.packageSize().signum() <= 0) throw new IllegalArgumentException("packageSize must be positive"); BigDecimal packs=q.divide(d.packageSize(),0,RoundingMode.CEILING); return packs.multiply(d.amount()); } },
            new Basic("volume_tier") { public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) { BigDecimal rate=d.tiers().stream().filter(t->t.upTo()==null||q.compareTo(t.upTo())<=0).min(Comparator.comparing(t->t.upTo()==null?new BigDecimal("1E100"):t.upTo())).orElseThrow(()->new IllegalArgumentException("No matching volume tier")).unitPrice(); return q.multiply(rate); } },
            new Basic("graduated_tier") { public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) { BigDecimal total=BigDecimal.ZERO, previous=BigDecimal.ZERO; for(var t:d.tiers().stream().sorted(Comparator.comparing(x->x.upTo()==null?new BigDecimal("1E100"):x.upTo())).toList()){BigDecimal cap=t.upTo()==null?q.min(q):t.upTo();BigDecimal units=q.min(cap).subtract(previous).max(BigDecimal.ZERO);total=total.add(units.multiply(t.unitPrice()));previous=cap;if(q.compareTo(cap)<=0)break;} if(previous.compareTo(q)<0 && d.tiers().stream().noneMatch(t->t.upTo()==null)) throw new IllegalArgumentException("Graduated tiers do not cover quantity"); return total; } }
        );
        Map<String,ChargeStrategy> map=new HashMap<>(); strategies.forEach(s->map.put(s.type(),s)); return Map.copyOf(map);
    }
    private static void validateTiers(ChargeDefinition definition) {
        if (!definition.type().equals("volume_tier") && !definition.type().equals("graduated_tier")) return;
        if (definition.tiers().isEmpty()) throw new IllegalArgumentException("Tiered charge requires at least one tier");
        BigDecimal previous = BigDecimal.ZERO;
        boolean unbounded = false;
        for (var tier : definition.tiers().stream().sorted(Comparator.comparing(t -> t.upTo() == null ? new BigDecimal("1E100") : t.upTo())).toList()) {
            if (tier.unitPrice() == null || tier.unitPrice().signum() < 0) throw new IllegalArgumentException("Tier unit price must be non-negative");
            if (tier.upTo() == null) { unbounded = true; continue; }
            if (tier.upTo().signum() <= 0 || tier.upTo().compareTo(previous) <= 0) throw new IllegalArgumentException("Tier upper bounds must be positive and strictly increasing");
            previous = tier.upTo();
        }
        if (!unbounded) throw new IllegalArgumentException("Tiered pricing must include a final unbounded tier (upTo: null)");
    }

    private abstract static class Basic implements ChargeStrategy {
        private final String type; Basic(String type){this.type=type;} public String type(){return type;}
        public abstract BigDecimal calculate(ChargeDefinition d,BigDecimal q,BigDecimal s);
    }
    public static BigDecimal calculate(List<ChargeDefinition> definitions, BigDecimal quantity, BigDecimal seats) {
        if (definitions == null || definitions.isEmpty()) throw new IllegalArgumentException("At least one charge definition is required");
        if (quantity == null || quantity.signum() < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        if (seats == null || seats.signum() < 0) throw new IllegalArgumentException("Seats cannot be negative");
        var registry = defaults();
        BigDecimal total = BigDecimal.ZERO;
        for (var d : definitions) {
            if (d == null || d.type() == null || d.name() == null || d.name().isBlank() || d.amount() == null || d.amount().signum() < 0)
                throw new IllegalArgumentException("Charge definition requires a name and non-negative amount");
            if (d.includedUnits().signum() < 0) throw new IllegalArgumentException("includedUnits cannot be negative");
            var strategy = registry.get(d.type());
            if (strategy == null) throw new IllegalArgumentException("Unsupported charge type: " + d.type());
            validateTiers(d);
            total = total.add(strategy.calculate(d, quantity, seats));
        }
        return money(total);
    }
}
