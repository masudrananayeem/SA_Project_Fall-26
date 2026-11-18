package com.meterflow.invoicing;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/invoices/prepaid")
public class PrepaidController {
    private final PrepaidService prepaid;

    public PrepaidController(PrepaidService prepaid) { this.prepaid = prepaid; }

    /** Runs the daily prepaid renewal now (normally 00:10 UTC). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/renew")
    public Map<String, Object> renew() { return prepaid.renew(); }
}
