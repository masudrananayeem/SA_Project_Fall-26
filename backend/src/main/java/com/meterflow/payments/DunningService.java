package com.meterflow.payments;

import com.meterflow.invoicing.InvoiceRepository;
import com.meterflow.notifications.WebhookDeliveryService;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Persisted day-1/day-3/day-7 retry state machine with subscription access propagation. */
@Service
public class DunningService {
    private final DunningRepository cases;
    private final InvoiceRepository invoices;
    private final WebhookDeliveryService webhooks;
    private final SubscriptionRepository subscriptions;

    public DunningService(DunningRepository cases, InvoiceRepository invoices,
                          WebhookDeliveryService webhooks, SubscriptionRepository subscriptions) {
        this.cases = cases;
        this.invoices = invoices;
        this.webhooks = webhooks;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public DunningCase recordFailure(UUID invoiceId) {
        var invoice = invoices.findById(invoiceId).orElseThrow();
        if (!"FINALISED".equals(invoice.getStatus()))
            throw new IllegalStateException("Only finalised invoices can enter dunning");
        return cases.findByInvoiceId(invoiceId).orElseGet(() ->
                cases.save(new DunningCase(invoiceId, invoice.getCustomerId())));
    }

    @Transactional
    public DunningCase recordSuccess(UUID invoiceId) {
        var c = cases.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("No dunning case for invoice"));
        c.paid();
        cases.save(c);
        subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(c.getCustomerId(), "PAUSED_DUNNING")
                .ifPresent(s -> { s.resumeFromDunning(); subscriptions.save(s); });
        return c;
    }

    @Transactional
    @Scheduled(cron = "0 0 2 * * *")
    public void processDueRetries() {
        List<DunningCase> dueCases = Stream.concat(
                cases.findByStatus("RETRYING").stream(),
                cases.findByStatus("PAST_DUE").stream()).toList();
        Instant now = Instant.now();
        for (var c : dueCases) {
            if (c.getNextAttemptAt() == null || c.getNextAttemptAt().isAfter(now)) continue;
            c.failed();
            cases.save(c);
            if ("SUSPENDED".equals(c.getStatus())) {
                subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(c.getCustomerId(), "ACTIVE")
                        .ifPresent(s -> { s.pauseForDunning(); subscriptions.save(s); });
            }
            webhooks.publish(c.getCustomerId(), "dunning.retry", Map.of(
                    "invoiceId", c.getInvoiceId().toString(),
                    "attempt", c.getAttempt(),
                    "status", c.getStatus(),
                    "nextAttemptAt", c.getNextAttemptAt() == null ? "none" : c.getNextAttemptAt().toString()));
        }
    }

    @Transactional
    public void recordSuccessIfPresent(UUID invoiceId) {
        cases.findByInvoiceId(invoiceId).ifPresent(c -> {
            c.paid();
            cases.save(c);
            subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(c.getCustomerId(), "PAUSED_DUNNING")
                    .ifPresent(s -> { s.resumeFromDunning(); subscriptions.save(s); });
        });
    }

    public List<DunningCase> all() { return cases.findAll(); }
}
