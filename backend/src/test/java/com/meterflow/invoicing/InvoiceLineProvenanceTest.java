package com.meterflow.invoicing;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InvoiceLineProvenanceTest {
    @Test
    void retainsSourceEventIdsAndCount() {
        UUID invoice = UUID.randomUUID();
        String ids = "[\"" + UUID.randomUUID() + "\",\"" + UUID.randomUUID() + "\"]";
        InvoiceLine line = new InvoiceLine(invoice, "api_calls", new BigDecimal("12"),
                new BigDecimal("0.10"), new BigDecimal("1.20"), "per-unit",
                "VAT 5%", ids, 2);
        assertEquals(ids, line.getSourceEventIdsJson());
        assertEquals(2, line.getSourceEventCount());
        assertEquals(invoice, line.getInvoiceId());
    }

    @Test
    void legacyConstructorDefaultsToNoProvenance() {
        InvoiceLine line = new InvoiceLine(UUID.randomUUID(), "api_calls",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "flat", "VAT 0%");
        assertEquals("[]", line.getSourceEventIdsJson());
        assertEquals(0, line.getSourceEventCount());
    }
}
