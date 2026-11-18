package com.meterflow.metering;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.subscriptions.Subscription;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Usage is refused while a prepaid plan is unpaid or a postpaid account is blocked. */
class UsageStandingGateTest {

    @Test
    void blockedAndUnpaidCustomersCannotSendUsage() {
        var subs = mock(SubscriptionRepository.class);
        var blocked = new Subscription("blocked", UUID.randomUUID(), LocalDate.now().minusDays(70), LocalDate.now().plusYears(1));
        blocked.block();
        var pending = new Subscription("pending", UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusYears(1));
        pending.awaitPayment();
        var ok = new Subscription("ok", UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusYears(1));
        when(subs.findByCustomerIdOrderByPeriodStartDesc(any())).thenAnswer(inv -> switch (inv.<String>getArgument(0)) {
            case "blocked" -> List.of(blocked);
            case "pending" -> List.of(pending);
            case "ok" -> List.of(ok);
            default -> List.of();
        });
        List<Object> published = new ArrayList<>();
        var service = new UsageService(mock(UsageEventRepository.class), new ObjectMapper(), new InMemoryUsageEventStore(),
                mock(LateUsageAdjustmentRepository.class), published::add, subs);
        Instant now = Instant.now();
        List<UsageController.EventInput> events = List.of(
                new UsageController.EventInput("blocked", "api_calls", BigDecimal.ONE, now, "k1", Map.of()),
                new UsageController.EventInput("pending", "api_calls", BigDecimal.ONE, now, "k2", Map.of()),
                new UsageController.EventInput("ok", "api_calls", BigDecimal.ONE, now, "k3", Map.of()),
                new UsageController.EventInput("new-customer", "api_calls", BigDecimal.ONE, now, "k4", Map.of()));

        Map<String, Object> result = service.ingest(events, now);

        assertEquals(2, result.get("accepted"));
        assertEquals(2, result.get("rejected"));
    }
}
