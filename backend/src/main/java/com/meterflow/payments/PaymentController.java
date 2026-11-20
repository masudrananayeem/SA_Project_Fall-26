package com.meterflow.payments;

import com.meterflow.invoicing.InvoiceRepository;
import com.meterflow.invoicing.UpgradeChargeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments/mock")
public class PaymentController {
    private final InvoiceRepository invoices;
    private final PaymentService payments;
    private final MockPaymentProvider provider;
    private final UpgradeChargeRepository upgradeCharges;

    public PaymentController(InvoiceRepository invoices, PaymentService payments, MockPaymentProvider provider,
                             UpgradeChargeRepository upgradeCharges) {
        this.invoices = invoices;
        this.payments = payments;
        this.provider = provider;
        this.upgradeCharges = upgradeCharges;
    }

    /** Pay the balance due after a mid-month plan upgrade (customer: own charge; admin: any). */
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @PostMapping("/upgrade-charges/{chargeId}/pay")
    public Map<String, Object> payUpgrade(@PathVariable UUID chargeId, @RequestParam(required = false) String method, Authentication auth) {
        var charge = upgradeCharges.findById(chargeId).orElseThrow(() -> new NoSuchElementException("Charge not found"));
        if (!isAdmin(auth) && !auth.getName().equals(charge.getCustomerId()))
            throw new AccessDeniedException("Customers may only pay their own charges");
        return payments.payUpgradeCharge(chargeId, method == null ? (isAdmin(auth) ? "admin" : "card") : method);
    }

    /** Customer pays an open invoice (mock checkout: method card, wallet or bank); admin "mark as paid". */
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @PostMapping("/{invoiceId}/pay")
    public Map<String, Object> pay(@PathVariable UUID invoiceId, @RequestParam(required = false) String method, Authentication auth) {
        guard(invoiceId, auth);
        boolean admin = isAdmin(auth);
        return payments.recordSuccess(invoiceId, method == null ? (admin ? "admin" : "card") : method);
    }

    public record TopUpInput(String customerId, @NotNull @DecimalMin("1") BigDecimal amount, String currency, String method) { }

    /** Buy prepaid credit (customer: own account; admin: any customer). Applied to the next invoices. */
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @PostMapping("/top-up")
    public Map<String, Object> topUp(@Valid @RequestBody TopUpInput in, Authentication auth) {
        String customer = isAdmin(auth) ? in.customerId() : auth.getName();
        if (customer == null || customer.isBlank()) throw new IllegalArgumentException("customerId is required");
        if (!isAdmin(auth) && in.customerId() != null && !in.customerId().equals(auth.getName()))
            throw new AccessDeniedException("Customers may only top up their own account");
        return payments.topUp(customer, in.amount(), in.currency(), in.method());
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

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN"));
    }

    private void guard(UUID invoiceId, Authentication auth) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new NoSuchElementException("Invoice not found"));
        if (auth.getAuthorities().stream().noneMatch(x -> x.getAuthority().equals("ROLE_ADMIN")) && !auth.getName().equals(invoice.getCustomerId()))
            throw new AccessDeniedException("Customers may only pay their own invoices");
    }
}
