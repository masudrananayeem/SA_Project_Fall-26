package com.meterflow.metering;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PricingService;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetricAggregationEndpointLogicTest {
 @Test void aggregateAppliesPropertyFiltersAndDistinctField() throws Exception {
  var repository=mock(UsageEventRepository.class);
  when(repository.findMetricWindow(eq("customer"),eq("api_calls"),any(),any())).thenReturn(java.util.List.of(
   new UsageEvent("customer","api_calls",new BigDecimal("3"),Instant.parse("2026-11-01T00:00:00Z"),"a","{\"region\":\"ap-south\",\"endpoint\":\"/v1\"}"),
   new UsageEvent("customer","api_calls",new BigDecimal("5"),Instant.parse("2026-11-02T00:00:00Z"),"b","{\"region\":\"us-east\",\"endpoint\":\"/v1\"}")));
  var service=new UsageService(repository,new ObjectMapper(),new PricingService(),mock(SubscriptionRepository.class),mock(PlanRepository.class));
  var result=service.aggregate("customer","api_calls",Instant.parse("2026-11-01T00:00:00Z"),Instant.parse("2026-12-01T00:00:00Z"),"sum",Map.of("region","ap-south"),"endpoint");
  assertEquals(0,new BigDecimal("3").compareTo((BigDecimal)result.get("value")));
  assertEquals(1L,result.get("matchedEventCount"));
 }
}
