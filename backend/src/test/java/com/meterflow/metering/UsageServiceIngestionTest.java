package com.meterflow.metering;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PricingService;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsageServiceIngestionTest {
    private UsageEventRepository repository;
    private UsageService service;

    @BeforeEach
    void setUp() {
        repository = mock(UsageEventRepository.class);
        service = new UsageService(repository, new ObjectMapper(), mock(PricingService.class),
                mock(SubscriptionRepository.class), mock(PlanRepository.class));
    }

    private UsageController.EventInput event(String key, Instant timestamp) {
        return new UsageController.EventInput("customer-1", "api_calls", BigDecimal.ONE,
                timestamp, key, Map.of("region", "ap-south-1"));
    }

    @Test
    void rejectsEventsOlderThanSeventyTwoHours() {
        when(repository.existsByIdempotencyKey("late-1")).thenReturn(false);
        var result = service.ingest(java.util.List.of(event("late-1", Instant.now().minusSeconds(73 * 3600))));
        assertEquals(0, result.get("accepted"));
        assertEquals(1, result.get("rejected"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void acceptsRecentEventsAndCountsExistingIdempotencyKeys() {
        when(repository.existsByIdempotencyKey("duplicate-1")).thenReturn(true);
        when(repository.existsByIdempotencyKey("new-1")).thenReturn(false);
        var result = service.ingest(java.util.List.of(
                event("duplicate-1", Instant.now()), event("new-1", Instant.now())));
        assertEquals(1, result.get("accepted"));
        assertEquals(1, result.get("duplicates"));
        verify(repository, times(1)).saveAndFlush(any(UsageEvent.class));
    }

    @Test
    void treatsConcurrentUniqueKeyCollisionAsDuplicate() {
        when(repository.existsByIdempotencyKey("race-1")).thenReturn(false);
        when(repository.saveAndFlush(any(UsageEvent.class))).thenThrow(new DataIntegrityViolationException("unique key"));
        var result = service.ingest(java.util.List.of(event("race-1", Instant.now())));
        assertEquals(0, result.get("accepted"));
        assertEquals(1, result.get("duplicates"));
    }
}
