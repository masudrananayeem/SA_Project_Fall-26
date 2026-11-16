package com.meterflow.payments;

import com.meterflow.invoicing.InvoiceRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments/mock")
public class PaymentController {
    private final InvoiceRepository invoices;
    private final PaymentService payments;
    private final MockPaymentProvider provider;

    public PaymentController(InvoiceRepository invoices, PaymentService payments, MockPaymentProvider provider) {
        this.invoices = invoices;
        this.payments = payments;
        this.provider = provider;
    }

    /** Customer pays an open invoice (mock checkout). */
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @PostMapping("/{invoiceId}/pay")
    public Map<String, Object> pay(@PathVariable UUID invoiceId, Authentication auth) {
        guard(invoiceId, auth);
        return payments.recordSuccess(invoiceId);
    }

    /** Simulates a declined payment for an invoice (starts dunning). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{invoiceId}/fail")
    public Map<String, Object> fail(@PathVariable UUID invoiceId) {
        var c = payments.recordFailure(invoiceId);
        return Map.of("invoiceId", invoiceId, "paymentStatus", "FAILED", "dunningCaseId", c.getId(),
                "nextAttemptAt", String.valueOf(c.getNextAttemptAt()));
    }

    /** Attempts collection now through the mock provider. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{invoiceId}/collect")
    public Map<String, Object> collect(@PathVariable UUID invoiceId) { return payments.collect(invoiceId); }

    /** Demo control: make the mock provider decline (succeed=false) or accept a customer's charges. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/customers/{customerId}/outcome")
    public Map<String, Object> outcome(@PathVariable String customerId, @RequestParam boolean succeed) {
        provider.setOutcome(customerId, succeed);
        return Map.of("customerId", customerId, "chargesSucceed", succeed);
    }

    private void guard(UUID invoiceId, Authentication auth) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new NoSuchElementException("Invoice not found"));
        if (auth.getAuthorities().stream().noneMatch(x -> x.getAuthority().equals("ROLE_ADMIN")) && !auth.getName().equals(invoice.getCustomerId()))
            throw new AccessDeniedException("Customers may only pay their own invoices");
    }
}
