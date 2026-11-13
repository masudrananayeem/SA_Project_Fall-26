package com.meterflow.payments;
import org.junit.jupiter.api.Test;import java.util.UUID;import static org.junit.jupiter.api.Assertions.*;
class DunningCaseTest{@Test void retriesThenSuspendsAndSuccessReactivates(){var d=new DunningCase(UUID.randomUUID(),"customer-1");assertEquals("RETRYING",d.getStatus());d.failed();assertEquals(1,d.getAttempt());assertEquals("PAST_DUE",d.getStatus());d.failed();assertEquals(2,d.getAttempt());d.failed();assertEquals("SUSPENDED",d.getStatus());assertNull(d.getNextAttemptAt());d.paid();assertEquals("REACTIVATED",d.getStatus());}}
