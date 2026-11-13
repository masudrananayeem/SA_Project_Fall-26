package com.meterflow.catalogue.charges;
import java.math.BigDecimal;
public interface ChargeStrategy {
    String type();
    BigDecimal calculate(ChargeDefinition definition, BigDecimal quantity, BigDecimal seats);
}
