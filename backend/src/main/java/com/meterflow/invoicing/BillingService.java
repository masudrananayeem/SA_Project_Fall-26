package com.meterflow.invoicing;

import com.meterflow.catalogue.PlanRepository;
import com.meterflow.metering.BillableUsageCalculator;
import com.meterflow.metering.UsageEvent;
import com.meterflow.metering.UsageEventRepository;
import com.meterflow.subscriptions.SubscriptionRepository;
import com.meterflow.subscriptions.SubscriptionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;

/** Facade used by the controller and the Spring Batch job. */
@Service
public class BillingService {
    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    static final int TRACE_ID_LIMIT = 1000;

    private final InvoiceBillingRun invoiceRun;
    private final SimulationBillingRun simulationRun;
    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final UsageEventRepository events;
    private final ApplicationEventPublisher publisher;
    private final BillableUsageCalculator usage;

    public BillingService(InvoiceBillingRun invoiceRun, SimulationBillingRun simulationRun, SubscriptionRepository subscriptions,
                          PlanRepository plans, InvoiceRepository invoices, InvoiceLineRepository lines,
                          UsageEventRepository events, ApplicationEventPublisher publisher, BillableUsageCalculator usage) {
        this.invoiceRun = invoiceRun;
        this.simulationRun = simulationRun;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.invoices = invoices;
        this.lines = lines;
        this.events = events;
        this.publisher = publisher;
        this.usage = usage;
    }

    static YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period);
        } catch (Exception e) {
            throw new IllegalArgumentException("period must be YYYY-MM");
        }
    }

    /** Bills one customer (used by each Spring Batch chunk). Idempotent. */
    public Map<String, Object> runCustomer(String period, String customer) {
        return invoiceRun.run(customer, parsePeriod(period)).toMap();
    }

    /** Simple sequential run for small installations and tests; production uses the batch job. */
    public Map<String, Object> run(String period) {
        YearMonth ym = parsePeriod(period);
        List<BillingOutcome> outcomes = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (String customer : subscriptions.findBillableCustomerIds(ym.atDay(1))) {
            try {
                outcomes.add(invoiceRun.run(customer, ym));
            } catch (RuntimeException e) {
                log.warn("Billing failed for customer {} period {}: {}", customer, period, e.getMessage());
                failures.add(customer + ": " + e.getMessage());
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>(BillingOutcome.summary(period, outcomes));
        summary.put("failed", failures);
        return summary;
    }

    public List<String> billableCustomers() {
        return subscriptions.findByStatus(SubscriptionState.ACTIVE.name()).stream()
                .map(s -> s.getCustomerId()).distinct().sorted().toList();
    }

    /** FR10: previous month by default, under the subscription's plan or any plan version. */
    public Map<String, Object> simulate(String customer, String period, UUID planId) {
        YearMonth ym = period == null || period.isBlank() ? YearMonth.now(ZoneOffset.UTC).minusMonths(1) : parsePeriod(period);
        return simulationRun.simulate(customer, ym, planId).toMap();
    }

    public Map<String, Object> simulate(String customer, String period) { return simulate(customer, period, null); }

    /** Simulates the month under every version of the customer's plan key, side by side. */
    @Transactional(readOnly = true)
    public Map<String, Object> compareVersions(String customer, String period) {
        var sub = subscriptions.findByCustomerIdOrderByPeriodStartDesc(customer).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Customer has no subscription"));
        var current = plans.findById(sub.getPlanId()).orElseThrow();
        List<Map<String, Object>> results = new ArrayList<>();
        for (var version : plans.findByPlanKeyOrderByVersionDesc(current.getPlanKey())) {
            Map<String, Object> row = new LinkedHashMap<>(simulate(customer, period, version.getId()));
            row.put("planId", version.getId());
            row.put("planVersion", version.getVersion());
            row.put("current", version.getId().equals(current.getId()));
            results.add(row);
        }
        return Map.of("customerId", customer, "planKey", current.getPlanKey(), "simulations", results);
    }

    @Transactional
    public Invoice finalise(UUID invoiceId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new NoSuchElementException("Invoice not found"));
        invoice.finalise();
        var saved = invoices.save(invoice);
        publisher.publishEvent(new InvoiceFinalised(saved.getId(), saved.getCustomerId(), saved.getPeriod(), saved.getTotal(), saved.getCurrency()));
        return saved;
    }

    @Transactional(readOnly = true)
    public InvoiceDocument document(UUID invoiceId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new NoSuchElementException("Invoice not found"));
        String planLabel = invoice.getPlanId() == null ? "" : plans.findById(invoice.getPlanId())
                .map(p -> p.getPlanKey() + " v" + p.getVersion()).orElse("");
        var billingLines = lines.findByInvoiceId(invoiceId).stream().map(InvoiceLine::toBillingLine).toList();
        return new InvoiceDocument(invoice.getCustomerId(), invoice.getPeriod(), invoice.getCurrency(), invoice.getPlanId(), planLabel,
                invoice.getBilledQuantity() == null ? BigDecimal.ZERO : invoice.getBilledQuantity(),
                invoice.getSeatCount() == null ? 1 : invoice.getSeatCount(), billingLines, invoice.getSubtotal(), invoice.getTax(),
                invoice.getTaxRule() == null ? "" : invoice.getTaxRule(), invoice.getCreditApplied(), invoice.getTotal());
    }

    /**
     * QA5: one call explains a line: its quantity, price rule, tax rule and the usage events behind
     * it, with the quantity recomputed from those events.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> trace(UUID lineId) {
        var line = lines.findById(lineId).orElseThrow(() -> new NoSuchElementException("Invoice line not found"));
        var invoice = invoices.findById(line.getInvoiceId()).orElseThrow();
        List<UUID> ids;
        if (InvoiceLine.USAGE.equals(line.getKind())) {
            // Usage lines of one invoice share the event list stored on its first usage line.
            ids = lines.findByInvoiceId(invoice.getId()).stream()
                    .filter(l -> InvoiceLine.USAGE.equals(l.getKind()))
                    .map(l -> InvoiceLine.idsFromJson(l.getSourceEventIdsJson()))
                    .filter(list -> !list.isEmpty()).findFirst().orElse(List.of());
        } else {
            ids = InvoiceLine.idsFromJson(line.getSourceEventIdsJson());
        }
        List<UsageEvent> found = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 1000) found.addAll(events.findAllById(ids.subList(i, Math.min(ids.size(), i + 1000))));
        var plan = invoice.getPlanId() == null ? null : plans.findById(invoice.getPlanId()).orElse(null);
        BigDecimal summed = found.stream().map(UsageEvent::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean usageLine = InvoiceLine.USAGE.equals(line.getKind());
        // Usage lines: re-aggregate the source events with the plan's metric definition (FR2).
        BigDecimal recomputed = usageLine && plan != null ? usage.quantify(plan, found).quantity() : summed;
        BigDecimal billed = usageLine
                ? (invoice.getBilledQuantity() == null ? line.getQuantity() : invoice.getBilledQuantity())
                : line.getQuantity();
        boolean comparable = usageLine || plan == null || plan.isAdditiveUnfiltered();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("invoiceId", invoice.getId());
        result.put("customerId", invoice.getCustomerId());
        result.put("period", invoice.getPeriod());
        result.put("lineId", line.getId());
        result.put("kind", line.getKind());
        result.put("line", line.getLineName());
        result.put("metric", line.getMetric());
        result.put("quantity", line.getQuantity());
        result.put("amount", line.getAmount());
        result.put("priceRule", line.getPriceRule());
        result.put("taxRule", line.getTaxRule());
        result.put("eventCount", ids.size());
        result.put("eventsFound", found.size());
        result.put("aggregation", plan == null ? "sum" : plan.getAggregation());
        result.put("billedUsageQuantity", billed);
        result.put("recomputedQuantity", recomputed);
        result.put("quantityMatches", comparable ? recomputed.compareTo(billed) == 0 : null);
        result.put("eventIds", ids.size() > TRACE_ID_LIMIT ? ids.subList(0, TRACE_ID_LIMIT) : ids);
        result.put("eventIdsTruncated", ids.size() > TRACE_ID_LIMIT);
        return result;
    }
}
