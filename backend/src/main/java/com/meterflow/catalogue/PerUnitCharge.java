package com.meterflow.catalogue;
import java.math.*;
public class PerUnitCharge implements Charge { public BigDecimal calculate(BigDecimal quantity,Plan plan){return quantity.multiply(plan.getUnitPrice()).setScale(2,RoundingMode.HALF_UP);} }
