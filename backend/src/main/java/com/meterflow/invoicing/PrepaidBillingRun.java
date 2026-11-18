package com.meterflow.invoicing;

import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PriceQuote;
import com.meterflow.catalogue.PricedCharge;
import com.meterflow.catalogue.PricingService;
import com.meterflow.credits.CreditService;
import com.meterflow.metering.BillableUsageCalculator;
import com.meterflow.metering.LateUsageAdjustmentRepository;
import com.meterflow.metering.UsageEventRepository;
import com.meterflow.subscriptions.PlanChangeRepository;
import com.meterflow.subscriptions.Subscription;
import com.meterflow.subscriptions.SubscriptionRepository;
import com.meterflow.subscriptions.SubscriptionState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Third variant of the billing template: the up-front invoice of a PREPAID plan. It is issued when
 * the period starts (or when the customer subscribes), before any usage, so the usage step prices
 * a quantity of zero: the customer pays the package (flat fee, seats, minimum) plus tax in advance.
 * The unique (customer, period) invoice constraint means the month-end run later finds this invoice
 * and does not bill the period again.
 */
@Component
public class PrepaidBillingRun extends AbstractBillingRun {
    private final CreditService credits;
    private final ApplicationEventPublisher publisher;

    public PrepaidBillingRun(SubscriptionRepository subscriptions, PlanRepository plans, UsageEventRepository events,
                             PricingService pricing, PlanChangeRepository planChanges, LateUsageAdjustmentRepository lateLedger,
                             InvoiceRepository invoices, InvoiceLineRepository lines, BillableUsageCalculator usage,
                             CreditService credits, ApplicationEventPublisher publisher) {
        super(subscriptions, plans, events, pricing, planChanges, lateLedger, invoices, lines, usage);
        this.credits = credits;
        this.publisher = publisher;
    }

    /** Issues (or finds) the prepaid invoice of the period. Open periods are allowed: that is the point. */
    @Transactional
    public BillingOutcome run(String customerId, YearMonth period) {
        return bill(customerId, period, null);
    }

    @Override
    protected Optional<Subscription> findSubscription(String customerId) {
        var pending = subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(customerId, SubscriptionState.PENDING_PAYMENT.name());
        return pending.isPresent() ? pending
                : subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(customerId, SubscriptionState.ACTIVE.name());
    }

    /** Prices the package with zero usage: the customer pays for the period before using it. */
    @Override
    protected void rateUsage(BillingContext ctx) {
        ctx.quantity = BigDecimal.ZERO;
        ctx.builder.billedQuantity(BigDecimal.ZERO);
        PriceQuote quote = pricing.quoteForPeriod(ctx.plan, BigDecimal.ZERO, BigDecimal.valueOf(ctx.subscription.getSeatCount()),
                ctx.subscription.getTrialEnd(), ctx.period);
        for (PricedCharge charge : quote.lines()) {
            ctx.builder.line(new BillingLine.UsageCharge(ctx.plan.getMetric(), charge.name(), charge.type(), charge.quantity(),
                    charge.unitPrice(), charge.amount(), "Prepaid package " + ctx.period + " - " + quote.planLabel() + ": " + charge.rule(),
                    List.of(), 0));
        }
    }

    /** Late usage belongs on postpaid invoices; a prepaid invoice carries only the package. */
    @Override
    protected void addLateUsage(BillingContext ctx) { }

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
        lineRepository.saveAll(document.lines().stream().map(l -> l.accept(mapper)).toList());
        ctx.prorations.forEach(c -> c.markInvoiced(invoice.getId()));
        planChanges.saveAll(ctx.prorations);
        if (document.total().signum() < 0) credits.topUp(ctx.customerId, document.currency(), document.total().negate());
        invoice.finalise();
        invoices.save(invoice);
        publisher.publishEvent(new InvoiceFinalised(invoice.getId(), invoice.getCustomerId(), invoice.getPeriod(),
                invoice.getTotal(), invoice.getCurrency()));
        return new BillingOutcome(ctx.customerId, ctx.period.toString(), BillingOutcome.CREATED, null, invoice.getId(), document);
    }
}
