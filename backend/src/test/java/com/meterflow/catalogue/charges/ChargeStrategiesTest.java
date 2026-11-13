package com.meterflow.catalogue.charges;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ChargeStrategiesTest {
 private BigDecimal n(String v){return new BigDecimal(v);}
 @Test void packageRoundsUpToWholePackages(){var d=new ChargeDefinition("package","1k pack",n("25"),n("0"),n("1000"),List.of());assertEquals(n("50.00"),ChargeStrategies.calculate(List.of(d),n("1001"),n("0")));}
 @Test void perSeatUsesSeatCount(){var d=new ChargeDefinition("per_seat","Seat",n("120"),null,null,null);assertEquals(n("360.00"),ChargeStrategies.calculate(List.of(d),n("0"),n("3")));}
 @Test void graduatedTierPricesEachBandSeparately(){var d=new ChargeDefinition("graduated_tier","API",n("0"),null,null,List.of(new ChargeDefinition.Tier(n("100"),n("1")),new ChargeDefinition.Tier(n("200"),n("0.8")),new ChargeDefinition.Tier(null,n("0.5"))));assertEquals(n("205.00"),ChargeStrategies.calculate(List.of(d),n("250"),n("0")));}
 @Test void volumeTierUsesOneRateForAllUnits(){var d=new ChargeDefinition("volume_tier","API",n("0"),null,null,List.of(new ChargeDefinition.Tier(n("100"),n("1")),new ChargeDefinition.Tier(null,n("0.8"))));assertEquals(n("200.00"),ChargeStrategies.calculate(List.of(d),n("250"),n("0")));}
}
