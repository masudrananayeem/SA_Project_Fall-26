package com.meterflow.subscriptions;
import jakarta.persistence.*; import java.time.*; import java.util.*;
@Entity @Table(name="subscriptions") public class Subscription {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(nullable=false) private String customerId; @Column(nullable=false) private UUID planId; @Column(nullable=false) private String status="ACTIVE"; @Column(nullable=false) private LocalDate periodStart; @Column(nullable=false) private LocalDate periodEnd; @org.hibernate.annotations.ColumnDefault("1") @Column(nullable=false) private int seatCount=1;
 @Column private LocalDate trialEnd;
 @Column private UUID scheduledPlanId;
 @Column private LocalDate scheduledChangeDate;
 @Version private long version;
 protected Subscription(){} public Subscription(String c,UUID p,LocalDate s,LocalDate e){customerId=c;planId=p;periodStart=s;periodEnd=e;}
 public UUID getId(){return id;} public String getCustomerId(){return customerId;} public UUID getPlanId(){return planId;} public String getStatus(){return status;} public LocalDate getPeriodStart(){return periodStart;} public LocalDate getPeriodEnd(){return periodEnd;} public int getSeatCount(){return seatCount;} public LocalDate getTrialEnd(){return trialEnd;} public UUID getScheduledPlanId(){return scheduledPlanId;} public LocalDate getScheduledChangeDate(){return scheduledChangeDate;}
 public boolean isTrialing(){return trialEnd!=null && LocalDate.now().isBefore(trialEnd);}
 public void setSeatCount(int seats){if(seats<1)throw new IllegalArgumentException("Seat count must be at least one");seatCount=seats;}
 public void startTrial(LocalDate end){if(end==null||!end.isAfter(LocalDate.now()))throw new IllegalArgumentException("Trial end must be in the future");trialEnd=end;}
 public void schedulePlanChange(UUID planId,LocalDate date){if(!date.isAfter(LocalDate.now()))throw new IllegalArgumentException("Scheduled change date must be in the future");scheduledPlanId=planId;scheduledChangeDate=date;}
 public boolean applyScheduledChange(LocalDate today){if(scheduledPlanId!=null&&scheduledChangeDate!=null&&!today.isBefore(scheduledChangeDate)){planId=scheduledPlanId;scheduledPlanId=null;scheduledChangeDate=null;return true;}return false;}
 public void changePlan(UUID p){if(!"ACTIVE".equals(status))throw new IllegalStateException("Only active subscriptions can change plans");planId=p;} public void pause(){status="PAUSED";} public void resume(){status="ACTIVE";} public void cancel(){status="CANCELLED";}
}
