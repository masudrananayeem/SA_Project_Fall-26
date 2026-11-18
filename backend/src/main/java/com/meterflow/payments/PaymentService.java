package com.meterflow.payments;

import com.meterflow.catalogue.PlanRepository;
import com.meterflow.credits.CreditService;
import com.meterflow.invoicing.Invoice;
import com.meterflow.invoicing.InvoiceFinalised;
import com.meterflow.invoicing.InvoiceRepository;
import com.meterflow.subscriptions.Subscription;
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

import java.math.BigDecimal;
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
    private final boolean autoCollect;
    private final PaymentRecordRepository records;
    private final CreditService credits;
    private final PlanRepository plans;
    private final int blockAfterUnpaid;

    public PaymentService(InvoiceRepository invoices, DunningRepository cases, SubscriptionRepository subscriptions,
                          MockPaymentProvider provider, ApplicationEventPublisher events,
                          PlatformTransactionManager transactionManager,
                          @Value("${meterflow.dunning.past-due-grace-days:7}") long graceDays,
                          @Value("${meterflow.payments.auto-collect:false}") boolean autoCollect,
                          PaymentRecordRepository records, CreditService credits, PlanRepository plans,
                          @Value("${meterflow.payments.block-after-unpaid-invoices:2}") int blockAfterUnpaid) {
        this.invoices = invoices;
        this.cases = cases;
        this.subscriptions = subscriptions;
        this.provider = provider;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
        this.grace = Duration.ofDays(graceDays);
        this.autoCollect = autoCollect;
        this.records = records;
        this.credits = credits;
        this.plans = plans;
        this.blockAfterUnpaid = Math.max(1, blockAfterUnpaid);
    }

    /**
     * After finalisation (asynchronous, after the billing transaction commits). By default nothing is
     * charged: the invoice stays FINALISED (payment pending) until the customer pays it or an admin
     * collects it. With meterflow.payments.auto-collect=true the card on file is charged right away.
     */
    @ApplicationModuleListener
    public void on(InvoiceFinalised finalised) {
        if (autoCollect) {
            collect(finalised.invoiceId());
            enforceStanding(finalised.customerId());
            return;
        }
        // Nothing to pay (e.g. fully covered by credit): close it without charging anyone.
        invoices.findById(finalised.invoiceId())
                .filter(i -> i.getTotal().signum() <= 0 && !"PAID".equals(i.getStatus()))
                .ifPresentOrElse(i -> collect(i.getId()),
                        () -> log.debug("Invoice {} finalised; payment pending until the customer pays", finalised.invoiceId()));
        // Postpaid: too many unpaid invoices block the account automatically.
        enforceStanding(finalised.customerId());
    }

    public boolean isAutoCollect() { return autoCollect; }

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
            succeed(invoice, result.reference(), "card_on_file");
            return Map.of("invoiceId", invoiceId, "paymentStatus", "SUCCEEDED", "reference", result.reference());
        }
        ledger(invoice, "card_on_file", PaymentRecord.FAILED, null, result.message());
        var dunning = openCase(invoice, result.message());
        return Map.of("invoiceId", invoiceId, "paymentStatus", "FAILED", "dunningCaseId", dunning.getId(), "reason", result.message());
    }

    /** Manual "payment succeeded" (mock checkout or bank transfer). Reactivates immediately. */
    @Transactional
    public Map<String, Object> recordSuccess(UUID invoiceId) { return recordSuccess(invoiceId, "manual"); }

    /** Customer checkout (card, mobile wallet, bank transfer) or admin "mark as paid". */
    @Transactional
    public Map<String, Object> recordSuccess(UUID invoiceId, String method) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> new NoSuchElementException("Invoice not found"));
        if (!"FINALISED".equals(invoice.getStatus()) && !"PAID".equals(invoice.getStatus()))
            throw new IllegalStateException("Only finalised invoices can be paid");
        if ("PAID".equals(invoice.getStatus())) return Map.of("invoiceId", invoiceId, "paymentStatus", "ALREADY_PAID");
        String m = normaliseMethod(method);
        String reference = m + "_" + UUID.randomUUID().toString().substring(0, 8);
        succeed(invoice, reference, m);
        return Map.of("invoiceId", invoiceId, "paymentStatus", "SUCCEEDED", "provider", "MOCK", "method", m, "reference", reference);
    }

    /** Prepaid credit bought by the customer (or recorded by an admin); applied to the next invoices. */
    @Transactional
    public Map<String, Object> topUp(String customerId, BigDecimal amount, String currency, String method) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (amount.compareTo(new BigDecimal("1000000")) > 0) throw new IllegalArgumentException("Amount is too large");
        String cur = currency == null || currency.isBlank() ? "BDT" : currency;
        String m = normaliseMethod(method);
        var account = credits.topUp(customerId, cur, amount);
        String reference = "topup_" + UUID.randomUUID().toString().substring(0, 8);
        records.save(new PaymentRecord(null, customerId, amount, cur, m, PaymentRecord.TOP_UP, PaymentRecord.SUCCEEDED, reference, "Prepaid credit"));
        return Map.of("customerId", customerId, "paymentStatus", "SUCCEEDED", "method", m, "reference", reference,
                "balance", account.getBalance(), "currency", account.getCurrency());
    }

    public List<PaymentRecord> recent(String customerId) {
        return customerId == null || customerId.isBlank() ? records.findTop200ByOrderByCreatedAtDesc()
                : records.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    /** Totals for the admin dashboard: today, last 7 and 30 days (successful payments). */
    public Map<String, Object> summary() {
        Instant now = Instant.now();
        Instant dayStart = now.atZone(java.time.ZoneOffset.UTC).toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        List<PaymentRecord> last30 = records.findByCreatedAtGreaterThanEqual(now.minus(Duration.ofDays(30)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("today", window(last30, dayStart));
        m.put("last7Days", window(last30, now.minus(Duration.ofDays(7))));
        m.put("last30Days", window(last30, now.minus(Duration.ofDays(30))));
        m.put("failedToday", last30.stream().filter(r -> PaymentRecord.FAILED.equals(r.getStatus()) && !r.getCreatedAt().isBefore(dayStart)).count());
        m.put("blockAfterUnpaidInvoices", blockAfterUnpaid);
        m.put("autoCollect", autoCollect);
        return m;
    }

    private static Map<String, Object> window(List<PaymentRecord> rows, Instant from) {
        BigDecimal total = BigDecimal.ZERO;
        long count = 0;
        for (var r : rows) {
            if (!PaymentRecord.SUCCEEDED.equals(r.getStatus()) || r.getCreatedAt().isBefore(from)) continue;
            total = total.add(r.getAmount());
            count++;
        }
        return Map.of("count", count, "amount", total);
    }

    private static String normaliseMethod(String method) {
        if (method == null || method.isBlank()) return "manual";
        String m = method.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z_]", "");
        return switch (m) {
            case "card", "wallet", "bank", "admin", "manual", "card_on_file" -> m;
            default -> "manual";
        };
    }

    private void ledger(Invoice invoice, String method, String status, String reference, String message) {
        records.save(new PaymentRecord(invoice.getId(), invoice.getCustomerId(), invoice.getTotal(), invoice.getCurrency(),
                method, PaymentRecord.INVOICE, status, reference, message));
    }

    /**
     * Postpaid accounts with {@code blockAfterUnpaid} or more unpaid invoices are blocked (usage is
     * refused); paying them down below the limit unblocks the account. Prepaid accounts are gated by
     * PENDING_PAYMENT instead. Pass null to check every customer.
     */
    @Transactional
    public Map<String, Object> enforceStanding(String onlyCustomer) {
        List<Invoice> source = onlyCustomer == null ? invoices.findAll() : invoices.findByCustomerIdOrderByCreatedAtDesc(onlyCustomer);
        Map<String, Long> unpaid = new HashMap<>();
        for (Invoice i : source)
            if ("FINALISED".equals(i.getStatus()) && i.getTotal().signum() > 0) unpaid.merge(i.getCustomerId(), 1L, Long::sum);
        List<Subscription> subs = onlyCustomer == null ? subscriptions.findAll() : subscriptions.findByCustomerIdOrderByPeriodStartDesc(onlyCustomer);
        Map<String, Subscription> latest = new LinkedHashMap<>();
        for (var s : subs) {
            if (SubscriptionState.CANCELLED.name().equals(s.getStatus()) || SubscriptionState.EXPIRED.name().equals(s.getStatus())) continue;
            latest.merge(s.getCustomerId(), s, (a, b) -> a.getPeriodStart().isAfter(b.getPeriodStart()) ? a : b);
        }
        List<String> blocked = new ArrayList<>();
        List<String> unblocked = new ArrayList<>();
        for (var s : latest.values()) {
            long count = unpaid.getOrDefault(s.getCustomerId(), 0L);
            String status = s.getStatus();
            if (count >= blockAfterUnpaid && (SubscriptionState.ACTIVE.name().equals(status) || SubscriptionState.PAUSED_DUNNING.name().equals(status))) {
                boolean prepaid = plans.findById(s.getPlanId()).map(p -> p.isPrepaid()).orElse(false);
                if (prepaid) continue;
                s.block();
                subscriptions.save(s);
                blocked.add(s.getCustomerId());
                events.publishEvent(new PaymentEvents.AccountStandingChanged(s.getCustomerId(), s.getStatus(), count));
            } else if (count < blockAfterUnpaid && SubscriptionState.BLOCKED.name().equals(status)) {
                s.unblock();
                subscriptions.save(s);
                unblocked.add(s.getCustomerId());
                events.publishEvent(new PaymentEvents.AccountStandingChanged(s.getCustomerId(), s.getStatus(), count));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blockAfterUnpaidInvoices", blockAfterUnpaid);
        result.put("blocked", blocked);
        result.put("unblocked", unblocked);
        return result;
    }

    @Scheduled(cron = "${meterflow.payments.standing-cron:0 20 * * * *}", zone = "UTC")
    public void scheduledStanding() {
        try {
            log.info("Account standing: {}", transactions.execute(tx -> enforceStanding(null)));
        } catch (RuntimeException e) {
            log.error("Account standing check failed", e);
        }
    }

    /** Manual "payment failed" (simulates a declined card for a finalised invoice). */
    @Transactional
    public DunningCase recordFailure(UUID invoiceId) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> new NoSuchElementException("Invoice not found"));
        if (!"FINALISED".equals(invoice.getStatus())) throw new IllegalStateException("Only finalised, unpaid invoices can enter dunning");
        ledger(invoice, "admin", PaymentRecord.FAILED, null, "Payment declined (simulated)");
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

    private void succeed(Invoice invoice, String reference, String method) {
        if (!"PAID".equals(invoice.getStatus())) {
            invoice.markPaid();
            invoices.save(invoice);
            if (invoice.getTotal().signum() > 0) ledger(invoice, method, PaymentRecord.SUCCEEDED, reference, null);
            events.publishEvent(new PaymentEvents.PaymentSucceeded(invoice.getId(), invoice.getCustomerId(), invoice.getTotal(),
                    invoice.getCurrency(), reference));
        }
        // Prepaid: the package is paid, so the service starts (or continues) now.
        subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(invoice.getCustomerId(), SubscriptionState.PENDING_PAYMENT.name())
                .ifPresent(s -> { s.resumeFromDunning(); subscriptions.save(s); });
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
        enforceStanding(invoice.getCustomerId());
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
            succeed(invoice, "already_paid", "manual");
            return true;
        }
        var result = provider.charge(invoice.getCustomerId(), invoice.getId(), invoice.getTotal(), invoice.getCurrency());
        if (result.success()) {
            succeed(invoice, result.reference(), "card_on_file");
            return true;
        }
        ledger(invoice, "card_on_file", PaymentRecord.FAILED, null, "Retry " + (c.getAttempt() + 1) + ": " + result.message());
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
