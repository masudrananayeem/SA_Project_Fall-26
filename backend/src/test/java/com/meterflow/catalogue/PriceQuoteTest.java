package com.meterflow.catalogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.charges.ChargeDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceQuoteTest {
    private final PricingService pricing = new PricingService();

    private Plan legacy() {
        return new Plan("starter", 1, "Starter", "BDT", new BigDecimal("299"), new BigDecimal("0.05"), "api_calls",
                new BigDecimal("1000"), new BigDecimal("350"));
    }

    @Test
    void everyAmountIsAnExplainableLineIncludingMinimumTopUp() {
        var quote = pricing.quote(legacy(), new BigDecimal("1200"), BigDecimal.ONE);
        // 299 + 200 x 0.05 = 309 < 350 minimum -> top-up 41
        assertEquals(3, quote.lines().size());
        assertEquals("minimum_commitment", quote.lines().get(2).type());
        assertEquals(new BigDecimal("41.00"), quote.lines().get(2).amount());
        assertEquals(new BigDecimal("350.00"), quote.subtotal());
        assertTrue(quote.lines().get(1).rule().contains("1200 used - 1000 free"));
    }

    @Test
    void trialMonthIsProratedAndFullTrialMonthIsFree() {
        var month = YearMonth.of(2026, 11); // 30 days
        var partial = pricing.quoteForPeriod(legacy(), BigDecimal.ZERO, BigDecimal.ONE, LocalDate.of(2026, 11, 21), month);
        assertEquals(new BigDecimal("116.67"), partial.subtotal()); // 350 x 10/30
        assertTrue(PricingService.fullyInTrial(LocalDate.of(2026, 12, 1), month));
        assertEquals(new BigDecimal("0.00"), pricing.quoteForPeriod(legacy(), BigDecimal.ZERO, BigDecimal.ONE, LocalDate.of(2026, 12, 1), month).subtotal());
        assertEquals(new BigDecimal("350.00"), pricing.quoteForPeriod(legacy(), BigDecimal.ZERO, BigDecimal.ONE, LocalDate.of(2026, 11, 1), month).subtotal());
    }

    @Test
    void configuredChargesProduceOneLinePerChargeWithRules() throws Exception {
        var mapper = new ObjectMapper();
        var tiers = List.of(new ChargeDefinition.Tier(new BigDecimal("1000"), BigDecimal.ZERO),
                new ChargeDefinition.Tier(new BigDecimal("10000"), new BigDecimal("0.02")),
                new ChargeDefinition.Tier(null, new BigDecimal("0.01")));
        var defs = List.of(new ChargeDefinition("flat_fee", "Platform", new BigDecimal("10"), null, null, null),
                new ChargeDefinition("graduated_tier", "API calls", BigDecimal.ZERO, null, null, tiers),
                new ChargeDefinition("per_seat", "Seats", new BigDecimal("5"), null, null, null));
        var plan = new Plan("pro", 2, "Pro", "USD", BigDecimal.ZERO, BigDecimal.ZERO, "api_calls", BigDecimal.ZERO, BigDecimal.ZERO,
                mapper.writeValueAsString(defs));
        var quote = new PricingService(mapper).quote(plan, new BigDecimal("12000"), new BigDecimal("3"));
        // 10 + (9000 x 0.02 + 2000 x 0.01) + 3 x 5 = 10 + 200 + 15
        assertEquals(new BigDecimal("225.00"), quote.subtotal());
        assertEquals(3, quote.lines().size());
        assertEquals(new BigDecimal("3"), quote.lines().get(2).quantity());
        assertEquals("pro v2", quote.planLabel());
    }
}
