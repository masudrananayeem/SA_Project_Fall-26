package com.meterflow.payments;

import com.meterflow.invoicing.Invoice;
import com.meterflow.invoicing.InvoiceFinalised;
import com.meterflow.invoicing.InvoiceRepository;
import com.meterflow.subscriptions.SubscriptionRepository;
import com.meterflow.subscriptions.SubscriptionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Collects finalised invoices through the (mock) provider and runs dunning (FR8). The customer is
 * notified at every step through events that the notifications module turns into webhooks and
 * in-app notifications.
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final InvoiceRepository invoices;
    private final DunningRepository cases;
    private final SubscriptionRepository subscriptions;
    private final MockPaymentProvider provider;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final Duration grace;

    public PaymentService(InvoiceRepository invoices, DunningRepository cases, SubscriptionRepository subscriptions,
                          MockPaymentProvider provider, ApplicationEventPublisher events,
                          PlatformTransactionManager transactionManager,
                          @Value("${meterflow.dunning.past-due-grace-days:7}") long graceDays) {
        this.invoices = invoices;
        this.cases = cases;
        this.subscriptions = subscriptions;
        this.provider = provider;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
        this.grace = Duration.ofDays(graceDays);
    }

    /** Automatic collection after finalisation (asynchronous, after the billing transaction commits). */
    @ApplicationModuleListener
    public void on(InvoiceFinalised finalised) { collect(finalised.invoiceId()); }

    @Transactional
    public Map<String, Object> collect(UUID invoiceId) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> new NoSuchElementException("Invoice not found"));
        if ("PAID".equals(invoice.getStatus())) return Map.of("invoiceId", invoiceId, "paymentStatus", "ALREADY_PAID");
        if (invoice.getTotal().signum() <= 0) {
            invoice.markPaid();
            invoices.save(invoice);
            return Map.of("invoiceId", invoiceId, "paymentStatus", "NOTHING_DUE");
        }
        var result = provider.charge(invoice.getCustomerId(), invoiceId, invoice.getTotal(), invoice.getCurrency());
        if (result.success()) {
            succeed(invoice, result.reference());
            return Map.of("invoiceId", invoiceId, "paymentStatus", "SUCCEEDED", "reference", result.reference());
        }
        var dunning = openCase(invoice, result.message());
        return Map.of("invoiceId", invoiceId, "paymentStatus", "FAILED", "dunningCaseId", dunning.getId(), "reason", result.message());
    }

    /** Manual "payment succeeded" (mock checkout or bank transfer). Reactivates immediately. */
    @Transactional
    public Map<String, Object> recordSuccess(UUID invoiceId) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> new NoSuchElementException("Invoice not found"));
        if (!"FINALISED".equals(invoice.getStatus()) && !"PAID".equals(invoice.getStatus()))
            throw new IllegalStateException("Only finalised invoices can be paid");
        succeed(invoice, "manual_" + UUID.randomUUID());
        return Map.of("invoiceId", invoiceId, "paymentStatus", "SUCCEEDED", "provider", "MOCK");
    }

    /** Manual "payment failed" (simulates a declined card for a finalised invoice). */
    @Transactional
    public DunningCase recordFailure(UUID invoiceId) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> new NoSuchElementException("Invoice not found"));
        if (!"FINALISED".equals(invoice.getStatus())) throw new IllegalStateException("Only finalised, unpaid invoices can enter dunning");
        return openCase(invoice, "Payment declined (manual mock)");
    }

    private DunningCase openCase(Invoice invoice, String reason) {
        var existing = cases.findByInvoiceId(invoice.getId());
        if (existing.isPresent()) return existing.get();
        var dunning = cases.save(new DunningCase(invoice.getId(), invoice.getCustomerId(), Instant.now()));
        events.publishEvent(new PaymentEvents.PaymentFailed(invoice.getId(), invoice.getCustomerId(), invoice.getTotal(),
                invoice.getCurrency(), 0, reason, String.valueOf(dunning.getNextAttemptAt())));
        statusChanged(dunning);
        return dunning;
    }

    private void succeed(Invoice invoice, String reference) {
        if (!"PAID".equals(invoice.getStatus())) {
            invoice.markPaid();
            invoices.save(invoice);
            events.publishEvent(new PaymentEvents.PaymentSucceeded(invoice.getId(), invoice.getCustomerId(), invoice.getTotal(),
                    invoice.getCurrency(), reference));
        }
        cases.findByInvoiceId(invoice.getId()).ifPresent(c -> {
            if (!c.state().open()) return;
            c.paymentSucceeded(Instant.now());
            cases.save(c);
            statusChanged(c);
        });
        // Reactivate as soon as payment succeeds, if no other suspended invoice remains.
        boolean otherSuspended = cases.findByStatus(DunningState.SUSPENDED.name()).stream()
                .anyMatch(c -> c.getCustomerId().equals(invoice.getCustomerId()) && !c.getInvoiceId().equals(invoice.getId()));
        if (!otherSuspended)
            subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(invoice.getCustomerId(), SubscriptionState.PAUSED_DUNNING.name())
                    .ifPresent(s -> { s.resumeFromDunning(); subscriptions.save(s); });
    }

    private void statusChanged(DunningCase c) {
        events.publishEvent(new PaymentEvents.DunningStatusChanged(c.getInvoiceId(), c.getCustomerId(), c.getStatus(), c.getAttempt(),
                c.getNextAttemptAt() == null ? "none" : c.getNextAttemptAt().toString()));
    }

    /** Retries every due case as of the given time; each case in its own transaction. */
    public Map<String, Object> processDueRetries(Instant now) {
        List<UUID> due = new ArrayList<>();
        for (String status : List.of(DunningState.RETRYING.name(), DunningState.PAST_DUE.name()))
            for (var c : cases.findByStatus(status)) if (c.isDue(now)) due.add(c.getId());
        int recovered = 0, failed = 0;
        for (UUID id : due) {
            Boolean ok = transactions.execute(tx -> retry(id, now));
            if (Boolean.TRUE.equals(ok)) recovered++; else failed++;
        }
        return Map.of("asOf", now.toString(), "processed", due.size(), "recovered", recovered, "failedAgain", failed);
    }

    private boolean retry(UUID caseId, Instant now) {
        var c = cases.findById(caseId).orElseThrow();
        if (!c.isDue(now)) return false;
        var invoice = invoices.findById(c.getInvoiceId()).orElseThrow();
        if ("PAID".equals(invoice.getStatus())) {
            succeed(invoice, "already_paid");
            return true;
        }
        var result = provider.charge(invoice.getCustomerId(), invoice.getId(), invoice.getTotal(), invoice.getCurrency());
        if (result.success()) {
            succeed(invoice, result.reference());
            return true;
        }
        DunningState next = c.attemptFailed(now, grace);
        cases.save(c);
        events.publishEvent(new PaymentEvents.PaymentFailed(invoice.getId(), invoice.getCustomerId(), invoice.getTotal(),
                invoice.getCurrency(), c.getAttempt(), result.message(), c.getNextAttemptAt() == null ? "none" : c.getNextAttemptAt().toString()));
        statusChanged(c);
        if (next == DunningState.SUSPENDED)
            subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(c.getCustomerId(), SubscriptionState.ACTIVE.name())
                    .ifPresent(s -> { s.pauseForDunning(); subscriptions.save(s); });
        return false;
    }

    @Scheduled(cron = "${meterflow.dunning.cron:0 0 2 * * *}", zone = "UTC")
    public void scheduledRetries() {
        try {
            log.info("Dunning retries: {}", processDueRetries(Instant.now()));
        } catch (RuntimeException e) {
            log.error("Dunning retry run failed", e);
        }
    }

    public List<DunningCase> all() { return cases.findAll(); }
}
