package com.meterflow.catalogue;
import org.springframework.stereotype.Service; import java.math.*;
@Service public class PricingService {
 private final Charge usageCharge=new PerUnitCharge();
 public BigDecimal calculate(Plan p,BigDecimal quantity){BigDecimal billable=quantity.subtract(p.getFreeAllowance()).max(BigDecimal.ZERO);BigDecimal usage=usageCharge.calculate(billable,p);BigDecimal total=p.getFlatFee().add(usage);return total.max(p.getMinimumCommitment()).setScale(2,RoundingMode.HALF_UP);}
}
