package com.meterflow.invoicing;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class InvoiceFinalisationTest {
    @Test void draftCanBeFinalisedOnce() {
        var invoice = new Invoice("customer-1", "2026-11", "BDT", new BigDecimal("100.00"), BigDecimal.ZERO);
        assertEquals("DRAFT", invoice.getStatus());
        invoice.finalise();
        assertEquals("FINALISED", invoice.getStatus());
        assertThrows(IllegalStateException.class, invoice::finalise);
    }
}
