package com.meterflow.subscriptions;
import org.springframework.stereotype.Service;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.transaction.annotation.Transactional;import java.time.*;
@Service public class SubscriptionLifecycleService {private final SubscriptionRepository repo;public SubscriptionLifecycleService(SubscriptionRepository r){repo=r;}
 @Scheduled(cron="0 10 0 * * *") @Transactional public void applyScheduledPlanChanges(){for(var s:repo.findByStatus("ACTIVE")){if(s.applyScheduledChange(LocalDate.now(ZoneOffset.UTC)))repo.save(s);}}
}
