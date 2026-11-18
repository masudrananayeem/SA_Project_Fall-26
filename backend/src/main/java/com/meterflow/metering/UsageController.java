package com.meterflow.metering;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usage")
public class UsageController {
    private static final TypeReference<Map<String, String>> FILTERS = new TypeReference<>() { };

    private final UsageService service;
    private final LiveSpendService liveSpend;
    private final ObjectMapper mapper;

    public UsageController(UsageService service, LiveSpendService liveSpend, ObjectMapper mapper) {
        this.service = service;
        this.liveSpend = liveSpend;
        this.mapper = mapper;
    }

    public record EventInput(@NotBlank @Size(max = 120) String customerId, @NotBlank @Size(max = 120) String metric,
                             @NotNull @DecimalMin("0.00000001") @Digits(integer = 16, fraction = 8) BigDecimal quantity,
                             @NotNull Instant timestamp, @NotBlank @Size(max = 180) String idempotencyKey,
                             Map<String, Object> properties) { }

    public record BatchInput(@NotEmpty @Size(max = 1000) List<@Valid EventInput> events) { }

    @PostMapping("/events:batch")
    public Map<String, Object> ingest(@Valid @RequestBody BatchInput input, Authentication auth) {
        if (!isAdmin(auth) && input.events().stream().anyMatch(e -> !auth.getName().equals(e.customerId())))
            throw new AccessDeniedException("Customers may only ingest events for their own customer ID.");
        return service.ingest(input.events());
    }

    @GetMapping("/aggregate")
    public Map<String, Object> aggregate(@RequestParam String customerId, @RequestParam String metric, @RequestParam Instant from,
                                         @RequestParam Instant to, @RequestParam(defaultValue = "sum") String operation,
                                         @RequestParam(required = false) String distinctProperty,
                                         @RequestParam(required = false) String filters, Authentication auth) {
        authorizeCustomer(customerId, auth);
        Map<String, String> parsed = Map.of();
        if (filters != null && !filters.isBlank()) {
            try {
                parsed = mapper.readValue(filters, FILTERS);
            } catch (Exception e) {
                throw new IllegalArgumentException("filters must be a JSON object of string values");
            }
        }
        return service.aggregate(customerId, metric, from, to, operation, parsed, distinctProperty);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam String customerId, Authentication auth) {
        authorizeCustomer(customerId, auth);
        return service.summary(customerId);
    }

    /** Live spend for the current (or a given) period: usage, charge lines, tax and estimated total. */
    @GetMapping("/estimate")
    public Map<String, Object> estimate(@RequestParam String customerId, @RequestParam(required = false) String period,
                                        Authentication auth) {
        authorizeCustomer(customerId, auth);
        return liveSpend.cachedEstimate(customerId, period == null || period.isBlank() ? LiveSpendService.currentPeriod() : period);
    }

    /**
     * What the customer is using: per metric, per property value (endpoint, region, ...), per day,
     * the latest events, and whether usage is currently accepted. Defaults to the current month.
     */
    @GetMapping("/breakdown")
    public Map<String, Object> breakdown(@RequestParam String customerId, @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to, Authentication auth) {
        authorizeCustomer(customerId, auth);
        Instant end = to == null ? Instant.now().plusSeconds(60) : to;
        Instant start = from == null ? java.time.LocalDate.now(java.time.ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant() : from;
        return service.breakdown(customerId, start, end);
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private void authorizeCustomer(String customerId, Authentication auth) {
        if (!isAdmin(auth) && !auth.getName().equals(customerId))
            throw new AccessDeniedException("Customers may only view their own usage.");
    }
}
