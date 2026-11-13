package com.meterflow.catalogue;
import org.junit.jupiter.api.Test;import java.math.*;import static org.junit.jupiter.api.Assertions.*;
class PricingServiceTest {@Test void appliesFreeAllowanceAndMinimumCommitment(){var p=new Plan("t",1,"Test","BDT",new BigDecimal("100"),new BigDecimal("2"),"api_calls",new BigDecimal("10"),new BigDecimal("150"));assertEquals(new BigDecimal("180.00"),new PricingService().calculate(p,new BigDecimal("50")));assertEquals(new BigDecimal("150.00"),new PricingService().calculate(p,new BigDecimal("0")));}}
