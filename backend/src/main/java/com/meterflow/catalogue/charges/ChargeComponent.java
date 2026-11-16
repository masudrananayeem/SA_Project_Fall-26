package com.meterflow.catalogue.charges;

import com.meterflow.catalogue.PricedCharge;

import java.math.BigDecimal;
import java.util.List;

/** Composite pattern component: a single configured charge or a group of charges. */
public interface ChargeComponent {
    List<PricedCharge> price(BigDecimal quantity, BigDecimal seats);

    default BigDecimal total(BigDecimal quantity, BigDecimal seats) {
        return price(quantity, seats).stream().map(PricedCharge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
