package com.meterflow.catalogue.charges;

import java.math.BigDecimal;

/**
 * Strategy for one charge type (flat fee, per unit, tiers, ...).
 *
 * <p>QA4: a new charge type is added by writing a new class that implements this interface
 * and registering it as a Spring bean (for example with {@code @Component}). The
 * {@link ChargeStrategyRegistry} picks it up; no existing pricing class is edited.</p>
 */
public interface ChargeStrategy {

    /** Configuration key used in a plan's charge definitions, for example {@code per_unit}. */
    String type();

    /** Amount for this charge before rounding. Must not depend on any other charge. */
    BigDecimal calculate(ChargeDefinition definition, BigDecimal quantity, BigDecimal seats);

    /** Rejects an invalid configuration when a plan is created. Default: accept. */
    default void validate(ChargeDefinition definition) { }

    /** Quantity shown on the invoice line (usage by default). */
    default BigDecimal billedQuantity(ChargeDefinition definition, BigDecimal quantity, BigDecimal seats) {
        return quantity;
    }

    /** Human-readable price rule shown on the invoice line. */
    default String explain(ChargeDefinition definition, BigDecimal quantity, BigDecimal seats, BigDecimal amount) {
        return type() + " charge '" + definition.name() + "' = " + amount.toPlainString();
    }
}
