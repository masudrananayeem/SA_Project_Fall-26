package com.meterflow.metering;

import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.*; import java.math.*; import java.util.*; import java.util.stream.*;

@Service public class UsageService {
 private final UsageEventRepository repo; private final ObjectMapper mapper; private final com.meterflow.catalogue.PricingService pricing; private final com.meterflow.subscriptions.SubscriptionRepository subscriptions; private final com.meterflow.catalogue.PlanRepository plans;
 public UsageService(UsageEventRepository r,ObjectMapper m,com.meterflow.catalogue.PricingService p,com.meterflow.subscriptions.SubscriptionRepository subscriptions,com.meterflow.catalogue.PlanRepository plans){repo=r;mapper=m;pricing=p;this.subscriptions=subscriptions;this.plans=plans;}
 @Transactional public Map<String,Object> ingest(List<UsageController.EventInput> inputs){int accepted=0,duplicates=0; for(var e:inputs){if(repo.existsByIdempotencyKey(e.idempotencyKey())){duplicates++;continue;} try{String props=mapper.writeValueAsString(e.properties()==null?Map.of():e.properties());repo.save(new UsageEvent(e.customerId(),e.metric(),e.quantity(),e.timestamp(),e.idempotencyKey(),props));accepted++;}catch(Exception ex){throw new IllegalArgumentException("Could not serialize event properties");}} return Map.of("received",inputs.size(),"accepted",accepted,"duplicates",duplicates);}
 public Map<String,Object> summary(String customer){Instant start=LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();Instant end=Instant.now();var events=repo.findByCustomerIdAndTimestampBetween(customer,start,end);Map<String,BigDecimal> totals=events.stream().collect(Collectors.groupingBy(UsageEvent::getMetric,Collectors.reducing(BigDecimal.ZERO,UsageEvent::getQuantity,BigDecimal::add)));return Map.of("customerId",customer,"eventCount",events.size(),"usage",totals,"asOf",end);}
 public Map<String,Object> estimate(String customer){
  var s=summary(customer); var active=subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(customer,"ACTIVE");
  if(active.isEmpty()) return Map.of("customerId",customer,"eventCount",s.get("eventCount"),"usage",s.get("usage"),"estimatedAmount","0.00","currency","BDT","note","No active subscription; estimate is usage-only.");
  var sub=active.get(); var plan=plans.findById(sub.getPlanId()).orElseThrow();
  var usage=repo.findByCustomerIdAndMetricAndTimestampBetween(customer,plan.getMetric(),sub.getPeriodStart().atStartOfDay(ZoneOffset.UTC).toInstant(),Instant.now()).stream().map(UsageEvent::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);
  var amount=pricing.calculate(plan,usage);
  return Map.of("customerId",customer,"eventCount",s.get("eventCount"),"usage",s.get("usage"),"billableMetric",plan.getMetric(),"billableQuantity",usage,"estimatedAmount",amount,"currency",plan.getCurrency(),"plan",plan.getPlanKey()+" v"+plan.getVersion(),"asOf",Instant.now());
 }
}
