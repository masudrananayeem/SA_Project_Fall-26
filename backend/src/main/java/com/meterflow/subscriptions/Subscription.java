package com.meterflow.subscriptions;
import jakarta.persistence.*; import java.time.*; import java.util.*;
@Entity @Table(name="subscriptions") public class Subscription {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(nullable=false) private String customerId; @Column(nullable=false) private UUID planId; @Column(nullable=false) private String status="ACTIVE"; @Column(nullable=false) private LocalDate periodStart; @Column(nullable=false) private LocalDate periodEnd; @Version private long version;
 protected Subscription(){} public Subscription(String c,UUID p,LocalDate s,LocalDate e){customerId=c;planId=p;periodStart=s;periodEnd=e;}
 public UUID getId(){return id;} public String getCustomerId(){return customerId;} public UUID getPlanId(){return planId;} public String getStatus(){return status;} public LocalDate getPeriodStart(){return periodStart;} public LocalDate getPeriodEnd(){return periodEnd;} public void pause(){status="PAUSED";} public void resume(){status="ACTIVE";} public void cancel(){status="CANCELLED";}
}
