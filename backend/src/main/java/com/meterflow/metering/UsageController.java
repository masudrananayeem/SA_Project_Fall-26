package com.meterflow.metering;

import jakarta.validation.Valid; import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*; import org.springframework.http.*;
import java.math.BigDecimal; import java.time.Instant; import java.util.*;

@RestController @RequestMapping("/api/usage")
public class UsageController {
 private final UsageService service; public UsageController(UsageService s){service=s;}
 public record EventInput(@NotBlank @Size(max=120) String customerId,@NotBlank @Size(max=120) String metric,@NotNull @DecimalMin("0.00000001") @Digits(integer=16,fraction=8) BigDecimal quantity,@NotNull Instant timestamp,@NotBlank @Size(max=180) String idempotencyKey,Map<String,Object> properties){}
 public record BatchInput(@NotEmpty @Size(max=1000) List<@Valid EventInput> events){}
 @PostMapping("/events:batch") public Map<String,Object> ingest(@Valid @RequestBody BatchInput input){return service.ingest(input.events());}
 @GetMapping("/summary") public Map<String,Object> summary(@RequestParam String customerId){return service.summary(customerId);}
 @GetMapping("/estimate") public Map<String,Object> estimate(@RequestParam String customerId){return service.estimate(customerId);}
}
