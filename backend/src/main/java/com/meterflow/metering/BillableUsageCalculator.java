package com.meterflow.metering;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.Plan;
import com.meterflow.metering.aggregation.MetricAggregation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FR2: turns a plan's billable-metric definition (aggregation sum / count / maximum / unique_count /
 * latest_value, optional property and property filters) into the quantity that is priced. Used by
 * the billing run, the live estimate and the trace endpoint, so all three agree.
 */
@Component
public class BillableUsageCalculator {
    private static final TypeReference<Map<String, Object>> PROPERTIES = new TypeReference<>() { };
    private static final TypeReference<Map<String, String>> FILTERS = new TypeReference<>() { };

    private final ObjectMapper mapper;

    public BillableUsageCalculator(ObjectMapper mapper) { this.mapper = mapper; }

    /** The events that count for the plan (metric and filters match) and their aggregated quantity. */
    public record BillableUsage(BigDecimal quantity, List<UsageEvent> matchedEvents) { }

    public BillableUsage quantify(Plan plan, List<UsageEvent> events) {
        Map<String, String> filters = filters(plan);
        List<UsageEvent> relevant = events.stream().filter(e -> e.getMetric().equals(plan.getMetric())).toList();
        if (plan.isAdditiveUnfiltered()) {
            return new BillableUsage(relevant.stream().map(UsageEvent::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add), relevant);
        }
        List<MetricAggregation.Sample> samples = relevant.stream().map(e -> {
            Map<String, Object> props = properties(e);
            String distinct = plan.getAggregationProperty() == null ? null
                    : props.get(plan.getAggregationProperty()) == null ? null : String.valueOf(props.get(plan.getAggregationProperty()));
            return new MetricAggregation.Sample(e.getQuantity(), props, distinct, e.getTimestamp());
        }).toList();
        BigDecimal quantity = MetricAggregation.aggregate(plan.getAggregation(), samples, filters);
        List<UsageEvent> matched = relevant.stream().filter(e -> matches(e, filters)).toList();
        return new BillableUsage(quantity.max(BigDecimal.ZERO), matched);
    }

    public boolean matches(Plan plan, UsageEvent event) {
        return event.getMetric().equals(plan.getMetric()) && matches(event, filters(plan));
    }

    private boolean matches(UsageEvent e, Map<String, String> filters) {
        if (filters.isEmpty()) return true;
        Map<String, Object> props = properties(e);
        return filters.entrySet().stream().allMatch(f -> Objects.equals(String.valueOf(props.get(f.getKey())), f.getValue()));
    }

    private Map<String, String> filters(Plan plan) {
        String json = plan.getMetricFiltersJson();
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, FILTERS);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid metric filters on plan " + plan.getPlanKey());
        }
    }

    private Map<String, Object> properties(UsageEvent e) {
        if (e.getPropertiesJson() == null || e.getPropertiesJson().isBlank()) return Map.of();
        try {
            return mapper.readValue(e.getPropertiesJson(), PROPERTIES);
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
