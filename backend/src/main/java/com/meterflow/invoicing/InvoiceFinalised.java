package com.meterflow.invoicing;

import java.math.BigDecimal;
import java.util.UUID;

/** Published when an invoice is finalised; payments collects it and notifications sends invoice.finalised. */
public record InvoiceFinalised(UUID invoiceId, String customerId, String period, BigDecimal total, String currency) { }
