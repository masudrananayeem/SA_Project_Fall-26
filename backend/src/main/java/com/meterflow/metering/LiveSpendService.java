package com.meterflow.metering;

import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PriceQuote;
import com.meterflow.catalogue.PricedCharge;
import com.meterflow.catalogue.PricingService;
import com.meterflow.subscriptions.SubscriptionRepository;
import com.meterflow.subscriptions.SubscriptionState;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live spend (FR7). The estimate is computed with the same {@link PricingService#quoteForPeriod}
 * and tax rule as the billing run, so for the same events it equals the invoice charges (QA5).
 * Usage is summed in the database; results are cached for {@value #REFRESH_SECONDS} seconds.
 */
@Service
public class LiveSpendService {
    public static final String CACHE = "liveEstimate";
    public static final int REFRESH_SECONDS = 30;

    private final UsageEventRepository events;
    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final PricingService pricing;
    private final BillableUsageCalculator usage;

    public LiveSpendService(UsageEventRepository events, SubscriptionRepository subscriptions, PlanRepository plans,
                            PricingService pricing, BillableUsageCalculator usage) {
        this.events = events;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.pricing = pricing;
        this.usage = usage;
    }

    public static String currentPeriod() { return YearMonth.now(ZoneOffset.UTC).toString(); }

    /** Cached view for the API and the customer page (refreshed at least every 30 seconds). */
    @Cacheable(cacheNames = CACHE, key = "#customerId + ':' + #period")
    @Transactional(readOnly = true)
    public Map<String, Object> cachedEstimate(String customerId, String period) { return estimate(customerId, period); }

    /** Uncached estimate, used by budget evaluation and tests. */
    @Transactional(readOnly = true)
    public Map<String, Object> estimate(String customerId, String period) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(period);
        } catch (Exception ex) {
            throw new IllegalArgumentException("period must be YYYY-MM");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("period", period);
        result.put("asOf", Instant.now());
        result.put("refreshSeconds", REFRESH_SECONDS);
        var active = subscriptions.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(customerId, SubscriptionState.ACTIVE.name());
        if (active.isEmpty()) {
            result.put("estimatedAmount", BigDecimal.ZERO.setScale(2));
            result.put("currency", "BDT");
            result.put("note", "No active subscription");
            return result;
        }
        var subscription = active.get();
        var plan = plans.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Subscription references a missing plan"));
        Instant from = ym.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal quantity;
        long count;
        if (plan.isAdditiveUnfiltered()) {
            quantity = events.sumQuantity(customerId, plan.getMetric(), from, to);   // summed in the database
            if (quantity == null) quantity = BigDecimal.ZERO;
            count = events.countInWindow(customerId, plan.getMetric(), from, to);
        } else {
            var billable = usage.quantify(plan, events.findMetricWindow(customerId, plan.getMetric(), from, to));
            quantity = billable.quantity();
            count = billable.matchedEvents().size();
        }

        PriceQuote quote = pricing.quoteForPeriod(plan, quantity, BigDecimal.valueOf(subscription.getSeatCount()),
                subscription.getTrialEnd(), ym);
        BigDecimal tax = taxOn(quote.subtotal(), plan.getTaxRate());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (PricedCharge line : quote.lines()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", line.name());
            row.put("type", line.type());
            row.put("quantity", line.quantity());
            row.put("amount", line.amount());
            row.put("rule", line.rule());
            lines.add(row);
        }
        result.put("plan", quote.planLabel());
        result.put("currency", quote.currency());
        result.put("billableMetric", plan.getMetric());
        result.put("aggregation", plan.getAggregation());
        result.put("billableQuantity", quantity);
        result.put("eventCount", count);
        result.put("lines", lines);
        result.put("estimatedSubtotal", quote.subtotal());
        result.put("taxRate", plan.getTaxRate());
        result.put("estimatedTax", tax);
        result.put("estimatedAmount", quote.subtotal().add(tax));
        if (PricingService.fullyInTrial(subscription.getTrialEnd(), ym)) result.put("note", "Whole period is inside the free trial");
        return result;
    }

    /** Same tax rule as the invoice: rate% of the taxable charges, rounded HALF_UP to 2 places. */
    public static BigDecimal taxOn(BigDecimal taxable, BigDecimal ratePercent) {
        BigDecimal rate = ratePercent == null ? BigDecimal.ZERO : ratePercent;
        return taxable.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
