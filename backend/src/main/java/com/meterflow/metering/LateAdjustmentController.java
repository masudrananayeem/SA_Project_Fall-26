package com.meterflow.metering;
import org.springframework.web.bind.annotation.*;import org.springframework.security.access.prepost.PreAuthorize;import java.util.*;
@RestController @RequestMapping("/api/late-adjustments") public class LateAdjustmentController {private final LateUsageAdjustmentRepository repo;public LateAdjustmentController(LateUsageAdjustmentRepository r){repo=r;}
 @GetMapping @PreAuthorize("hasRole('ADMIN')") public List<LateUsageAdjustment> list(@RequestParam String customerId,@RequestParam String period){return repo.findByCustomerIdAndBillingPeriodAndStatus(customerId,period,"PENDING");}
}
