package com.meterflow;

import com.meterflow.catalogue.Plan;
import com.meterflow.catalogue.PlanRepository;
import com.meterflow.invoicing.BillingOutcome;
import com.meterflow.invoicing.InvoiceBillingRun;
import com.meterflow.invoicing.InvoiceRepository;
import com.meterflow.metering.UsageController;
import com.meterflow.metering.UsageEventRepository;
import com.meterflow.metering.UsageService;
import com.meterflow.subscriptions.Subscription;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL (Testcontainers): the ON CONFLICT ingestion path, the unique invoice constraint
 * and the event publication registry. Run with: mvn verify -Pintegration (Docker required).
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PostgresBillingIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired UsageService usage;
    @Autowired InvoiceBillingRun billing;
    @Autowired PlanRepository plans;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired InvoiceRepository invoices;
    @Autowired UsageEventRepository events;

    @Test
    void duplicatesAreIgnoredByTheDatabaseAndBillingIsIdempotent() {
        var plan = plans.save(new Plan("it-plan", 1, "IT", "BDT", new BigDecimal("100"), new BigDecimal("0.10"), "api_calls",
                BigDecimal.ZERO, BigDecimal.ZERO));
        subscriptions.save(new Subscription("it-customer", plan.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)));
        List<UsageController.EventInput> batch = new ArrayList<>();
        Instant at = Instant.parse("2026-09-15T10:00:00Z");
        for (int i = 0; i < 1000; i++)
            batch.add(new UsageController.EventInput("it-customer", "api_calls", BigDecimal.ONE, at, "it-" + (i % 900), Map.of()));
        var result = usage.ingest(batch, Instant.parse("2026-09-20T00:00:00Z"));
        assertEquals(900, result.get("accepted"));
        assertEquals(100, result.get("duplicates"));
        assertEquals(0, usage.ingest(batch, Instant.parse("2026-09-20T00:00:00Z")).get("accepted"));

        var first = billing.run("it-customer", YearMonth.of(2026, 9));
        assertEquals(BillingOutcome.CREATED, first.status());
        assertEquals(new BigDecimal("190.00"), invoices.findById(first.invoiceId()).orElseThrow().getSubtotal());
        assertEquals(BillingOutcome.EXISTING, billing.run("it-customer", YearMonth.of(2026, 9)).status());
        assertEquals(1, invoices.findByCustomerIdOrderByCreatedAtDesc("it-customer").size());
    }
}
