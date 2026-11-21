package com.meterflow.metering;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.metering.aggregation.MetricAggregation;
import com.meterflow.subscriptions.Subscription;
import com.meterflow.subscriptions.SubscriptionRepository;
import com.meterflow.subscriptions.SubscriptionState;
import org.springframework.beans.factory.annotation.Autowired;
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
    /** Null in unit tests that do not care about account standing. */
    private final SubscriptionRepository subscriptions;

    public UsageService(UsageEventRepository repo, ObjectMapper mapper, UsageEventStore store,
                        LateUsageAdjustmentRepository lateAdjustments, ApplicationEventPublisher events) {
        this(repo, mapper, store, lateAdjustments, events, null);
    }

    @Autowired
    public UsageService(UsageEventRepository repo, ObjectMapper mapper, UsageEventStore store,
                        LateUsageAdjustmentRepository lateAdjustments, ApplicationEventPublisher events,
                        SubscriptionRepository subscriptions) {
        this.repo = repo;
        this.mapper = mapper;
        this.store = store;
        this.lateAdjustments = lateAdjustments;
        this.events = events;
        this.subscriptions = subscriptions;
    }

    /**
     * Why a customer may not send usage right now, or empty. Customers without a subscription are
     * accepted (usage can be recorded before a plan is assigned).
     */
    public Optional<String> usageBlockedReason(String customerId) {
        return usageBlockedReason(customerId, LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Why usage for {@code period} is refused, if it is. Every accepted event must be billed exactly
     * once, so usage is only taken while a subscription will invoice that month:
     * <ul>
     *   <li>live subscription waiting for payment, blocked, suspended or paused: refused;</li>
     *   <li>only cancelled or expired subscriptions: accepted only for events that happened on or
     *       before the cancellation day in a month that is still invoiced (late arrivals), refused
     *       otherwise;</li>
     *   <li>no subscription yet: accepted (a subscription started later that month bills it).</li>
     * </ul>
     */
    public Optional<String> usageBlockedReason(String customerId, LocalDate eventDay) {
        YearMonth period = YearMonth.from(eventDay);
        if (subscriptions == null) return Optional.empty();
        var all = subscriptions.findByCustomerIdOrderByPeriodStartDesc(customerId);
        if (all.isEmpty()) return Optional.empty();
        Subscription latest = all.stream()
                .filter(s -> !SubscriptionState.CANCELLED.name().equals(s.getStatus()) && !SubscriptionState.EXPIRED.name().equals(s.getStatus()))
                .findFirst().orElse(null);
        if (latest == null) {
            boolean stillInvoiced = all.stream().anyMatch(s -> s.billableIn(period)
                    && s.getStatusChangedAt() != null && !eventDay.isAfter(s.getStatusChangedAt()));
            return stillInvoiced ? Optional.empty()
                    : Optional.of("Subscription cancelled: usage is no longer accepted. Choose a plan to start again");
        }
        if (latest.state().acceptsUsage()) return Optional.empty();
        return Optional.of(switch (latest.state()) {
            case PENDING_PAYMENT -> "Prepaid plan not paid yet: pay the pending invoice to start using the service";
            case BLOCKED -> "Account blocked for unpaid invoices: pay them to resume the service";
            case PAUSED -> "Subscription is paused: resume it to send usage again";
            default -> "Service suspended for non-payment: pay the overdue invoice to resume";
        });
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
        Map<String, Optional<String>> standing = new HashMap<>();
        for (var event : inputs) {
            LocalDate eventDay = event.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
            Optional<String> blocked = standing.computeIfAbsent(event.customerId() + "|" + eventDay,
                    key -> usageBlockedReason(event.customerId(), eventDay));
            if (blocked.isPresent()) {
                rejected.add(Map.of("idempotencyKey", event.idempotencyKey(), "reason", blocked.get()));
                continue;
            }
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

    /** Usage breakdown for the "API usage" pages (customer portal and admin customer view). */
    @Transactional(readOnly = true)
    public Map<String, Object> breakdown(String customer, Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) throw new IllegalArgumentException("from must be before to");
        if (Duration.between(from, to).compareTo(Duration.ofDays(93)) > 0)
            throw new IllegalArgumentException("Breakdown window cannot exceed 93 days");
        List<UsageEvent> window = repo.findWindow(customer, from, to);
        Map<String, BigDecimal> quantityByMetric = new TreeMap<>();
        Map<String, Long> countByMetric = new TreeMap<>();
        Map<String, Map<String, long[]>> countsByProperty = new TreeMap<>();
        Map<String, Map<String, BigDecimal>> quantityByProperty = new TreeMap<>();
        Map<String, Long> perDay = new TreeMap<>();
        Instant last = null;
        for (UsageEvent e : window) {
            quantityByMetric.merge(e.getMetric(), e.getQuantity(), BigDecimal::add);
            countByMetric.merge(e.getMetric(), 1L, Long::sum);
            perDay.merge(e.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate().toString(), 1L, Long::sum);
            if (last == null || e.getTimestamp().isAfter(last)) last = e.getTimestamp();
            Map<String, Object> props;
            try {
                props = e.getPropertiesJson() == null ? Map.of() : mapper.readValue(e.getPropertiesJson(), PROPERTIES);
            } catch (Exception ex) {
                props = Map.of();
            }
            for (var p : props.entrySet()) {
                if (p.getValue() == null || p.getValue() instanceof Map || p.getValue() instanceof Collection) continue;
                String value = String.valueOf(p.getValue());
                if (value.length() > 120) value = value.substring(0, 120);
                countsByProperty.computeIfAbsent(p.getKey(), k -> new HashMap<>()).computeIfAbsent(value, v -> new long[1])[0]++;
                quantityByProperty.computeIfAbsent(p.getKey(), k -> new HashMap<>()).merge(value, e.getQuantity(), BigDecimal::add);
            }
        }
        List<Map<String, Object>> metrics = new ArrayList<>();
        for (var m : quantityByMetric.entrySet())
            metrics.add(Map.of("metric", m.getKey(), "events", countByMetric.get(m.getKey()), "quantity", m.getValue()));
        Map<String, Object> properties = new LinkedHashMap<>();
        for (var prop : countsByProperty.entrySet()) {
            // Skip high-cardinality properties (ids): they say little about "what" is used.
            if (prop.getValue().size() > 50) continue;
            List<Map<String, Object>> values = prop.getValue().entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                    .limit(12)
                    .map(v -> Map.<String, Object>of("value", v.getKey(), "events", v.getValue()[0],
                            "quantity", quantityByProperty.get(prop.getKey()).get(v.getKey())))
                    .toList();
            properties.put(prop.getKey(), values);
        }
        List<Map<String, Object>> days = perDay.entrySet().stream()
                .map(d -> Map.<String, Object>of("day", d.getKey(), "events", d.getValue())).toList();
        List<Map<String, Object>> recent = window.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(15)
                .map(e -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", e.getId());
                    r.put("metric", e.getMetric());
                    r.put("quantity", e.getQuantity());
                    r.put("timestamp", e.getTimestamp());
                    r.put("idempotencyKey", e.getIdempotencyKey());
                    r.put("properties", e.getPropertiesJson());
                    return r;
                }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customer);
        result.put("from", from);
        result.put("to", to);
        result.put("totalEvents", window.size());
        result.put("lastEventAt", last);
        result.put("metrics", metrics);
        result.put("properties", properties);
        result.put("days", days);
        result.put("recent", recent);
        result.put("usageAccepted", usageBlockedReason(customer).isEmpty());
        result.put("usageBlockedReason", usageBlockedReason(customer).orElse(null));
        return result;
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
