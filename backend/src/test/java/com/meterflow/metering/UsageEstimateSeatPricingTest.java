package com.meterflow.metering;

import com.meterflow.catalogue.*;
import com.meterflow.subscriptions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsageEstimateSeatPricingTest {
    @Test
    void estimateUsesTheSubscriptionsConfiguredSeatCount() {
        UsageEventRepository events = mock(UsageEventRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        PlanRepository plans = mock(PlanRepository.class);
        PricingService pricing = mock(PricingService.class);
        UUID planId = UUID.randomUUID();
        Plan plan = new Plan("team", 1, "Team", "BDT", BigDecimal.ZERO,
                new BigDecimal("2.00"), "api_calls", BigDecimal.ZERO, BigDecimal.ZERO);
        Subscription subscription = new Subscription("customer-1", planId,
                LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1),
                LocalDate.now(ZoneOffset.UTC).plusMonths(1).withDayOfMonth(1));
        subscription.setSeatCount(7);

        when(events.findByCustomerIdAndTimestampGreaterThanEqualAndTimestampLessThan(
                eq("customer-1"), any(Instant.class), any(Instant.class))).thenReturn(List.of());
        when(events.findByCustomerIdAndMetricAndTimestampGreaterThanEqualAndTimestampLessThan(
                eq("customer-1"), eq("api_calls"), any(Instant.class), any(Instant.class))).thenReturn(List.of());
        when(subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc("customer-1", "ACTIVE"))
                .thenReturn(Optional.of(subscription));
        when(plans.findById(planId)).thenReturn(Optional.of(plan));
        when(pricing.calculate(plan, BigDecimal.ZERO, BigDecimal.valueOf(7))).thenReturn(new BigDecimal("14.00"));

        UsageService service = new UsageService(events, new ObjectMapper(), pricing, subscriptions, plans);
        Map<String, Object> result = service.estimate("customer-1");

        assertEquals(new BigDecimal("14.00"), result.get("estimatedAmount"));
        verify(pricing).calculate(plan, BigDecimal.ZERO, BigDecimal.valueOf(7));
    }
}
