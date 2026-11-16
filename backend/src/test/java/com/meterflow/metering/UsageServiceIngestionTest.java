package com.meterflow.metering;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsageServiceIngestionTest {
    private InMemoryUsageEventStore store;
    private LateUsageAdjustmentRepository ledger;
    private final List<Object> published = new ArrayList<>();
    private final List<LateUsageAdjustment> ledgerRows = new ArrayList<>();
    private UsageService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryUsageEventStore();
        ledger = mock(LateUsageAdjustmentRepository.class);
        when(ledger.saveAll(any())).thenAnswer(inv -> {
            Iterable<LateUsageAdjustment> rows = inv.getArgument(0);
            rows.forEach(ledgerRows::add);
            return ledgerRows;
        });
        service = new UsageService(mock(UsageEventRepository.class), new ObjectMapper(), store, ledger, published::add);
    }

    private UsageController.EventInput event(String key, Instant timestamp) {
        return new UsageController.EventInput("customer-1", "api_calls", BigDecimal.ONE, timestamp, key, Map.of("region", "ap-south-1"));
    }

    @Test
    void rejectsEventsAfterTheSeventyTwoHourWindowFromPeriodClose() {
        Instant now = Instant.parse("2026-11-04T00:00:01Z"); // October closed 2026-11-01T00:00Z; +72h = 2026-11-04T00:00Z
        var result = service.ingest(List.of(event("late-1", Instant.parse("2026-10-31T23:00:00Z"))), now);
        assertEquals(0, result.get("accepted"));
        assertEquals(1, result.get("rejected"));
        assertEquals(0, store.size());
    }

    @Test
    void acceptsLateEventInsideWindowAndRecordsItInTheLedger() {
        Instant now = Instant.parse("2026-11-03T23:59:00Z");
        var result = service.ingest(List.of(event("late-ok", Instant.parse("2026-10-31T23:00:00Z"))), now);
        assertEquals(1, result.get("accepted"));
        assertEquals(1, result.get("lateAccepted"));
        assertEquals(1, ledgerRows.size());
        assertEquals("2026-10", ledgerRows.get(0).getBillingPeriod());
        assertEquals(LateUsageAdjustment.PENDING, ledgerRows.get(0).getStatus());
    }

    @Test
    void ignoresDuplicatesIncludingDuplicatesInsideOneBatch() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        Instant t = Instant.parse("2026-11-10T11:00:00Z");
        service.ingest(List.of(event("dup-1", t)), now);
        var result = service.ingest(List.of(event("dup-1", t), event("new-1", t), event("new-1", t)), now);
        assertEquals(1, result.get("accepted"));
        assertEquals(2, result.get("duplicates"));
        assertEquals(2, store.size());
        assertTrue(ledgerRows.isEmpty());
    }

    @Test
    void publishesOneUsageRecordedEventPerAcceptedBatch() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        service.ingest(List.of(event("a", now.minusSeconds(60)), event("b", now.minusSeconds(30))), now);
        service.ingest(List.of(event("a", now.minusSeconds(60))), now); // all duplicates: nothing published
        assertEquals(1, published.size());
        var recorded = (UsageRecorded) published.get(0);
        assertEquals(List.of("customer-1"), recorded.customerIds());
        assertEquals(List.of("2026-11"), recorded.periods());
        assertEquals(2, recorded.acceptedEvents());
    }

    @Test
    void rejectsEventsTooFarInTheFuture() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        var result = service.ingest(List.of(event("future", now.plusSeconds(3600))), now);
        assertEquals(1, result.get("rejected"));
    }
}
