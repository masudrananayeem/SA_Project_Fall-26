package com.meterflow.metering;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PricingService;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UsageService {
    private static final Duration MAX_LATENESS = Duration.ofHours(72);
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);
    private final UsageEventRepository repo;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final PricingService pricing;
    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;

    public UsageService(UsageEventRepository repo, ObjectMapper mapper, JdbcTemplate jdbc,
                        PricingService pricing, SubscriptionRepository subscriptions, PlanRepository plans) {
        this.repo = repo;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.pricing = pricing;
        this.subscriptions = subscriptions;
        this.plans = plans;
    }

    // Each INSERT runs independently. ON CONFLICT makes the database unique key the final
    // authority, including when two ingestion requests race on the same idempotency key.
    public Map<String, Object> ingest(List<UsageController.EventInput> inputs) {
        Instant now = Instant.now();
        int accepted = 0, duplicates = 0;
        List<Map<String, String>> rejected = new ArrayList<>();
        for (var event : inputs) {
            if (event.timestamp().isBefore(now.minus(MAX_LATENESS))) {
                rejected.add(Map.of("idempotencyKey", event.idempotencyKey(), "reason", "Event is older than the 72-hour late-arrival window"));
                continue;
            }
            if (event.timestamp().isAfter(now.plus(FUTURE_TOLERANCE))) {
                rejected.add(Map.of("idempotencyKey", event.idempotencyKey(), "reason", "Event timestamp is more than 5 minutes in the future"));
                continue;
            }
            try {
                String properties = mapper.writeValueAsString(event.properties() == null ? Map.of() : event.properties());
                int inserted = jdbc.update("""
                    INSERT INTO usage_events (id, customer_id, metric, quantity, timestamp, idempotency_key, properties_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (idempotency_key) DO NOTHING
                    """, UUID.randomUUID(), event.customerId(), event.metric(), event.quantity(),
                        event.timestamp().atOffset(ZoneOffset.UTC), event.idempotencyKey(), properties);
                if (inserted == 1) accepted++; else duplicates++;
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException("Could not serialize event properties");
            }
        }
        return Map.of("received", inputs.size(), "accepted", accepted, "duplicates", duplicates,
                "rejected", rejected.size(), "rejectionDetails", rejected, "lateEventWindowHours", 72);
    }

    public Map<String, Object> summary(String customer) {
        Instant start = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = Instant.now();
        var events = repo.findWindow(customer, start, end);
        Map<String, BigDecimal> totals = events.stream().collect(Collectors.groupingBy(
                UsageEvent::getMetric,
                Collectors.reducing(BigDecimal.ZERO, UsageEvent::getQuantity, BigDecimal::add)));
        return Map.of("customerId", customer, "eventCount", events.size(), "usage", totals,
                "periodStart", start, "asOf", end);
    }

    public Map<String, Object> estimate(String customer) {
        var summary = summary(customer);
        var active = subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(customer, "ACTIVE");
        if (active.isEmpty()) return Map.of("customerId", customer, "eventCount", summary.get("eventCount"),
                "usage", summary.get("usage"), "estimatedAmount", "0.00", "currency", "BDT",
                "note", "No active subscription; estimate is usage-only.");
        var subscription = active.get();
        var plan = plans.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Subscription references a missing plan"));
        Instant from = subscription.getPeriodStart().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = Instant.now();
        var usage = repo.findMetricWindow(customer, plan.getMetric(), from, to).stream()
                .map(UsageEvent::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        var amount = pricing.calculate(plan, usage);
        return Map.of("customerId", customer, "eventCount", summary.get("eventCount"),
                "usage", summary.get("usage"), "billableMetric", plan.getMetric(),
                "billableQuantity", usage, "estimatedAmount", amount, "currency", plan.getCurrency(),
                "plan", plan.getPlanKey() + " v" + plan.getVersion(), "asOf", to);
    }
}
