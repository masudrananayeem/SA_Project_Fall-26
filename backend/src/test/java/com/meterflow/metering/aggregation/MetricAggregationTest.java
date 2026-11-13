package com.meterflow.metering.aggregation;
import org.junit.jupiter.api.Test;import java.math.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class MetricAggregationTest {
 private List<MetricAggregation.Sample> samples(){return List.of(new MetricAggregation.Sample(new BigDecimal("2"),Map.of("region","bd"),"u1"),new MetricAggregation.Sample(new BigDecimal("3"),Map.of("region","us"),"u2"),new MetricAggregation.Sample(new BigDecimal("4"),Map.of("region","bd"),"u1"));}
 @Test void sumRespectsFilters(){assertEquals(new BigDecimal("6"),MetricAggregation.aggregate("sum",samples(),Map.of("region","bd")));}
 @Test void uniqueCountDeduplicatesPropertyValues(){assertEquals(new BigDecimal("2"),MetricAggregation.aggregate("unique_count",samples(),Map.of()));}
 @Test void latestValueUsesLastSampleInInputOrder(){assertEquals(new BigDecimal("4"),MetricAggregation.aggregate("latest_value",samples(),Map.of()));}
 @Test void countAndMaximumWork(){assertEquals(new BigDecimal("3"),MetricAggregation.aggregate("count",samples(),Map.of()));assertEquals(new BigDecimal("4"),MetricAggregation.aggregate("maximum",samples(),Map.of()));}
}
