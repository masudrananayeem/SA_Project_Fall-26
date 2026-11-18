package com.meterflow.payments;

import java.math.BigDecimal;
import java.util.UUID;

/** Domain events published by the payments module (observed by notifications). */
public final class PaymentEvents {
    private PaymentEvents() { }

    public record PaymentSucceeded(UUID invoiceId, String customerId, BigDecimal amount, String currency, String reference) { }

    public record PaymentFailed(UUID invoiceId, String customerId, BigDecimal amount, String currency, int attempt,
                                String reason, String nextAttemptAt) { }

    /** A postpaid account was blocked (too many unpaid invoices) or unblocked (paid down). */
    public record AccountStandingChanged(String customerId, String status, long unpaidInvoices) { }

    /** Every dunning step (retrying, past due, suspended, reactivated) so the customer is told each time. */
    public record DunningStatusChanged(UUID invoiceId, String customerId, String status, int attempt, String nextAttemptAt) { }
}
