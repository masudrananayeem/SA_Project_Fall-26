package com.meterflow.invoicing;

import com.meterflow.catalogue.Plan;
import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PriceQuote;
import com.meterflow.catalogue.PricedCharge;
import com.meterflow.catalogue.PricingService;
import com.meterflow.metering.BillableUsageCalculator;
import com.meterflow.metering.LateUsageAdjustment;
import com.meterflow.metering.LateUsageAdjustmentRepository;
import com.meterflow.metering.UsageEvent;
import com.meterflow.metering.UsageEventRepository;
import com.meterflow.subscriptions.PlanChange;
import com.meterflow.subscriptions.PlanChangeRepository;
import com.meterflow.subscriptions.Subscription;
import com.meterflow.subscriptions.SubscriptionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Template Method for billing one customer for one calendar month (FR5). The skeleton in
 * {@link #bill} fixes the order of the steps; subclasses decide whether the result is persisted
 * (the real run) or only returned (the simulator), and may switch individual steps off.
 *
 * <ol>
 *   <li>prepare: subscription, plan version, period window, trial rule</li>
 *   <li>rateUsage: price this month's events with the plan (one line per charge)</li>
 *   <li>addProrations: mid-cycle plan-change deltas not yet invoiced</li>
 *   <li>addLateUsage: events that arrived after an earlier month was invoiced (FR1/FR6)</li>
 *   <li>build: subtotal and tax (TaxVisitor) through the InvoiceBuilder</li>
 *   <li>applyCredits: prepaid balance (FR6)</li>
 *   <li>complete: persist + finalise, or return a preview</li>
 * </ol>
 */
public abstract class AbstractBillingRun {
    protected final SubscriptionRepository subscriptions;
    protected final PlanRepository plans;
    protected final UsageEventRepository events;
    protected final PricingService pricing;
    protected final PlanChangeRepository planChanges;
    protected final LateUsageAdjustmentRepository lateLedger;
    protected final InvoiceRepository invoices;
    protected final InvoiceLineRepository lineRepository;
    protected final BillableUsageCalculator usage;

    protected AbstractBillingRun(SubscriptionRepository subscriptions, PlanRepository plans, UsageEventRepository events,
                                 PricingService pricing, PlanChangeRepository planChanges,
                                 LateUsageAdjustmentRepository lateLedger, InvoiceRepository invoices,
                                 InvoiceLineRepository lineRepository, BillableUsageCalculator usage) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.events = events;
        this.pricing = pricing;
        this.planChanges = planChanges;
        this.lateLedger = lateLedger;
        this.invoices = invoices;
        this.lineRepository = lineRepository;
        this.usage = usage;
    }

    /** Mutable working state of one run. */
    protected static final class BillingContext {
        final String customerId;
        final YearMonth period;
        final Instant from;
        final Instant to;
        Subscription subscription;
        Plan plan;
        BigDecimal quantity = BigDecimal.ZERO;
        final InvoiceBuilder builder = new InvoiceBuilder();
        final List<LateUsageAdjustment> includedInThisInvoice = new ArrayList<>();
        final List<LateUsageAdjustment> billedAsLateUsage = new ArrayList<>();
        final List<LateUsageAdjustment> ignored = new ArrayList<>();
        final List<PlanChange> prorations = new ArrayList<>();

        BillingContext(String customerId, YearMonth period) {
            this.customerId = customerId;
            this.period = period;
            this.from = period.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            this.to = period.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }

    /** The template method. Final so the order of the steps cannot be changed by a subclass. */
    protected final BillingOutcome bill(String customerId, YearMonth period, UUID planOverride) {
        String p = period.toString();
        if (alreadyBilled(customerId, p)) {
            var existing = invoices.findByCustomerIdAndPeriod(customerId, p).orElseThrow();
            return new BillingOutcome(customerId, p, BillingOutcome.EXISTING, "Invoice already exists", existing.getId(), null);
        }
        BillingContext ctx = new BillingContext(customerId, period);
        String skip = prepare(ctx, planOverride);
        if (skip != null) return BillingOutcome.skipped(customerId, p, skip);
        rateUsage(ctx);
        addProrations(ctx);
        addLateUsage(ctx);
        InvoiceDocument document = ctx.builder.build();
        document = applyCredits(ctx, document);
        return complete(ctx, document);
    }

    // ---- hooks -------------------------------------------------------------------------------

    protected boolean alreadyBilled(String customerId, String period) {
        return invoices.findByCustomerIdAndPeriod(customerId, period).isPresent();
    }

    /** Subscription to bill. The real run bills only ACTIVE subscriptions. */
    protected abstract Optional<Subscription> findSubscription(String customerId);

    protected abstract InvoiceDocument applyCredits(BillingContext ctx, InvoiceDocument document);

    protected abstract BillingOutcome complete(BillingContext ctx, InvoiceDocument document);

    // ---- steps -------------------------------------------------------------------------------

    /** Returns a skip reason, or null when the customer is billable for the period. */
    protected String prepare(BillingContext ctx, UUID planOverride) {
        var sub = findSubscription(ctx.customerId).orElse(null);
        if (sub == null) return "No billable subscription";
        LocalDate start = ctx.period.atDay(1);
        LocalDate next = ctx.period.plusMonths(1).atDay(1);
        if (!sub.getPeriodStart().isBefore(next) || !sub.getPeriodEnd().isAfter(start.minusDays(1)))
            return "Subscription does not overlap the period";
        if (PricingService.fullyInTrial(sub.getTrialEnd(), ctx.period)) return "Period is inside the free trial";
        UUID planId = planOverride == null ? planAtEndOf(sub, ctx.period) : planOverride;
        Plan plan = plans.findById(planId).orElseThrow(() -> new IllegalStateException("Plan not found: " + planId));
        ctx.subscription = sub;
        ctx.plan = plan;
        ctx.builder.customer(ctx.customerId).period(ctx.period.toString()).currency(plan.getCurrency())
                .plan(plan.getId(), plan.getPlanKey() + " v" + plan.getVersion())
                .seats(sub.getSeatCount())
                .tax(TaxVisitor.vat(plan.getTaxRate()));
        return null;
    }

    /**
     * The plan in force on the last day of the period. A change that took effect later (for
     * example a period-end downgrade applied on the 1st, before the month-end run) must not
     * re-price the month that just closed, so the earliest such change gives the plan to use.
     */
    protected UUID planAtEndOf(Subscription sub, YearMonth period) {
        LocalDate next = period.plusMonths(1).atDay(1);
        PlanChange first = null;
        for (PlanChange change : planChanges.findBySubscriptionIdOrderByEffectiveDateDesc(sub.getId())) {
            if (change.getEffectiveDate().isBefore(next)) continue;
            if (first == null || change.getEffectiveDate().isBefore(first.getEffectiveDate())) first = change;
        }
        return first == null ? sub.getPlanId() : first.getFromPlanId();
    }

    protected void rateUsage(BillingContext ctx) {
        List<UsageEvent> source = events.findMetricWindow(ctx.customerId, ctx.plan.getMetric(), ctx.from, ctx.to);
        var billable = usage.quantify(ctx.plan, source); // FR2: sum, count, maximum, unique_count, latest_value + filters
        BigDecimal quantity = billable.quantity();
        ctx.quantity = quantity;
        ctx.builder.billedQuantity(quantity);
        PriceQuote quote = pricing.quoteForPeriod(ctx.plan, quantity, BigDecimal.valueOf(ctx.subscription.getSeatCount()),
                ctx.subscription.getTrialEnd(), ctx.period);
        List<UUID> ids = billable.matchedEvents().stream().map(UsageEvent::getId).toList();
        boolean first = true;
        for (PricedCharge charge : quote.lines()) {
            // The full event list is stored once (first line); every usage line carries the count.
            ctx.builder.line(new BillingLine.UsageCharge(ctx.plan.getMetric(), charge.name(), charge.type(), charge.quantity(),
                    charge.unitPrice(), charge.amount(), quote.planLabel() + ": " + charge.rule(),
                    first ? ids : List.of(), ids.size()));
            first = false;
        }
        // Late-ledger entries for this period that are already in this invoice must not be billed again.
        Set<UUID> idSet = new HashSet<>();
        for (UsageEvent e : source) idSet.add(e.getId());
        for (var entry : lateLedger.findByCustomerIdAndBillingPeriodAndStatus(ctx.customerId, ctx.period.toString(), LateUsageAdjustment.PENDING)) {
            if (idSet.contains(entry.getUsageEventId())) ctx.includedInThisInvoice.add(entry);
        }
    }

    protected void addProrations(BillingContext ctx) {
        var pending = planChanges.findBySubscriptionIdAndInvoiceIdIsNullAndEffectiveDateBefore(
                ctx.subscription.getId(), ctx.period.plusMonths(1).atDay(1));
        for (PlanChange change : pending) {
            if (!change.getCurrency().equals(ctx.plan.getCurrency())) continue;
            ctx.prorations.add(change);
            if (change.getProratedDelta().signum() != 0)
                ctx.builder.line(new BillingLine.Proration(change.getId(), change.getProratedDelta(),
                        change.getExplanation() == null ? "Plan change on " + change.getEffectiveDate() : change.getExplanation()));
        }
    }

    /**
     * Late events for an earlier period P are billed as the price difference
     * price(billed(P) + late) - price(billed(P)) under the plan version that priced P, where
     * billed(P) is the quantity on P's invoice plus late usage already billed for P.
     */
    protected void addLateUsage(BillingContext ctx) {
        String current = ctx.period.toString();
        Map<String, List<LateUsageAdjustment>> byPeriod = lateLedger.findByCustomerIdAndStatus(ctx.customerId, LateUsageAdjustment.PENDING)
                .stream().filter(e -> e.getBillingPeriod().compareTo(current) < 0)
                .collect(Collectors.groupingBy(LateUsageAdjustment::getBillingPeriod, TreeMap::new, Collectors.toList()));
        for (var group : byPeriod.entrySet()) {
            String original = group.getKey();
            var originalInvoice = invoices.findByCustomerIdAndPeriod(ctx.customerId, original).orElse(null);
            if (originalInvoice == null) continue; // that period is not invoiced yet: its own run will include these events
            if (!originalInvoice.getCurrency().equals(ctx.plan.getCurrency())) continue;
            Plan originalPlan = originalInvoice.getPlanId() == null ? ctx.plan
                    : plans.findById(originalInvoice.getPlanId()).orElse(ctx.plan);
            YearMonth originalPeriod = YearMonth.parse(original);

            Map<UUID, UsageEvent> lateEvents = new HashMap<>();
            for (UsageEvent e : events.findAllById(group.getValue().stream().map(LateUsageAdjustment::getUsageEventId).toList()))
                lateEvents.put(e.getId(), e);
            List<UUID> billableIds = new ArrayList<>();
            BigDecimal lateQuantity = BigDecimal.ZERO;
            for (var entry : group.getValue()) {
                UsageEvent e = lateEvents.get(entry.getUsageEventId());
                if (e == null || !usage.matches(originalPlan, e)) {
                    ctx.ignored.add(entry);
                    continue;
                }
                billableIds.add(e.getId());
                lateQuantity = lateQuantity.add(e.getQuantity());
                ctx.billedAsLateUsage.add(entry);
            }
            if (billableIds.isEmpty()) continue;

            var previouslyLate = lateLedger.findByCustomerIdAndBillingPeriodAndStatus(ctx.customerId, original, LateUsageAdjustment.INVOICED);
            BigDecimal alreadyBilled;
            BigDecimal newQuantity;
            if (originalPlan.isAdditiveUnfiltered()) {
                // Sum metrics: billed quantity of the original invoice plus late usage already billed.
                alreadyBilled = originalInvoice.getBilledQuantity() == null ? BigDecimal.ZERO : originalInvoice.getBilledQuantity();
                if (!previouslyLate.isEmpty()) {
                    for (UsageEvent e : events.findAllById(previouslyLate.stream().map(LateUsageAdjustment::getUsageEventId).toList()))
                        if (usage.matches(originalPlan, e)) alreadyBilled = alreadyBilled.add(e.getQuantity());
                }
                newQuantity = alreadyBilled.add(lateQuantity);
            } else {
                // Non-additive metrics (count of filtered events, maximum, unique count, latest value):
                // re-aggregate the events already billed for the period, with and without the late ones.
                List<UUID> previousIds = new ArrayList<>(originalEventIds(originalInvoice));
                previouslyLate.forEach(e -> previousIds.add(e.getUsageEventId()));
                List<UsageEvent> previous = events.findAllById(previousIds);
                List<UsageEvent> combined = new ArrayList<>(previous);
                billableIds.forEach(id -> combined.add(lateEvents.get(id)));
                alreadyBilled = usage.quantify(originalPlan, previous).quantity();
                newQuantity = usage.quantify(originalPlan, combined).quantity();
                lateQuantity = newQuantity.subtract(alreadyBilled);
            }
            BigDecimal seats = BigDecimal.valueOf(originalInvoice.getSeatCount() == null ? ctx.subscription.getSeatCount() : originalInvoice.getSeatCount());
            BigDecimal before = pricing.quoteForPeriod(originalPlan, alreadyBilled, seats, ctx.subscription.getTrialEnd(), originalPeriod).subtotal();
            BigDecimal after = pricing.quoteForPeriod(originalPlan, newQuantity, seats, ctx.subscription.getTrialEnd(), originalPeriod).subtotal();
            String rule = originalPlan.getPlanKey() + " v" + originalPlan.getVersion() + " (" + originalPlan.getAggregation() + "): price("
                    + plain(newQuantity) + " with late usage) - price(" + plain(alreadyBilled) + " billed) = " + after.toPlainString() + " - " + before.toPlainString();
            ctx.builder.line(new BillingLine.LateUsage(original, originalPlan.getMetric(), lateQuantity, after.subtract(before), rule, billableIds));
        }
    }

    private static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }

    /** Source events of an invoice are stored on its first usage line that carries ids. */
    protected List<UUID> originalEventIds(Invoice invoice) {
        return lineRepository.findByInvoiceId(invoice.getId()).stream()
                .filter(l -> InvoiceLine.USAGE.equals(l.getKind()))
                .map(l -> InvoiceLine.idsFromJson(l.getSourceEventIdsJson()))
                .filter(list -> !list.isEmpty()).findFirst().orElse(List.of());
    }
}
