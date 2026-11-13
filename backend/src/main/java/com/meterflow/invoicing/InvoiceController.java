package com.meterflow.invoicing;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/invoices") public class InvoiceController {
 private final InvoiceRepository repo; private final InvoiceLineRepository lines; private final BillingService billing;
 public InvoiceController(InvoiceRepository r,InvoiceLineRepository l,BillingService b){repo=r;lines=l;billing=b;}
 @GetMapping public List<Invoice> list(@RequestParam(required=false) String customerId){return customerId==null?repo.findAll():repo.findByCustomerIdOrderByCreatedAtDesc(customerId);}
 @GetMapping("/{id}/lines") public List<InvoiceLine> lines(@PathVariable UUID id){return lines.findByInvoiceId(id);}
 @PostMapping("/run") public Map<String,Object> run(@RequestParam String period){return billing.run(period);}
 @PostMapping("/{id}/finalise") public Invoice finalise(@PathVariable UUID id){var i=repo.findById(id).orElseThrow();i.finalise();return repo.save(i);}
}
