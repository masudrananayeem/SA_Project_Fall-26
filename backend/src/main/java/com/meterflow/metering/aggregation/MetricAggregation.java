package com.meterflow.metering.aggregation;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;

/** Aggregates already-filtered event records; property filters use exact string matching. */
public final class MetricAggregation {
    private MetricAggregation() {}
    public record Sample(BigDecimal quantity, Map<String,Object> properties, String distinctValue) {}
    public static BigDecimal aggregate(String operation, List<Sample> input, Map<String,String> filters) {
        Predicate<Sample> matches=s->filters.entrySet().stream().allMatch(f->Objects.equals(String.valueOf(s.properties().get(f.getKey())),f.getValue()));
        List<Sample> samples=input.stream().filter(matches).toList();
        return switch(operation.toLowerCase(Locale.ROOT)) {
            case "sum" -> samples.stream().map(Sample::quantity).reduce(BigDecimal.ZERO,BigDecimal::add);
            case "count" -> BigDecimal.valueOf(samples.size());
            case "maximum" -> samples.stream().map(Sample::quantity).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            case "unique_count" -> BigDecimal.valueOf(samples.stream().map(Sample::distinctValue).filter(Objects::nonNull).distinct().count());
            case "latest_value" -> samples.isEmpty()?BigDecimal.ZERO:samples.get(samples.size()-1).quantity();
            default -> throw new IllegalArgumentException("Unsupported aggregation: "+operation);
        };
    }
}
