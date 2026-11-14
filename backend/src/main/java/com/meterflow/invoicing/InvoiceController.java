package com.meterflow.invoicing;
import org.springframework.web.bind.annotation.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.Authentication; import java.util.*;
@RestController @RequestMapping("/api/invoices") public class InvoiceController {
 private final InvoiceRepository repo; private final InvoiceLineRepository lines; private final BillingService billing; private final com.meterflow.notifications.WebhookDeliveryService webhooks; private final LateAdjustmentBillingService lateBilling;
 public InvoiceController(InvoiceRepository r,InvoiceLineRepository l,BillingService b,com.meterflow.notifications.WebhookDeliveryService w,LateAdjustmentBillingService lb){repo=r;lines=l;billing=b;webhooks=w;lateBilling=lb;}
 @GetMapping public List<Invoice> list(@RequestParam(required=false) String customerId, Authentication auth){boolean admin=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));if(!admin && (customerId==null || !auth.getName().equals(customerId))) throw new org.springframework.security.access.AccessDeniedException("Customers may only view their own invoices.");return customerId==null?repo.findAll():repo.findByCustomerIdOrderByCreatedAtDesc(customerId);}
 @GetMapping("/{id}/lines") public List<InvoiceLine> lines(@PathVariable UUID id, Authentication auth){var invoice=repo.findById(id).orElseThrow();boolean admin=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));if(!admin && !auth.getName().equals(invoice.getCustomerId())) throw new org.springframework.security.access.AccessDeniedException("Customers may only view their own invoice lines.");return lines.findByInvoiceId(id);}
 @PreAuthorize("hasRole('ADMIN')") @GetMapping("/simulate") public Map<String,Object> simulate(@RequestParam String customerId,@RequestParam String period){return billing.simulate(customerId,period);}
 @PreAuthorize("hasRole('ADMIN')") @PostMapping("/late-adjustments/run") public InvoiceAdjustment runLateAdjustment(@RequestParam String customerId,@RequestParam String period){return lateBilling.bill(customerId,period);}
 @PreAuthorize("hasRole('ADMIN')")
 @PostMapping("/run") public Map<String,Object> run(@RequestParam String period){return billing.run(period);}
 @PreAuthorize("hasRole('ADMIN')")
 @PostMapping("/{id}/finalise") public Invoice finalise(@PathVariable UUID id){var i=repo.findById(id).orElseThrow();i.finalise();var saved=repo.save(i);webhooks.publish(saved.getCustomerId(),"invoice.finalised",Map.of("invoiceId",saved.getId().toString(),"period",saved.getPeriod(),"total",saved.getTotal(),"currency",saved.getCurrency()));return saved;}
}
