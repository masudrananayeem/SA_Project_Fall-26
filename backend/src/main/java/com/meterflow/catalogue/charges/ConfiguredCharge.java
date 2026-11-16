package com.meterflow.catalogue.charges;

import com.meterflow.catalogue.PricedCharge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Composite leaf: one charge definition priced by its strategy. */
public final class ConfiguredCharge implements ChargeComponent {
    private final ChargeDefinition definition;
    private final ChargeStrategy strategy;

    public ConfiguredCharge(ChargeDefinition definition, ChargeStrategy strategy) {
        this.definition = definition;
        this.strategy = strategy;
    }

    @Override
    public List<PricedCharge> price(BigDecimal quantity, BigDecimal seats) {
        BigDecimal amount = strategy.calculate(definition, quantity, seats);
        BigDecimal billed = strategy.billedQuantity(definition, quantity, seats);
        BigDecimal unitPrice = billed.signum() == 0 ? BigDecimal.ZERO : amount.divide(billed, 8, RoundingMode.HALF_UP);
        return List.of(new PricedCharge(definition.name(), definition.type(), billed, unitPrice, amount,
                strategy.explain(definition, quantity, seats, amount)));
    }
}
