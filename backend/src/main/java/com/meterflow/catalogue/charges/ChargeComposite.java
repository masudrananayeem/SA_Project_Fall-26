package com.meterflow.catalogue.charges;

import com.meterflow.catalogue.PricedCharge;

import java.math.BigDecimal;
import java.util.List;

/** Composite node: a plan is a group of charges whose lines are concatenated. */
public final class ChargeComposite implements ChargeComponent {
    private final List<ChargeComponent> children;

    public ChargeComposite(List<ChargeComponent> children) { this.children = List.copyOf(children); }

    @Override
    public List<PricedCharge> price(BigDecimal quantity, BigDecimal seats) {
        return children.stream().flatMap(c -> c.price(quantity, seats).stream()).toList();
    }
}
