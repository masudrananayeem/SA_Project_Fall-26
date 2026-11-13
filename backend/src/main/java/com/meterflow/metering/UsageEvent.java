package com.meterflow.metering;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="usage_events", uniqueConstraints=@UniqueConstraint(name="uk_usage_idempotency",columnNames="idempotency_key"), indexes={@Index(name="idx_usage_customer_metric_time",columnList="customer_id,metric,timestamp")})
public class UsageEvent {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="customer_id",nullable=false) private String customerId;
 @Column(nullable=false) private String metric;
 @Column(nullable=false,precision=24,scale=8) private BigDecimal quantity;
 @Column(nullable=false) private Instant timestamp;
 @Column(name="idempotency_key",nullable=false,unique=true) private String idempotencyKey;
 @Column(length=1000) private String propertiesJson;
 protected UsageEvent(){}
 public UsageEvent(String c,String m,BigDecimal q,Instant t,String k,String p){customerId=c;metric=m;quantity=q;timestamp=t;idempotencyKey=k;propertiesJson=p;}
 public UUID getId(){return id;} public String getCustomerId(){return customerId;} public String getMetric(){return metric;} public BigDecimal getQuantity(){return quantity;} public Instant getTimestamp(){return timestamp;} public String getIdempotencyKey(){return idempotencyKey;} public String getPropertiesJson(){return propertiesJson;}
}
