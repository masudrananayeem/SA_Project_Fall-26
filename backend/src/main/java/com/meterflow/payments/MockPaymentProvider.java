package com.meterflow.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock payment gateway (real gateways are out of scope). Charges succeed unless the customer is
 * configured to fail, either through meterflow.payments.mock.failing-customers or at runtime via
 * POST /api/payments/mock/customers/{customerId}/outcome.
 */
@Component
public class MockPaymentProvider {
    public record Result(boolean success, String reference, String message) { }

    private final Map<String, Boolean> outcomes = new ConcurrentHashMap<>();

    public MockPaymentProvider(@Value("${meterflow.payments.mock.failing-customers:}") String failingCustomers) {
        for (String customer : failingCustomers.split(",")) if (!customer.isBlank()) outcomes.put(customer.trim(), false);
    }

    public Result charge(String customerId, UUID invoiceId, BigDecimal amount, String currency) {
        if (Boolean.FALSE.equals(outcomes.get(customerId)))
            return new Result(false, null, "Card declined (mock provider)");
        return new Result(true, "mock_" + UUID.randomUUID(), "Charged " + amount.toPlainString() + " " + currency);
    }

    public void setOutcome(String customerId, boolean succeed) { outcomes.put(customerId, succeed); }

    public Map<String, Boolean> outcomes() { return Map.copyOf(outcomes); }
}
