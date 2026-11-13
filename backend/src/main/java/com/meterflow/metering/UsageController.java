package com.meterflow.metering;

import jakarta.validation.Valid; import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*; import org.springframework.http.*; import org.springframework.security.core.Authentication;
import java.math.BigDecimal; import java.time.Instant; import java.util.*;

@RestController @RequestMapping("/api/usage")
public class UsageController {
 private final UsageService service; public UsageController(UsageService s){service=s;}
 public record EventInput(@NotBlank @Size(max=120) String customerId,@NotBlank @Size(max=120) String metric,@NotNull @DecimalMin("0.00000001") @Digits(integer=16,fraction=8) BigDecimal quantity,@NotNull Instant timestamp,@NotBlank @Size(max=180) String idempotencyKey,Map<String,Object> properties){}
 public record BatchInput(@NotEmpty @Size(max=1000) List<@Valid EventInput> events){}
 @PostMapping("/events:batch") public Map<String,Object> ingest(@Valid @RequestBody BatchInput input, Authentication auth){boolean admin=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));if(!admin && input.events().stream().anyMatch(e->!auth.getName().equals(e.customerId()))) throw new org.springframework.security.access.AccessDeniedException("Customers may only ingest events for their own customer ID.");return service.ingest(input.events());}
 @GetMapping("/summary") public Map<String,Object> summary(@RequestParam String customerId, Authentication auth){authorizeCustomer(customerId, auth); return service.summary(customerId);}
 @GetMapping("/estimate") public Map<String,Object> estimate(@RequestParam String customerId, Authentication auth){authorizeCustomer(customerId, auth); return service.estimate(customerId);}
 private void authorizeCustomer(String customerId, Authentication auth){if(auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")) && !auth.getName().equals(customerId)) throw new org.springframework.security.access.AccessDeniedException("Customers may only view their own usage.");}
}
