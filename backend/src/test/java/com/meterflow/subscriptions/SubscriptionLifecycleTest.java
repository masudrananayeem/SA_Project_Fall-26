package com.meterflow.subscriptions;
import org.junit.jupiter.api.Test;import java.time.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class SubscriptionLifecycleTest {
 @Test void supportsSeatsAndTrial(){var s=new Subscription("acme",UUID.randomUUID(),LocalDate.now(),LocalDate.now().plusDays(30));s.setSeatCount(5);s.startTrial(LocalDate.now().plusDays(7));assertEquals(5,s.getSeatCount());assertTrue(s.isTrialing());}
 @Test void scheduledPlanChangeActivatesOnEffectiveDate(){var old=UUID.randomUUID();var next=UUID.randomUUID();var s=new Subscription("acme",old,LocalDate.now(),LocalDate.now().plusDays(30));s.schedulePlanChange(next,LocalDate.now().plusDays(3));assertFalse(s.applyScheduledChange(LocalDate.now().plusDays(2)));assertTrue(s.applyScheduledChange(LocalDate.now().plusDays(3)));assertEquals(next,s.getPlanId());assertNull(s.getScheduledPlanId());}
 @Test void rejectsInvalidSeatCount(){var s=new Subscription("acme",UUID.randomUUID(),LocalDate.now(),LocalDate.now().plusDays(30));assertThrows(IllegalArgumentException.class,()->s.setSeatCount(0));}
}
