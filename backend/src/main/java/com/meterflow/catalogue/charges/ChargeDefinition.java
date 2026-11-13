package com.meterflow.catalogue.charges;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** Immutable, serialisable pricing configuration. Amounts use the subscription currency. */
public record ChargeDefinition(
        @NotBlank String type,
        @NotBlank String name,
        @NotNull BigDecimal amount,
        BigDecimal includedUnits,
        BigDecimal packageSize,
        List<Tier> tiers) {
    public ChargeDefinition {
        tiers = tiers == null ? List.of() : List.copyOf(tiers);
        includedUnits = includedUnits == null ? BigDecimal.ZERO : includedUnits;
        packageSize = packageSize == null ? BigDecimal.ONE : packageSize;
    }
    public record Tier(BigDecimal upTo, BigDecimal unitPrice) {}
}
