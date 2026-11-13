package com.meterflow.metering;

import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.*; import java.math.*; import java.util.*; import java.util.stream.*;

@Service public class UsageService {
 private final UsageEventRepository repo; private final ObjectMapper mapper; private final com.meterflow.catalogue.PricingService pricing;
 public UsageService(UsageEventRepository r,ObjectMapper m,com.meterflow.catalogue.PricingService p){repo=r;mapper=m;pricing=p;}
 @Transactional public Map<String,Object> ingest(List<UsageController.EventInput> inputs){int accepted=0,duplicates=0; for(var e:inputs){if(repo.existsByIdempotencyKey(e.idempotencyKey())){duplicates++;continue;} try{String props=mapper.writeValueAsString(e.properties()==null?Map.of():e.properties());repo.save(new UsageEvent(e.customerId(),e.metric(),e.quantity(),e.timestamp(),e.idempotencyKey(),props));accepted++;}catch(Exception ex){throw new IllegalArgumentException("Could not serialize event properties");}} return Map.of("received",inputs.size(),"accepted",accepted,"duplicates",duplicates);}
 public Map<String,Object> summary(String customer){Instant start=LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();Instant end=Instant.now();var events=repo.findByCustomerIdAndTimestampBetween(customer,start,end);Map<String,BigDecimal> totals=events.stream().collect(Collectors.groupingBy(UsageEvent::getMetric,Collectors.reducing(BigDecimal.ZERO,UsageEvent::getQuantity,BigDecimal::add)));return Map.of("customerId",customer,"eventCount",events.size(),"usage",totals,"asOf",end);}
 public Map<String,Object> estimate(String customer){var s=summary(customer);return Map.of("customerId",customer,"eventCount",s.get("eventCount"),"usage",s.get("usage"),"estimatedAmount","0.00","currency","BDT","note","Estimate is a metering preview; attach an active subscription and configured plan for rated totals.");}
}
