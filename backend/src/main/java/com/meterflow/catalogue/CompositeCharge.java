package com.meterflow.catalogue;
import java.math.*; import java.util.*;
public class CompositeCharge implements Charge {private final List<Charge> charges; public CompositeCharge(List<Charge> c){charges=List.copyOf(c);} public BigDecimal calculate(BigDecimal q,Plan p){return charges.stream().map(x->x.calculate(q,p)).reduce(BigDecimal.ZERO,BigDecimal::add);} }
