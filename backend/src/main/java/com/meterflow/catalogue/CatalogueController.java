package com.meterflow.catalogue;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*; import org.springframework.security.access.prepost.PreAuthorize; import java.math.*; import java.util.*; import com.fasterxml.jackson.databind.ObjectMapper; import com.meterflow.catalogue.charges.*;
@RestController @RequestMapping("/api/catalogue/plans") public class CatalogueController {
 private final PlanRepository repo; private final ObjectMapper mapper; public CatalogueController(PlanRepository r,ObjectMapper m){repo=r;mapper=m;}
 public record PlanInput(@NotBlank String planKey,@NotBlank String name,@Pattern(regexp="BDT|USD") String currency,@NotNull @DecimalMin("0") BigDecimal flatFee,@NotNull @DecimalMin("0") BigDecimal unitPrice,@NotBlank String metric,@NotNull @DecimalMin("0") BigDecimal freeAllowance,@NotNull @DecimalMin("0") BigDecimal minimumCommitment,List<@Valid ChargeDefinition> charges){}
 @GetMapping public List<Plan> all(){return repo.findAllByOrderByPlanKeyAscVersionDesc();}
 @PreAuthorize("hasRole('ADMIN')")
 @PostMapping public Plan create(@Valid @RequestBody PlanInput in){int version=repo.findTopByPlanKeyOrderByVersionDesc(in.planKey()).map(p->p.getVersion()+1).orElse(1);try {
   String chargeJson=null;
   if(in.charges()!=null&&!in.charges().isEmpty()){
     ChargeStrategies.calculate(in.charges(),BigDecimal.ZERO,BigDecimal.ONE);
     chargeJson=mapper.writeValueAsString(in.charges());
   }
   return repo.save(new Plan(in.planKey(),version,in.name(),in.currency(),in.flatFee(),in.unitPrice(),in.metric(),in.freeAllowance(),in.minimumCommitment(),chargeJson));
  } catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Invalid charge configuration");}}
}
