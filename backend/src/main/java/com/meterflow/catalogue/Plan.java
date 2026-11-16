package com.meterflow.catalogue;
import jakarta.persistence.*; import java.util.*; import java.math.*;
@Entity @Table(name="plans",uniqueConstraints=@UniqueConstraint(columnNames={"plan_key","version"}))
public class Plan {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(name="plan_key",nullable=false) private String planKey; @Column(nullable=false) private int version; @Column(nullable=false) private String name; @Column(nullable=false) private String currency; @Column(nullable=false) private boolean active=true;
 @Column(nullable=false,precision=18,scale=2) private BigDecimal flatFee=BigDecimal.ZERO; @Column(nullable=false,precision=18,scale=8) private BigDecimal unitPrice=BigDecimal.ZERO; @Column(nullable=false) private String metric="api_calls"; @Column(nullable=false,precision=18,scale=2) private BigDecimal freeAllowance=BigDecimal.ZERO; @Column(nullable=false,precision=18,scale=2) private BigDecimal minimumCommitment=BigDecimal.ZERO;
 @Column(name="charge_definitions_json", length=8000) private String chargeDefinitionsJson;
 @org.hibernate.annotations.ColumnDefault("0") @Column(nullable=false,precision=7,scale=4) private BigDecimal taxRate=BigDecimal.ZERO;
 /** FR2 billable metric definition: aggregation over the plan's metric, optional property and filters. */
 @org.hibernate.annotations.ColumnDefault("'sum'") @Column(nullable=false,length=40) private String aggregation="sum";
 @Column(name="aggregation_property",length=255) private String aggregationProperty;
 @Column(name="metric_filters_json",length=2000) private String metricFiltersJson;
 protected Plan(){} public Plan(String k,int v,String n,String c,BigDecimal flat,BigDecimal unit,String metric,BigDecimal allowance,BigDecimal min){this(k,v,n,c,flat,unit,metric,allowance,min,null);}
 public Plan(String k,int v,String n,String c,BigDecimal flat,BigDecimal unit,String metric,BigDecimal allowance,BigDecimal min,String chargeJson){planKey=k;version=v;name=n;currency=c;flatFee=flat;unitPrice=unit;this.metric=metric;freeAllowance=allowance;minimumCommitment=min;chargeDefinitionsJson=chargeJson;taxRate=BigDecimal.ZERO;}
 public UUID getId(){return id;} public String getPlanKey(){return planKey;} public int getVersion(){return version;} public String getName(){return name;} public String getCurrency(){return currency;} public BigDecimal getFlatFee(){return flatFee;} public BigDecimal getUnitPrice(){return unitPrice;} public String getMetric(){return metric;} public BigDecimal getFreeAllowance(){return freeAllowance;} public BigDecimal getMinimumCommitment(){return minimumCommitment;} public boolean isActive(){return active;} public String getChargeDefinitionsJson(){return chargeDefinitionsJson;} public BigDecimal getTaxRate(){return taxRate;}
 public String getAggregation(){return aggregation==null?"sum":aggregation;} public String getAggregationProperty(){return aggregationProperty;} public String getMetricFiltersJson(){return metricFiltersJson;}
 public static final java.util.Set<String> AGGREGATIONS=java.util.Set.of("sum","count","maximum","unique_count","latest_value");
 /** Sets the billable-metric definition at creation time (plans are otherwise immutable). */
 public void defineMetric(String aggregation,String property,String filtersJson){String a=aggregation==null||aggregation.isBlank()?"sum":aggregation.toLowerCase(java.util.Locale.ROOT);if(!AGGREGATIONS.contains(a))throw new IllegalArgumentException("aggregation must be one of "+AGGREGATIONS);if(a.equals("unique_count")&&(property==null||property.isBlank()))throw new IllegalArgumentException("unique_count needs aggregationProperty");this.aggregation=a;this.aggregationProperty=property==null||property.isBlank()?null:property;this.metricFiltersJson=filtersJson;}
 public boolean isAdditiveUnfiltered(){return "sum".equals(getAggregation())&&(metricFiltersJson==null||metricFiltersJson.isBlank()||metricFiltersJson.equals("{}"));} public void setTaxRate(BigDecimal rate){if(rate==null||rate.signum()<0||rate.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Tax rate must be between 0 and 100");taxRate=rate;}
}
