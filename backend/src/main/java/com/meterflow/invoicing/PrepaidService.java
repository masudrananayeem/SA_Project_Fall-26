package com.meterflow.invoicing;

import com.meterflow.catalogue.PlanRepository;
import com.meterflow.subscriptions.SubscriptionRepository;
import com.meterflow.subscriptions.SubscriptionStarted;
import com.meterflow.subscriptions.SubscriptionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepaid plans: the package invoice is issued up front and the subscription waits in
 * PENDING_PAYMENT until it is paid (the payments module activates it).
 * - When a prepaid subscription starts: invoice the current month.
 * - Every day (and on demand): renew active prepaid subscriptions whose current month has no invoice yet.
 */
@Service
public class PrepaidService {
    private static final Logger log = LoggerFactory.getLogger(PrepaidService.class);

    private final PrepaidBillingRun run;
    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final InvoiceRepository invoices;
    private final TransactionTemplate tx;

    public PrepaidService(PrepaidBillingRun run, SubscriptionRepository subscriptions, PlanRepository plans,
                          InvoiceRepository invoices, PlatformTransactionManager transactionManager) {
        this.run = run;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.invoices = invoices;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Asynchronous, after the subscription is committed. */
    @ApplicationModuleListener
    public void on(SubscriptionStarted started) {
        if (!started.prepaid()) return;
        invoiceCurrentPeriod(started.customerId());
    }

    /**
     * Issues this month's prepaid invoice for one customer. If an invoice for the month already
     * exists and is paid (for example after cancelling and subscribing again), the subscription is
     * activated straight away.
     */
    public BillingOutcome invoiceCurrentPeriod(String customerId) {
        YearMonth period = YearMonth.now(ZoneOffset.UTC);
        BillingOutcome outcome = run.run(customerId, period);
        boolean paidAlready = BillingOutcome.EXISTING.equals(outcome.status()) && outcome.invoiceId() != null
                && invoices.findById(outcome.invoiceId()).map(i -> "PAID".equals(i.getStatus())).orElse(false);
        // Nothing to pay now (already paid, or the month is inside a free trial): start the service.
        if (paidAlready || BillingOutcome.SKIPPED.equals(outcome.status())) {
            tx.executeWithoutResult(status -> subscriptions
                    .findTopByCustomerIdAndStatusOrderByPeriodStartDesc(customerId, SubscriptionState.PENDING_PAYMENT.name())
                    .ifPresent(s -> {
                        s.resumeFromDunning();
                        subscriptions.save(s);
                    }));
        }
        log.info("Prepaid invoice for {} {}: {}", customerId, period, outcome.status());
        return outcome;
    }

    /** Daily renewal: each active prepaid subscription gets its new month's package invoice and waits for payment. */
    @Scheduled(cron = "${meterflow.billing.prepaid-renewal-cron:0 10 0 * * *}", zone = "UTC")
    public void scheduledRenewal() { renew(); }

    public Map<String, Object> renew() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String period = YearMonth.from(today).toString();
        List<String> renewed = new ArrayList<>();
        for (var sub : subscriptions.findByStatus(SubscriptionState.ACTIVE.name())) {
            if (today.isBefore(sub.getPeriodStart()) || today.isAfter(sub.getPeriodEnd())) continue;
            var plan = plans.findById(sub.getPlanId()).orElse(null);
            if (plan == null || !plan.isPrepaid()) continue;
            if (invoices.findByCustomerIdAndPeriod(sub.getCustomerId(), period).isPresent()) continue;
            BillingOutcome outcome = run.run(sub.getCustomerId(), YearMonth.from(today));
            if (!BillingOutcome.CREATED.equals(outcome.status())) continue;
            tx.executeWithoutResult(status -> subscriptions.findById(sub.getId()).ifPresent(s -> {
                var invoice = invoices.findById(outcome.invoiceId()).orElse(null);
                if (invoice != null && !"PAID".equals(invoice.getStatus()) && invoice.getTotal().signum() > 0) {
                    s.awaitPayment();
                    subscriptions.save(s);
                }
            }));
            renewed.add(sub.getCustomerId());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("renewed", renewed.size());
        result.put("customers", renewed);
        return result;
    }
}
