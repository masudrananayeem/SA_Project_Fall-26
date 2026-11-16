package com.meterflow.catalogue.charges;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.Plan;
import com.meterflow.catalogue.PricingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QA4: a charge type the catalogue has never seen is added by writing one new class. No existing
 * pricing class is edited; the registry accepts it and plans can use it through configuration.
 */
class ChargeRegistryExtensionTest {

    /** Example instructor-defined type: a percentage surcharge on usage above a threshold. */
    static final class OverageSurcharge implements ChargeStrategy {
        public String type() { return "overage_surcharge"; }
        public void validate(ChargeDefinition d) {
            if (d.includedUnits().signum() <= 0) throw new IllegalArgumentException("includedUnits is the overage threshold");
        }
        public BigDecimal calculate(ChargeDefinition d, BigDecimal q, BigDecimal s) {
            return q.subtract(d.includedUnits()).max(BigDecimal.ZERO).multiply(d.amount()).multiply(new BigDecimal("1.10"));
        }
    }

    @Test
    void registryAcceptsNewStrategyWithoutChangingBuiltIns() {
        var registry = ChargeStrategyRegistry.builtIn().with(List.of(new OverageSurcharge()));
        assertTrue(registry.types().containsAll(List.of("flat_fee", "per_unit", "per_seat", "package", "volume_tier", "graduated_tier", "overage_surcharge")));
        var defs = List.of(new ChargeDefinition("overage_surcharge", "Overage", new BigDecimal("2"), new BigDecimal("100"), null, null));
        assertEquals(new BigDecimal("22.00"), registry.price(defs, new BigDecimal("110"), BigDecimal.ONE).get(0).amount());
    }

    @Test
    void configuredPlanUsesTheNewTypeThroughPricingService() throws Exception {
        var mapper = new ObjectMapper();
        var defs = List.of(
                new ChargeDefinition("flat_fee", "Platform", new BigDecimal("50"), null, null, null),
                new ChargeDefinition("overage_surcharge", "Overage", new BigDecimal("1"), new BigDecimal("1000"), null, null));
        var plan = new Plan("ext", 1, "Extension plan", "USD", BigDecimal.ZERO, BigDecimal.ZERO, "api_calls", BigDecimal.ZERO,
                BigDecimal.ZERO, mapper.writeValueAsString(defs));
        var pricing = new PricingService(mapper, ChargeStrategyRegistry.builtIn().with(List.of(new OverageSurcharge())));
        var quote = pricing.quote(plan, new BigDecimal("1500"), BigDecimal.ONE);
        assertEquals(2, quote.lines().size());
        assertEquals(new BigDecimal("600.00"), quote.subtotal()); // 50 + 500 x 1.10
    }

    @Test
    void unknownTypeIsRejectedAndDuplicatesAreRefused() {
        var defs = List.of(new ChargeDefinition("overage_surcharge", "Overage", BigDecimal.ONE, BigDecimal.TEN, null, null));
        assertThrows(IllegalArgumentException.class, () -> ChargeStrategyRegistry.builtIn().validate(defs));
        assertThrows(IllegalArgumentException.class, () -> ChargeStrategyRegistry.builtIn().with(List.of(new BuiltInCharges.PerUnit())));
    }
}
