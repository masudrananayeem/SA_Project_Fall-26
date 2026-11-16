package com.meterflow.subscriptions;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Plan-version migration lives in the subscriptions module so catalogue has no outgoing dependency. */
@RestController
@RequestMapping("/api/catalogue/plans")
public class PlanMigrationController {
    private final PlanChangeService service;

    public PlanMigrationController(PlanChangeService service) { this.service = service; }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{planKey}/migrate-active")
    public Map<String, Object> migrateActive(@PathVariable String planKey, @RequestParam int fromVersion,
                                             @RequestParam(required = false) Integer toVersion,
                                             @RequestParam(defaultValue = "false") boolean dryRun) {
        return service.migrate(planKey, fromVersion, toVersion, dryRun);
    }
}
