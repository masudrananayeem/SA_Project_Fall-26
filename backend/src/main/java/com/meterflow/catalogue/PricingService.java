package com.meterflow.catalogue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.charges.*;
import org.springframework.stereotype.Service;
import java.math.*;
import java.util.*;
@Service
public class PricingService {
 private final ObjectMapper mapper;
 public PricingService(){this(new ObjectMapper());}\n public PricingService(ObjectMapper mapper){this.mapper=mapper;}
 public BigDecimal calculate(Plan p,BigDecimal quantity){
   BigDecimal total;
   if(p.getChargeDefinitionsJson()!=null && !p.getChargeDefinitionsJson().isBlank()){
     try {
       var definitions=mapper.readValue(p.getChargeDefinitionsJson(),new TypeReference<List<ChargeDefinition>>(){});
       total=ChargeStrategies.calculate(definitions,quantity,BigDecimal.ONE);
     } catch(Exception e){throw new IllegalStateException("Invalid charge definitions for plan "+p.getPlanKey(),e);}
   } else {
     BigDecimal billable=quantity.subtract(p.getFreeAllowance()).max(BigDecimal.ZERO);
     total=p.getFlatFee().add(billable.multiply(p.getUnitPrice()));
   }
   return total.max(p.getMinimumCommitment()).setScale(2,RoundingMode.HALF_UP);
 }
}
