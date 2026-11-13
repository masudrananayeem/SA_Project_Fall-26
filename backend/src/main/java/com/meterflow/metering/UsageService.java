package com.meterflow.metering;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PricingService;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final PricingService pricing;
    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;

    public UsageService(UsageEventRepository repo, ObjectMapper mapper, PricingService pricing,
                        SubscriptionRepository subscriptions, PlanRepository plans) {
        this.repo = repo;
        this.mapper = mapper;
        this.pricing = pricing;
        this.subscriptions = subscriptions;
        this.plans = plans;
    }

    // Intentionally not wrapped in one transaction: each insert is independently committed so
    // a concurrent idempotency-key collision can be counted without rolling back the whole batch.
    public Map<String, Object> ingest(List<UsageController.EventInput> inputs) {
        Instant now = Instant.now();
        int accepted = 0, duplicates = 0;
        List<Map<String, String>> rejected = new ArrayList<>();

        for (var event : inputs) {
            // Late-arrival eligibility is measured from the close of the event's billing
            // month, not from the event timestamp. Events inside a closed period remain
            // admissible for 72 hours so billing can pick them up in a late adjustment.
            YearMonth eventPeriod = YearMonth.from(event.timestamp().atZone(ZoneOffset.UTC));
            Instant periodClose = eventPeriod.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            if (now.isAfter(periodClose.plus(MAX_LATENESS))) {
                rejected.add(Map.of("idempotencyKey", event.idempotencyKey(),
                        "reason", "Billing period late-arrival window (72 hours after period close) has expired"));
                continue;
            }
            if (event.timestamp().isAfter(now.plus(FUTURE_TOLERANCE))) {
                rejected.add(Map.of("idempotencyKey", event.idempotencyKey(), "reason", "Event timestamp is too far in the future"));
                continue;
            }
            if (repo.existsByIdempotencyKey(event.idempotencyKey())) {
                duplicates++;
                continue;
            }
            try {
                String properties = mapper.writeValueAsString(event.properties() == null ? Map.of() : event.properties());
                repo.saveAndFlush(new UsageEvent(event.customerId(), event.metric(), event.quantity(), event.timestamp(), event.idempotencyKey(), properties));
                accepted++;
            } catch (DataIntegrityViolationException duplicateRace) {
                // The database unique constraint is the final authority under concurrent requests.
                duplicates++;
            } catch (Exception serializationFailure) {
                throw new IllegalArgumentException("Could not serialize event properties");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("received", inputs.size());
        result.put("accepted", accepted);
        result.put("duplicates", duplicates);
        result.put("rejected", rejected.size());
        result.put("rejectionDetails", rejected);
        return result;
    }

    public Map<String, Object> summary(String customer) {
        Instant start = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = Instant.now();
        var events = repo.findByCustomerIdAndTimestampGreaterThanEqualAndTimestampLessThan(customer, start, end);
        Map<String, BigDecimal> totals = events.stream().collect(Collectors.groupingBy(
                UsageEvent::getMetric,
                Collectors.reducing(BigDecimal.ZERO, UsageEvent::getQuantity, BigDecimal::add)));
        return Map.of("customerId", customer, "eventCount", events.size(), "usage", totals, "periodStart", start, "asOf", end);
    }

    public Map<String, Object> estimate(String customer) {
        var summary = summary(customer);
        var active = subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(customer, "ACTIVE");
        if (active.isEmpty()) return Map.of("customerId", customer, "eventCount", summary.get("eventCount"),
                "usage", summary.get("usage"), "estimatedAmount", "0.00", "currency", "BDT",
                "note", "No active subscription; estimate is usage-only.");

        var subscription = active.get();
        var plan = plans.findById(subscription.getPlanId()).orElseThrow(() -> new IllegalStateException("Subscription references a missing plan"));
        Instant from = subscription.getPeriodStart().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = Instant.now();
        var usage = repo.findByCustomerIdAndMetricAndTimestampGreaterThanEqualAndTimestampLessThan(
                customer, plan.getMetric(), from, to).stream().map(UsageEvent::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        var amount = pricing.calculate(plan, usage);
        return Map.of("customerId", customer, "eventCount", summary.get("eventCount"), "usage", summary.get("usage"),
                "billableMetric", plan.getMetric(), "billableQuantity", usage, "estimatedAmount", amount,
                "currency", plan.getCurrency(), "plan", plan.getPlanKey() + " v" + plan.getVersion(), "asOf", to);
    }
}
