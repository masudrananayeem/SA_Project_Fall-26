package com.meterflow.catalogue;
import jakarta.persistence.*; import java.util.*; import java.math.*;
@Entity @Table(name="plans",uniqueConstraints=@UniqueConstraint(columnNames={"plan_key","version"}))
public class Plan {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(name="plan_key",nullable=false) private String planKey; @Column(nullable=false) private int version; @Column(nullable=false) private String name; @Column(nullable=false) private String currency; @Column(nullable=false) private boolean active=true;
 @Column(nullable=false,precision=18,scale=2) private BigDecimal flatFee=BigDecimal.ZERO; @Column(nullable=false,precision=18,scale=8) private BigDecimal unitPrice=BigDecimal.ZERO; @Column(nullable=false) private String metric="api_calls"; @Column(nullable=false,precision=18,scale=2) private BigDecimal freeAllowance=BigDecimal.ZERO; @Column(nullable=false,precision=18,scale=2) private BigDecimal minimumCommitment=BigDecimal.ZERO;
 @Column(name="charge_definitions_json", length=8000) private String chargeDefinitionsJson;
 protected Plan(){} public Plan(String k,int v,String n,String c,BigDecimal flat,BigDecimal unit,String metric,BigDecimal allowance,BigDecimal min){this(k,v,n,c,flat,unit,metric,allowance,min,null);}
 public Plan(String k,int v,String n,String c,BigDecimal flat,BigDecimal unit,String metric,BigDecimal allowance,BigDecimal min,String chargeJson){planKey=k;version=v;name=n;currency=c;flatFee=flat;unitPrice=unit;this.metric=metric;freeAllowance=allowance;minimumCommitment=min;chargeDefinitionsJson=chargeJson;}
 public UUID getId(){return id;} public String getPlanKey(){return planKey;} public int getVersion(){return version;} public String getName(){return name;} public String getCurrency(){return currency;} public BigDecimal getFlatFee(){return flatFee;} public BigDecimal getUnitPrice(){return unitPrice;} public String getMetric(){return metric;} public BigDecimal getFreeAllowance(){return freeAllowance;} public BigDecimal getMinimumCommitment(){return minimumCommitment;} public boolean isActive(){return active;} public String getChargeDefinitionsJson(){return chargeDefinitionsJson;}
}
