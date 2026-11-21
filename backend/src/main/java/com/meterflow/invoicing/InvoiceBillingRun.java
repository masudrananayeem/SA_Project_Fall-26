package com.meterflow.invoicing;

import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PricingService;
import com.meterflow.credits.CreditService;
import com.meterflow.metering.BillableUsageCalculator;
import com.meterflow.metering.LateUsageAdjustmentRepository;
import com.meterflow.metering.UsageEventRepository;
import com.meterflow.subscriptions.PlanChangeRepository;
import com.meterflow.subscriptions.Subscription;
import com.meterflow.subscriptions.SubscriptionRepository;
import com.meterflow.subscriptions.SubscriptionState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * The real billing run: persists the invoice and its lines, consumes prepaid credit, resolves the
 * late-usage ledger and prorations, then finalises the invoice and publishes InvoiceFinalised.
 * Everything happens in one transaction per customer, guarded by the unique (customer, period)
 * constraint, so a crash or restart can never produce a second invoice (QA3).
 */
@Component
public class InvoiceBillingRun extends AbstractBillingRun {
    private final InvoiceLineRepository lines;
    private final CreditService credits;
    private final ApplicationEventPublisher publisher;
    private final boolean autoFinalise;
    private final boolean allowOpenPeriod;

    public InvoiceBillingRun(SubscriptionRepository subscriptions, PlanRepository plans, UsageEventRepository events,
                             PricingService pricing, PlanChangeRepository planChanges, LateUsageAdjustmentRepository lateLedger,
                             InvoiceRepository invoices, InvoiceLineRepository lines, BillableUsageCalculator usage,
                             CreditService credits, ApplicationEventPublisher publisher,
                             @Value("${meterflow.billing.auto-finalise:true}") boolean autoFinalise,
                             @Value("${meterflow.billing.allow-open-period:false}") boolean allowOpenPeriod) {
        super(subscriptions, plans, events, pricing, planChanges, lateLedger, invoices, lines, usage);
        this.lines = lines;
        this.credits = credits;
        this.publisher = publisher;
        this.autoFinalise = autoFinalise;
        this.allowOpenPeriod = allowOpenPeriod;
    }

    /** Public, proxied entry point (one transaction per customer). */
    @Transactional
    public BillingOutcome run(String customerId, YearMonth period) {
        Instant close = period.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        if (!allowOpenPeriod && Instant.now().isBefore(close))
            return BillingOutcome.skipped(customerId, period.toString(), "Period " + period + " is still open");
        return bill(customerId, period, null);
    }

    @Override
    protected Optional<Subscription> findSubscription(String customerId, YearMonth period) {
        var active = subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(customerId, SubscriptionState.ACTIVE.name());
        if (active.isPresent()) return active;
        // Paused, blocked, suspended or cancelled during this month: the month is still invoiced once.
        return subscriptions.findByCustomerIdOrderByPeriodStartDesc(customerId).stream().filter(s -> s.billableIn(period)).findFirst();
    }

    @Override
    protected InvoiceDocument applyCredits(BillingContext ctx, InvoiceDocument document) {
        BigDecimal gross = document.grossTotal();
        if (gross.signum() <= 0) return document;
        return document.withCreditApplied(credits.apply(ctx.customerId, document.currency(), gross));
    }

    @Override
    protected BillingOutcome complete(BillingContext ctx, InvoiceDocument document) {
        Invoice invoice = invoices.saveAndFlush(Invoice.from(document));
        var mapper = new InvoiceLineMapper(invoice.getId(), document.taxRule());
        lines.saveAll(document.lines().stream().map(l -> l.accept(mapper)).toList());

        ctx.includedInThisInvoice.forEach(e -> e.markIncludedInOriginal(invoice.getId()));
        ctx.billedAsLateUsage.forEach(e -> e.markInvoiced(invoice.getId()));
        ctx.ignored.forEach(e -> e.markIgnored());
        lateLedger.saveAll(ctx.includedInThisInvoice);
        lateLedger.saveAll(ctx.billedAsLateUsage);
        lateLedger.saveAll(ctx.ignored);
        ctx.prorations.forEach(c -> c.markInvoiced(invoice.getId()));
        planChanges.saveAll(ctx.prorations);

        // A net credit (for example a large downgrade proration) is carried to the prepaid balance.
        if (document.total().signum() < 0) credits.topUp(ctx.customerId, document.currency(), document.total().negate());

        if (autoFinalise) {
            invoice.finalise();
            invoices.save(invoice);
            publisher.publishEvent(new InvoiceFinalised(invoice.getId(), invoice.getCustomerId(), invoice.getPeriod(),
                    invoice.getTotal(), invoice.getCurrency()));
        }
        return new BillingOutcome(ctx.customerId, ctx.period.toString(), BillingOutcome.CREATED, null, invoice.getId(), document);
    }
}
