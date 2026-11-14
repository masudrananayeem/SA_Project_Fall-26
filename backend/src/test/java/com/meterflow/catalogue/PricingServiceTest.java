package com.meterflow.catalogue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.charges.ChargeDefinition;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PricingServiceTest {
 @Test void appliesFreeAllowanceAndMinimumCommitment(){
  var p=new Plan("t",1,"Test","BDT",new BigDecimal("100"),new BigDecimal("2"),"api_calls",new BigDecimal("10"),new BigDecimal("150"));
  assertEquals(new BigDecimal("180.00"),new PricingService().calculate(p,new BigDecimal("50")));
  assertEquals(new BigDecimal("150.00"),new PricingService().calculate(p,new BigDecimal("0")));
 }
 @Test void evaluatesConfiguredGraduatedAndPackageCharges() throws Exception {
  var mapper=new ObjectMapper();
  var tiers=List.of(new ChargeDefinition.Tier(new BigDecimal("100"),new BigDecimal("1")),new ChargeDefinition.Tier(null,new BigDecimal("2")));
  var definitions=List.of(
   new ChargeDefinition("graduated_tier","API usage",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ONE,tiers),
   new ChargeDefinition("package","Storage packs",new BigDecimal("5"),BigDecimal.ZERO,new BigDecimal("10"),List.of())
  );
  var plan=new Plan("configured",1,"Configured","BDT",BigDecimal.ZERO,BigDecimal.ZERO,"api_calls",BigDecimal.ZERO,BigDecimal.ZERO,mapper.writeValueAsString(definitions));
  assertEquals(new BigDecimal("200.00"),new PricingService(mapper).calculate(plan,new BigDecimal("120")));
 }
 @Test void calculatesPerSeatChargeUsingSeatQuantity() throws Exception {
  var mapper=new ObjectMapper();var defs=List.of(new ChargeDefinition("per_seat","Seats",new BigDecimal("12"),BigDecimal.ZERO,BigDecimal.ONE,List.of()));
  var plan=new Plan("seat",1,"Seat Plan","BDT",BigDecimal.ZERO,BigDecimal.ZERO,"api_calls",BigDecimal.ZERO,BigDecimal.ZERO,mapper.writeValueAsString(defs));
  assertEquals(new BigDecimal("60.00"),new PricingService(mapper).calculate(plan,BigDecimal.ZERO,new BigDecimal("5")));
 }

 @Test void rejectsUnknownChargeTypes() {
  var definitions=List.of(new ChargeDefinition("mystery","Unknown",BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ONE,List.of()));
  assertThrows(IllegalArgumentException.class,()->com.meterflow.catalogue.charges.ChargeStrategies.calculate(definitions,BigDecimal.ONE,BigDecimal.ONE));
 }
}
