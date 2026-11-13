package com.meterflow.credits;
import org.junit.jupiter.api.Test;import java.math.BigDecimal;import static org.junit.jupiter.api.Assertions.*;
class CreditAccountTest{@Test void consumesOnlyAvailableCredit(){var a=new CreditAccount("customer-1","BDT");a.add(new BigDecimal("30.00"));assertEquals(new BigDecimal("30.00"),a.consume(new BigDecimal("45.00")));assertEquals(new BigDecimal("0.00"),a.getBalance());}@Test void rejectsCurrencylessOrNegativeTopups(){var a=new CreditAccount("customer-1","BDT");assertThrows(IllegalArgumentException.class,()->a.add(new BigDecimal("-1")));}}
