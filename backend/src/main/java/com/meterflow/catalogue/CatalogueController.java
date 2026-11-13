package com.meterflow.catalogue;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*; import org.springframework.security.access.prepost.PreAuthorize; import java.math.*; import java.util.*;
@RestController @RequestMapping("/api/catalogue/plans") public class CatalogueController {
 private final PlanRepository repo; public CatalogueController(PlanRepository r){repo=r;}
 public record PlanInput(@NotBlank String planKey,@NotBlank String name,@Pattern(regexp="BDT|USD") String currency,@NotNull @DecimalMin("0") BigDecimal flatFee,@NotNull @DecimalMin("0") BigDecimal unitPrice,@NotBlank String metric,@NotNull @DecimalMin("0") BigDecimal freeAllowance,@NotNull @DecimalMin("0") BigDecimal minimumCommitment){}
 @GetMapping public List<Plan> all(){return repo.findAllByOrderByPlanKeyAscVersionDesc();}
 @PreAuthorize("hasRole('ADMIN')")
 @PostMapping public Plan create(@Valid @RequestBody PlanInput in){int version=repo.findTopByPlanKeyOrderByVersionDesc(in.planKey()).map(p->p.getVersion()+1).orElse(1);return repo.save(new Plan(in.planKey(),version,in.name(),in.currency(),in.flatFee(),in.unitPrice(),in.metric(),in.freeAllowance(),in.minimumCommitment()));}
}
