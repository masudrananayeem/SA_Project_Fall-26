package com.meterflow.catalogue;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*; import org.springframework.security.access.prepost.PreAuthorize; import java.math.*; import java.util.*; import com.fasterxml.jackson.databind.ObjectMapper; import com.meterflow.catalogue.charges.*;
@RestController @RequestMapping("/api/catalogue/plans") public class CatalogueController {
 private final PlanRepository repo; private final ObjectMapper mapper; private final com.meterflow.subscriptions.SubscriptionRepository subscriptions; private final com.meterflow.subscriptions.PlanChangeRepository changes; public CatalogueController(PlanRepository r,ObjectMapper m,com.meterflow.subscriptions.SubscriptionRepository sr,com.meterflow.subscriptions.PlanChangeRepository cr){repo=r;mapper=m;subscriptions=sr;changes=cr;}
 public record PlanInput(@NotBlank String planKey,@NotBlank String name,@Pattern(regexp="BDT|USD") String currency,@NotNull @DecimalMin("0") BigDecimal flatFee,@NotNull @DecimalMin("0") BigDecimal unitPrice,@NotBlank String metric,@NotNull @DecimalMin("0") BigDecimal freeAllowance,@NotNull @DecimalMin("0") BigDecimal minimumCommitment,List<@Valid ChargeDefinition> charges,BigDecimal taxRate){}
 @GetMapping public List<Plan> all(){return repo.findAllByOrderByPlanKeyAscVersionDesc();}
 @PreAuthorize("hasRole('ADMIN')")
 @PostMapping public Plan create(@Valid @RequestBody PlanInput in){int version=repo.findTopByPlanKeyOrderByVersionDesc(in.planKey()).map(p->p.getVersion()+1).orElse(1);try {
   String chargeJson=null;
   if(in.charges()!=null&&!in.charges().isEmpty()){
     ChargeStrategies.calculate(in.charges(),BigDecimal.ZERO,BigDecimal.ONE);
     chargeJson=mapper.writeValueAsString(in.charges());
   }
   var plan=new Plan(in.planKey(),version,in.name(),in.currency(),in.flatFee(),in.unitPrice(),in.metric(),in.freeAllowance(),in.minimumCommitment(),chargeJson); if(in.taxRate()!=null)plan.setTaxRate(in.taxRate()); return repo.save(plan);
  } catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Invalid charge configuration");}}
 @PreAuthorize("hasRole('ADMIN')") @PostMapping("/{planKey}/migrate-active") public Map<String,Object> migrateActive(@PathVariable String planKey,@RequestParam int fromVersion){var from=repo.findByPlanKeyAndVersion(planKey,fromVersion).orElseThrow(()->new IllegalArgumentException("Source plan version not found"));var target=repo.findTopByPlanKeyOrderByVersionDesc(planKey).orElseThrow();if(target.getVersion()==fromVersion)throw new IllegalArgumentException("No newer plan version exists");if(!from.getCurrency().equals(target.getCurrency()))throw new IllegalArgumentException("Migration currencies must match");int migrated=0;for(var sub:subscriptions.findByStatus("ACTIVE")){if(sub.getPlanId().equals(from.getId())){sub.changePlan(target.getId());subscriptions.save(sub);changes.save(new com.meterflow.subscriptions.PlanChange(sub.getId(),from.getId(),target.getId(),BigDecimal.ZERO,target.getCurrency(),java.time.LocalDate.now(java.time.ZoneOffset.UTC)));migrated++;}}return Map.of("planKey",planKey,"fromVersion",fromVersion,"toVersion",target.getVersion(),"migratedSubscriptions",migrated,"proration","No price delta posted; migration is an administrative version migration");}

}
