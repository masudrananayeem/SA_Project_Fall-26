package com.meterflow.metering;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PricingService;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UsageServiceIngestionTest {
    private UsageEventRepository repository;
    private JdbcTemplate jdbc;
    private UsageService service;

    @BeforeEach
    void setUp() {
        repository = mock(UsageEventRepository.class);
        jdbc = mock(JdbcTemplate.class);
        service = new UsageService(repository, new ObjectMapper(), jdbc, mock(PricingService.class),
                mock(SubscriptionRepository.class), mock(PlanRepository.class));
    }

    private UsageController.EventInput event(String key, Instant timestamp) {
        return new UsageController.EventInput("customer-1", "api_calls", BigDecimal.ONE,
                timestamp, key, Map.of("region", "ap-south-1"));
    }

    @Test
    void rejectsEventsOlderThanSeventyTwoHours() {
        var result = service.ingest(List.of(event("late-1", Instant.now().minusSeconds(73 * 3600))));
        assertEquals(0, result.get("accepted"));
        assertEquals(1, result.get("rejected"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void acceptsRecentEventsAndCountsExistingIdempotencyKeys() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0, 1);
        var result = service.ingest(List.of(event("duplicate-1", Instant.now()), event("new-1", Instant.now())));
        assertEquals(1, result.get("accepted"));
        assertEquals(1, result.get("duplicates"));
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsEventsTooFarInTheFuture() {
        var result = service.ingest(List.of(event("future-1", Instant.now().plusSeconds(600))));
        assertEquals(1, result.get("rejected"));
        verifyNoInteractions(jdbc);
    }
}
