package com.meterflow.catalogue.charges;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * The six charge types required by FR3. Each one is an independent {@link ChargeStrategy}
 * class; the registry is the only place that knows the full list.
 */
public final class BuiltInCharges {
    private BuiltInCharges() { }

    static final BigDecimal UNBOUNDED = new BigDecimal("1E100");

    public static List<ChargeStrategy> all() {
        return List.of(new FlatFee(), new PerUnit(), new PerSeat(), new Package(), new VolumeTier(), new GraduatedTier());
    }

    static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    static List<ChargeDefinition.Tier> sortedTiers(ChargeDefinition definition) {
        return definition.tiers().stream()
                .sorted(Comparator.comparing(t -> t.upTo() == null ? UNBOUNDED : t.upTo()))
                .toList();
    }

    /** Shared validation for volume and graduated tiers: increasing bounds and a final open tier. */
    static void validateTiers(ChargeDefinition definition) {
        if (definition.tiers().isEmpty()) throw new IllegalArgumentException("Tiered charge requires at least one tier");
        BigDecimal previous = BigDecimal.ZERO;
        boolean unbounded = false;
        for (var tier : sortedTiers(definition)) {
            if (tier.unitPrice() == null || tier.unitPrice().signum() < 0)
                throw new IllegalArgumentException("Tier unit price must be non-negative");
            if (tier.upTo() == null) {
                if (unbounded) throw new IllegalArgumentException("Only one unbounded tier is allowed");
                unbounded = true;
                continue;
            }
            if (tier.upTo().signum() <= 0 || tier.upTo().compareTo(previous) <= 0)
                throw new IllegalArgumentException("Tier upper bounds must be positive and strictly increasing");
            previous = tier.upTo();
        }
        if (!unbounded) throw new IllegalArgumentException("Tiered pricing must include a final unbounded tier (upTo: null)");
    }

    /** Fixed amount per billing period, independent of usage. */
    public static final class FlatFee implements ChargeStrategy {
        public String type() { return "flat_fee"; }
        public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) { return d.amount(); }
        public BigDecimal billedQuantity(ChargeDefinition d, BigDecimal q, BigDecimal s) { return BigDecimal.ONE; }
        public String explain(ChargeDefinition d, BigDecimal q, BigDecimal s, BigDecimal amount) {
            return "Flat fee '" + d.name() + "': " + plain(d.amount()) + " per period";
        }
    }

    /** amount per unit above the free allowance (includedUnits). */
    public static final class PerUnit implements ChargeStrategy {
        public String type() { return "per_unit"; }
        public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) {
            return q.subtract(d.includedUnits()).max(BigDecimal.ZERO).multiply(d.amount());
        }
        public String explain(ChargeDefinition d, BigDecimal q, BigDecimal s, BigDecimal amount) {
            BigDecimal billable = q.subtract(d.includedUnits()).max(BigDecimal.ZERO);
            return "Per unit '" + d.name() + "': (" + plain(q) + " used - " + plain(d.includedUnits())
                    + " free) = " + plain(billable) + " x " + plain(d.amount());
        }
    }

    /** amount per seat on the subscription. */
    public static final class PerSeat implements ChargeStrategy {
        public String type() { return "per_seat"; }
        public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) { return s.multiply(d.amount()); }
        public BigDecimal billedQuantity(ChargeDefinition d, BigDecimal q, BigDecimal s) { return s; }
        public String explain(ChargeDefinition d, BigDecimal q, BigDecimal s, BigDecimal amount) {
            return "Per seat '" + d.name() + "': " + plain(s) + " seats x " + plain(d.amount());
        }
    }

    /** amount per started package of packageSize units (for example per 1,000 calls), after free units. */
    public static final class Package implements ChargeStrategy {
        public String type() { return "package"; }
        public void validate(ChargeDefinition d) {
            if (d.packageSize().signum() <= 0) throw new IllegalArgumentException("packageSize must be positive");
        }
        public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) {
            validate(d);
            BigDecimal billable = q.subtract(d.includedUnits()).max(BigDecimal.ZERO);
            return billable.divide(d.packageSize(), 0, RoundingMode.CEILING).multiply(d.amount());
        }
        public String explain(ChargeDefinition d, BigDecimal q, BigDecimal s, BigDecimal amount) {
            BigDecimal billable = q.subtract(d.includedUnits()).max(BigDecimal.ZERO);
            BigDecimal packs = billable.divide(d.packageSize(), 0, RoundingMode.CEILING);
            return "Package '" + d.name() + "': ceil(" + plain(billable) + " / " + plain(d.packageSize()) + ") = "
                    + plain(packs) + " packages x " + plain(d.amount());
        }
    }

    /** The tier that contains the total quantity prices every unit. */
    public static final class VolumeTier implements ChargeStrategy {
        public String type() { return "volume_tier"; }
        public void validate(ChargeDefinition d) { validateTiers(d); }
        private ChargeDefinition.Tier tierFor(ChargeDefinition d, BigDecimal q) {
            return sortedTiers(d).stream()
                    .filter(t -> t.upTo() == null || q.compareTo(t.upTo()) <= 0)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No matching volume tier"));
        }
        public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) {
            validateTiers(d);
            return q.multiply(tierFor(d, q).unitPrice());
        }
        public String explain(ChargeDefinition d, BigDecimal q, BigDecimal s, BigDecimal amount) {
            var tier = tierFor(d, q);
            return "Volume tier '" + d.name() + "': all " + plain(q) + " units at "
                    + plain(tier.unitPrice()) + " (tier up to " + (tier.upTo() == null ? "unlimited" : plain(tier.upTo())) + ")";
        }
    }

    /** Each band of units is priced at its own tier rate. */
    public static final class GraduatedTier implements ChargeStrategy {
        public String type() { return "graduated_tier"; }
        public void validate(ChargeDefinition d) { validateTiers(d); }
        public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) {
            validateTiers(d);
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal previous = BigDecimal.ZERO;
            for (var tier : sortedTiers(d)) {
                BigDecimal cap = tier.upTo() == null ? q : tier.upTo();
                BigDecimal units = q.min(cap).subtract(previous).max(BigDecimal.ZERO);
                total = total.add(units.multiply(tier.unitPrice()));
                previous = cap;
                if (q.compareTo(cap) <= 0) break;
            }
            return total;
        }
        public String explain(ChargeDefinition d, BigDecimal q, BigDecimal s, BigDecimal amount) {
            StringBuilder text = new StringBuilder("Graduated tiers '" + d.name() + "':");
            BigDecimal previous = BigDecimal.ZERO;
            for (var tier : sortedTiers(d)) {
                BigDecimal cap = tier.upTo() == null ? q : tier.upTo();
                BigDecimal units = q.min(cap).subtract(previous).max(BigDecimal.ZERO);
                if (units.signum() > 0) text.append(' ').append(plain(units)).append(" x ").append(plain(tier.unitPrice())).append(';');
                previous = cap;
                if (q.compareTo(cap) <= 0) break;
            }
            return text.toString();
        }
    }
}
