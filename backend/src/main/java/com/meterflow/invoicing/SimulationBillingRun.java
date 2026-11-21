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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * FR10 simulator: "what would this customer's invoice for a month have been under plan version X?"
 * Reuses the same template steps as the real run (same usage rating, trial rule and tax), but
 * reads only: it never writes invoices, never consumes credit and never touches the ledgers.
 * All of the month's events are priced, including late ones, so it shows the complete month.
 */
@Component
public class SimulationBillingRun extends AbstractBillingRun {
    private final CreditService credits;

    public SimulationBillingRun(SubscriptionRepository subscriptions, PlanRepository plans, UsageEventRepository events,
                                PricingService pricing, PlanChangeRepository planChanges, LateUsageAdjustmentRepository lateLedger,
                                InvoiceRepository invoices, InvoiceLineRepository lines, BillableUsageCalculator usage,
                                CreditService credits) {
        super(subscriptions, plans, events, pricing, planChanges, lateLedger, invoices, lines, usage);
        this.credits = credits;
    }

    @Transactional(readOnly = true)
    public BillingOutcome simulate(String customerId, YearMonth period, UUID planId) { return bill(customerId, period, planId); }

    @Override protected boolean alreadyBilled(String customerId, String period) { return false; }

    /** Any most recent subscription, so past months of cancelled customers can be simulated too. */
    @Override
    protected Optional<Subscription> findSubscription(String customerId, YearMonth period) {
        return subscriptions.findByCustomerIdOrderByPeriodStartDesc(customerId).stream().findFirst();
    }

    @Override protected void addProrations(BillingContext ctx) { }

    @Override protected void addLateUsage(BillingContext ctx) { }

    /** Preview only: shows how much of the current prepaid balance would be used. */
    @Override
    protected InvoiceDocument applyCredits(BillingContext ctx, InvoiceDocument document) {
        BigDecimal gross = document.grossTotal();
        if (gross.signum() <= 0) return document;
        var account = credits.balance(ctx.customerId);
        if (!account.getCurrency().equals(document.currency())) return document;
        return document.withCreditApplied(account.getBalance().min(gross).max(BigDecimal.ZERO));
    }

    @Override
    protected BillingOutcome complete(BillingContext ctx, InvoiceDocument document) {
        return new BillingOutcome(ctx.customerId, ctx.period.toString(), BillingOutcome.SIMULATED, null, null, document);
    }
}
