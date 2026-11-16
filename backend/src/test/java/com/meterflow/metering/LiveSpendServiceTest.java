package com.meterflow.metering;

import com.meterflow.catalogue.Plan;
import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PricingService;
import com.meterflow.subscriptions.Subscription;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveSpendServiceTest {
    private final UsageEventRepository events = mock(UsageEventRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final PlanRepository plans = mock(PlanRepository.class);
    private final PricingService pricing = new PricingService();
    private final UUID planId = UUID.randomUUID();

    private LiveSpendService service(Plan plan, int seats) {
        var sub = new Subscription("customer-1", planId, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        sub.setSeatCount(seats);
        when(subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc("customer-1", "ACTIVE")).thenReturn(Optional.of(sub));
        when(plans.findById(planId)).thenReturn(Optional.of(plan));
        return new LiveSpendService(events, subscriptions, plans, pricing, new BillableUsageCalculator(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @Test
    void estimateUsesDatabaseSumAndPlanPricingIncludingTax() {
        var plan = new Plan("starter", 1, "Starter", "BDT", new BigDecimal("299"), new BigDecimal("0.05"), "api_calls",
                new BigDecimal("1000"), BigDecimal.ZERO);
        plan.setTaxRate(new BigDecimal("15"));
        when(events.sumQuantity(eq("customer-1"), eq("api_calls"), any(), any())).thenReturn(new BigDecimal("3000"));
        when(events.countInWindow(eq("customer-1"), eq("api_calls"), any(), any())).thenReturn(3000L);
        var result = service(plan, 1).estimate("customer-1", "2026-10");
        // 299 flat + (3000 - 1000) x 0.05 = 399.00; VAT 15% = 59.85
        assertEquals(new BigDecimal("399.00"), result.get("estimatedSubtotal"));
        assertEquals(new BigDecimal("59.85"), result.get("estimatedTax"));
        assertEquals(new BigDecimal("458.85"), result.get("estimatedAmount"));
        assertEquals(3000L, result.get("eventCount"));
    }

    @Test
    void estimateEqualsTheBillingQuoteForTheSameUsage() {
        var plan = new Plan("seat", 1, "Seats", "USD", BigDecimal.ZERO, new BigDecimal("0.01"), "api_calls", BigDecimal.ZERO, BigDecimal.ZERO);
        when(events.sumQuantity(eq("customer-1"), eq("api_calls"), any(), any())).thenReturn(new BigDecimal("12345"));
        var result = service(plan, 7).estimate("customer-1", "2026-10");
        var billing = pricing.quoteForPeriod(plan, new BigDecimal("12345"), BigDecimal.valueOf(7), null, YearMonth.parse("2026-10"));
        assertEquals(billing.subtotal(), result.get("estimatedSubtotal"));
    }

    @Test
    void returnsZeroWithoutAnActiveSubscription() {
        var service = new LiveSpendService(events, subscriptions, plans, pricing, new BillableUsageCalculator(new com.fasterxml.jackson.databind.ObjectMapper()));
        var result = service.estimate("nobody", "2026-10");
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) result.get("estimatedAmount")));
    }
}
