package com.meterflow.invoicing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
    private final InvoiceRepository repo;
    private final InvoiceLineRepository lines;
    private final BillingService billing;

    public InvoiceController(InvoiceRepository repo, InvoiceLineRepository lines, BillingService billing) {
        this.repo = repo;
        this.lines = lines;
        this.billing = billing;
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private static void guard(String customerId, Authentication auth) {
        if (!isAdmin(auth) && (customerId == null || !auth.getName().equals(customerId)))
            throw new AccessDeniedException("Customers may only view their own invoices.");
    }

    @GetMapping
    public List<Invoice> list(@RequestParam(required = false) String customerId, Authentication auth) {
        guard(customerId, auth);
        return customerId == null ? repo.findAll(Sort.by(Sort.Direction.DESC, "createdAt")) : repo.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @GetMapping("/page")
    public Page<Invoice> page(@RequestParam(required = false) String customerId, @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size, Authentication auth) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be >= 0 and size must be 1..100");
        guard(customerId, auth);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return customerId == null ? repo.findAll(pageable) : repo.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    @GetMapping("/{id}")
    public Invoice get(@PathVariable UUID id, Authentication auth) {
        var invoice = repo.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("Invoice not found"));
        guard(invoice.getCustomerId(), auth);
        return invoice;
    }

    @GetMapping("/{id}/lines")
    public List<InvoiceLine> lines(@PathVariable UUID id, Authentication auth) {
        guard(get(id, auth).getCustomerId(), auth);
        return lines.findByInvoiceId(id);
    }

    /** Rendered invoice (Visitor): format=text (default) or csv. */
    @GetMapping("/{id}/render")
    public ResponseEntity<String> render(@PathVariable UUID id, @RequestParam(defaultValue = "text") String format, Authentication auth) {
        guard(get(id, auth).getCustomerId(), auth);
        var document = billing.document(id);
        boolean csv = "csv".equalsIgnoreCase(format);
        return ResponseEntity.ok()
                .contentType(csv ? MediaType.parseMediaType("text/csv") : MediaType.TEXT_PLAIN)
                .body(csv ? InvoiceRenderers.csv(document) : InvoiceRenderers.text(document));
    }

    /** QA5: trace one line back to its events and price rule in a single call. */
    @GetMapping("/lines/{lineId}/trace")
    public Map<String, Object> trace(@PathVariable UUID lineId, Authentication auth) {
        var result = billing.trace(lineId);
        guard((String) result.get("customerId"), auth);
        return result;
    }

    /** FR10 simulator; period defaults to last month, planId to the subscription's own plan version. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/simulate")
    public Map<String, Object> simulate(@RequestParam String customerId, @RequestParam(required = false) String period,
                                        @RequestParam(required = false) UUID planId) {
        return billing.simulate(customerId, period, planId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/simulate/versions")
    public Map<String, Object> simulateVersions(@RequestParam String customerId, @RequestParam(required = false) String period) {
        return billing.compareVersions(customerId, period);
    }

    /** Sequential run (small data sets). The partitioned, restartable job is POST /api/invoices/batch/run. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/run")
    public Map<String, Object> run(@RequestParam String period) { return billing.run(period); }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/run/customer")
    public Map<String, Object> runCustomer(@RequestParam String customerId, @RequestParam String period) {
        return billing.runCustomer(period, customerId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/finalise")
    public Invoice finalise(@PathVariable UUID id) { return billing.finalise(id); }
}
