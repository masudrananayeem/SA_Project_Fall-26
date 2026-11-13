package com.meterflow.catalogue;
import java.math.BigDecimal;
public interface Charge { BigDecimal calculate(BigDecimal quantity, Plan plan); }
