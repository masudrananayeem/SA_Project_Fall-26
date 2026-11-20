package com.meterflow.invoicing;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Balances due after a mid-month upgrade. Admin: all (or one customer); customer: own only. */
@RestController
@RequestMapping("/api/billing/upgrade-charges")
public class UpgradeChargeController {
    private final UpgradeChargeService service;

    public UpgradeChargeController(UpgradeChargeService service) { this.service = service; }

    /** Cost of changing plan now, shown to the customer before confirming. */
    @GetMapping("/preview")
    public Map<String, Object> preview(@RequestParam UUID subscriptionId, @RequestParam UUID planId, Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return service.preview(subscriptionId, planId, auth.getName(), admin);
    }

    @GetMapping
    public List<UpgradeCharge> list(@RequestParam(required = false) String customerId, Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (admin) return customerId == null || customerId.isBlank() ? service.recent() : service.forCustomer(customerId);
        if (customerId != null && !customerId.equals(auth.getName()))
            throw new AccessDeniedException("Customers may only see their own charges");
        return service.forCustomer(auth.getName());
    }
}
