package com.meterflow.metering;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.metering.aggregation.MetricAggregation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Usage ingestion (FR1) and metric aggregation (FR2).
 *
 * <p>Ingestion only writes raw events, ledger rows for late events and one {@link UsageRecorded}
 * outbox event; pricing, budgets and billing read the stored events later. This keeps the
 * ingestion path independent of billing load (QA2).</p>
 */
@Service
public class UsageService {
    public static final Duration MAX_LATENESS = Duration.ofHours(72);
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);
    private static final int MAX_PROPERTIES_JSON = 1000;
    private static final TypeReference<Map<String, Object>> PROPERTIES = new TypeReference<>() { };

    private final UsageEventRepository repo;
    private final ObjectMapper mapper;
    private final UsageEventStore store;
    private final LateUsageAdjustmentRepository lateAdjustments;
    private final ApplicationEventPublisher events;

    public UsageService(UsageEventRepository repo, ObjectMapper mapper, UsageEventStore store,
                        LateUsageAdjustmentRepository lateAdjustments, ApplicationEventPublisher events) {
        this.repo = repo;
        this.mapper = mapper;
        this.store = store;
        this.lateAdjustments = lateAdjustments;
        this.events = events;
    }

    /** Close of the UTC calendar month that contains the instant. */
    public static Instant periodClose(Instant timestamp) {
        return YearMonth.from(timestamp.atZone(ZoneOffset.UTC)).plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @Transactional
    public Map<String, Object> ingest(List<UsageController.EventInput> inputs) { return ingest(inputs, Instant.now()); }

    @Transactional
    public Map<String, Object> ingest(List<UsageController.EventInput> inputs, Instant now) {
        List<Map<String, String>> rejected = new ArrayList<>();
        List<UsageEventStore.NewEvent> candidates = new ArrayList<>();
        for (var event : inputs) {
            Instant close = periodClose(event.timestamp());
            if (now.isAfter(close.plus(MAX_LATENESS))) {
                rejected.add(Map.of("idempotencyKey", event.idempotencyKey(),
                        "reason", "Billing period late-arrival window (72 hours after period close) has expired"));
                continue;
            }
            if (event.timestamp().isAfter(now.plus(FUTURE_TOLERANCE))) {
                rejected.add(Map.of("idempotencyKey", event.idempotencyKey(), "reason", "Event timestamp is too far in the future"));
                continue;
            }
            String properties;
            try {
                properties = mapper.writeValueAsString(event.properties() == null ? Map.of() : event.properties());
            } catch (Exception e) {
                rejected.add(Map.of("idempotencyKey", event.idempotencyKey(), "reason", "Properties are not serialisable"));
                continue;
            }
            if (properties.length() > MAX_PROPERTIES_JSON) {
                rejected.add(Map.of("idempotencyKey", event.idempotencyKey(), "reason", "Properties exceed 1000 characters"));
                continue;
            }
            candidates.add(new UsageEventStore.NewEvent(UUID.randomUUID(), event.customerId(), event.metric(), event.quantity(),
                    event.timestamp(), event.idempotencyKey(), properties));
        }

        boolean[] inserted = store.insertIgnoringDuplicates(candidates);
        int accepted = 0, duplicates = 0, late = 0;
        List<LateUsageAdjustment> ledger = new ArrayList<>();
        Set<String> customers = new TreeSet<>();
        Set<String> periods = new TreeSet<>();
        for (int i = 0; i < candidates.size(); i++) {
            var e = candidates.get(i);
            if (!inserted[i]) { duplicates++; continue; }
            accepted++;
            String period = YearMonth.from(e.timestamp().atZone(ZoneOffset.UTC)).toString();
            customers.add(e.customerId());
            periods.add(period);
            if (now.isAfter(periodClose(e.timestamp()))) {
                ledger.add(new LateUsageAdjustment(e.id(), e.customerId(), period));
                late++;
            }
        }
        if (!ledger.isEmpty()) lateAdjustments.saveAll(ledger);
        if (accepted > 0) events.publishEvent(new UsageRecorded(List.copyOf(customers), List.copyOf(periods), accepted, now));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("received", inputs.size());
        result.put("accepted", accepted);
        result.put("duplicates", duplicates);
        result.put("lateAccepted", late);
        result.put("rejected", rejected.size());
        result.put("rejectionDetails", rejected);
        return result;
    }

    public Map<String, Object> aggregate(String customer, String metric, Instant from, Instant to, String operation,
                                         Map<String, String> filters, String distinctProperty) {
        if (from == null || to == null || !from.isBefore(to)) throw new IllegalArgumentException("from must be before to");
        if (Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0)
            throw new IllegalArgumentException("Aggregation window cannot exceed 366 days");
        Map<String, String> effectiveFilters = filters == null ? Map.of() : filters;
        var samples = repo.findMetricWindow(customer, metric, from, to).stream().map(event -> {
            Map<String, Object> properties;
            try {
                properties = event.getPropertiesJson() == null ? Map.of() : mapper.readValue(event.getPropertiesJson(), PROPERTIES);
            } catch (Exception ex) {
                properties = Map.of();
            }
            String distinct = distinctProperty == null || distinctProperty.isBlank() ? null : String.valueOf(properties.get(distinctProperty));
            return new MetricAggregation.Sample(event.getQuantity(), properties, distinct, event.getTimestamp());
        }).toList();
        BigDecimal value = MetricAggregation.aggregate(operation, samples, effectiveFilters);
        long matched = samples.stream().filter(sample -> effectiveFilters.entrySet().stream()
                .allMatch(f -> Objects.equals(String.valueOf(sample.properties().get(f.getKey())), f.getValue()))).count();
        return Map.of("customerId", customer, "metric", metric, "aggregation", operation, "value", value,
                "matchedEventCount", matched, "from", from, "to", to, "filters", effectiveFilters);
    }

    public Map<String, Object> summary(String customer) {
        Instant start = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = Instant.now();
        var events = repo.findByCustomerIdAndTimestampGreaterThanEqualAndTimestampLessThan(customer, start, end);
        Map<String, BigDecimal> totals = events.stream().collect(Collectors.groupingBy(
                UsageEvent::getMetric, TreeMap::new, Collectors.reducing(BigDecimal.ZERO, UsageEvent::getQuantity, BigDecimal::add)));
        return Map.of("customerId", customer, "eventCount", events.size(), "usage", totals, "periodStart", start, "asOf", end);
    }
}
