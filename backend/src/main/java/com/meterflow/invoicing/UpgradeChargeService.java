package com.meterflow.invoicing;

import com.meterflow.catalogue.Plan;
import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PricingService;
import com.meterflow.credits.CreditService;
import com.meterflow.subscriptions.PlanChangeRepository;
import com.meterflow.subscriptions.PlanChanged;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Settles a plan change that happens inside a month that is already invoiced (a prepaid package
 * billed up front). The month-end run will not bill that month again, so the difference is settled
 * now:
 * <ul>
 *   <li>Upgrade: an {@link UpgradeCharge} for the new plan's price over the remaining days minus
 *       the old plan's price over the same days, plus tax. The customer sees what was already
 *       paid, the new price and the balance still due, and is asked to pay it.</li>
 *   <li>Downgrade: the unused part is added to the customer's credit balance (credit note) and
 *       used on the next invoice.</li>
 * </ul>
 * A change in a month that is not invoiced yet (postpaid) needs nothing here: the month-end
 * invoice prorates it.
 */
@Service
public class UpgradeChargeService {
    private static final Logger log = LoggerFactory.getLogger(UpgradeChargeService.class);

    private final UpgradeChargeRepository charges;
    private final InvoiceRepository invoices;
    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final PlanChangeRepository changes;
    private final PricingService pricing;
    private final CreditService credits;

    public UpgradeChargeService(UpgradeChargeRepository charges, InvoiceRepository invoices, SubscriptionRepository subscriptions,
                                PlanRepository plans, PlanChangeRepository changes, PricingService pricing, CreditService credits) {
        this.charges = charges;
        this.invoices = invoices;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.changes = changes;
        this.pricing = pricing;
        this.credits = credits;
    }

    /**
     * Price of the new plan minus the old plan for the days that remain in the month, including the
     * effective day. Equals (new - old) + proration delta, so the customer pays each plan exactly
     * for the days it was active.
     */
    public static BigDecimal remainingDifference(BigDecimal oldRecurring, BigDecimal newRecurring, LocalDate effective) {
        YearMonth month = YearMonth.from(effective);
        long remaining = month.lengthOfMonth() - ChronoUnit.DAYS.between(month.atDay(1), effective);
        return newRecurring.subtract(oldRecurring)
                .multiply(BigDecimal.valueOf(remaining))
                .divide(BigDecimal.valueOf(month.lengthOfMonth()), 2, RoundingMode.HALF_UP);
    }

    /** Asynchronous, after the plan change is committed. Idempotent per plan change. */
    @ApplicationModuleListener
    public void on(PlanChanged changed) { settle(changed); }

    @Transactional
    public Optional<UpgradeCharge> settle(PlanChanged changed) {
        if (charges.findByPlanChangeId(changed.planChangeId()).isPresent()) return charges.findByPlanChangeId(changed.planChangeId());
        var change = changes.findById(changed.planChangeId()).orElse(null);
        if (change == null || change.getInvoiceId() != null) return Optional.empty();
        YearMonth period = YearMonth.from(changed.effectiveDate());
        var invoice = invoices.findByCustomerIdAndPeriod(changed.customerId(), period.toString()).orElse(null);
        if (invoice == null) return Optional.empty();                       // not invoiced yet: month-end run prorates it
        var sub = subscriptions.findById(changed.subscriptionId()).orElse(null);
        Plan from = plans.findById(changed.fromPlanId()).orElse(null);
        Plan to = plans.findById(changed.toPlanId()).orElse(null);
        if (sub == null || from == null || to == null || !from.getCurrency().equals(to.getCurrency())) return Optional.empty();

        // The change is settled here, so later invoices must not prorate it again.
        change.markInvoiced(invoice.getId());
        changes.save(change);
        if (sub.getTrialEnd() != null && changed.effectiveDate().isBefore(sub.getTrialEnd())) return Optional.empty();

        BigDecimal seats = BigDecimal.valueOf(sub.getSeatCount());
        BigDecimal oldRecurring = pricing.recurringAmount(from, seats);
        BigDecimal newRecurring = pricing.recurringAmount(to, seats);
        BigDecimal difference = remainingDifference(oldRecurring, newRecurring, changed.effectiveDate());
        String fromLabel = from.getName() + " (" + from.getPlanKey() + " v" + from.getVersion() + ")";
        String toLabel = to.getName() + " (" + to.getPlanKey() + " v" + to.getVersion() + ")";
        int days = period.lengthOfMonth();
        int remaining = (int) (days - ChronoUnit.DAYS.between(period.atDay(1), changed.effectiveDate()));

        if (difference.signum() < 0) {
            credits.issueNote(changed.customerId(), to.getCurrency(), difference.negate(),
                    "Downgrade " + fromLabel + " -> " + toLabel + " on " + changed.effectiveDate() + ": unused " + remaining + " of " + days + " days");
            log.info("Downgrade credit {} {} for {}", difference.negate(), to.getCurrency(), changed.customerId());
            return Optional.empty();
        }
        if (difference.signum() == 0) return Optional.empty();

        TaxVisitor vat = TaxVisitor.vat(to.getTaxRate());
        BigDecimal tax = difference.multiply(to.getTaxRate()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal alreadyPaid = "PAID".equals(invoice.getStatus()) ? invoice.getTotal() : BigDecimal.ZERO.setScale(2);
        String explanation = "Upgrade " + fromLabel + " -> " + toLabel + " on " + changed.effectiveDate() + ": ("
                + newRecurring.toPlainString() + " - " + oldRecurring.toPlainString() + ") x " + remaining + "/" + days
                + " remaining days = " + difference.toPlainString() + " + " + vat.describe();
        var charge = charges.save(new UpgradeCharge(changed.customerId(), sub.getId(), change.getId(), invoice.getId(),
                period.toString(), to.getCurrency(), fromLabel, toLabel, changed.effectiveDate(), remaining, days,
                invoice.getTotal(), alreadyPaid, oldRecurring, newRecurring, difference, tax, vat.describe(), explanation));
        log.info("Upgrade balance due {} {} for {}", charge.getTotal(), charge.getCurrency(), changed.customerId());
        return Optional.of(charge);
    }

    /**
     * What an immediate change to {@code toPlanId} would cost today, before the customer confirms:
     * SETTLE_NOW (pay the difference now), CREDIT (unused part goes to the credit balance),
     * NEXT_INVOICE (postpaid: prorated on the month-end invoice) or NONE.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> preview(UUID subscriptionId, UUID toPlanId, String requester, boolean admin) {
        var sub = subscriptions.findById(subscriptionId).orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        if (!admin && !sub.getCustomerId().equals(requester))
            throw new org.springframework.security.access.AccessDeniedException("Customers may only preview their own subscription");
        Plan from = plans.findById(sub.getPlanId()).orElseThrow();
        Plan to = plans.findById(toPlanId).orElseThrow(() -> new IllegalArgumentException("Plan not found"));
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate effective = today.isBefore(sub.getPeriodStart()) ? sub.getPeriodStart() : today;
        YearMonth period = YearMonth.from(effective);
        BigDecimal seats = BigDecimal.valueOf(sub.getSeatCount());
        BigDecimal oldRecurring = pricing.recurringAmount(from, seats);
        BigDecimal newRecurring = pricing.recurringAmount(to, seats);
        BigDecimal difference = remainingDifference(oldRecurring, newRecurring, effective);
        BigDecimal tax = difference.max(BigDecimal.ZERO).multiply(to.getTaxRate()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        var invoice = invoices.findByCustomerIdAndPeriod(sub.getCustomerId(), period.toString()).orElse(null);
        boolean crossMode = from.isPrepaid() != to.isPrepaid();
        boolean inTrial = sub.getTrialEnd() != null && effective.isBefore(sub.getTrialEnd());
        String mode;
        if (crossMode) mode = "PERIOD_END_ONLY";
        else if (inTrial || difference.signum() == 0) mode = "NONE";
        else if (invoice == null) mode = "NEXT_INVOICE";
        else mode = difference.signum() > 0 ? "SETTLE_NOW" : "CREDIT";
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("mode", mode);
        m.put("fromPlan", from.getName());
        m.put("toPlan", to.getName());
        m.put("currency", to.getCurrency());
        m.put("effectiveDate", effective.toString());
        m.put("remainingDays", period.lengthOfMonth() - ChronoUnit.DAYS.between(period.atDay(1), effective));
        m.put("daysInPeriod", period.lengthOfMonth());
        m.put("oldRecurring", oldRecurring);
        m.put("newRecurring", newRecurring);
        m.put("difference", difference);
        m.put("tax", tax);
        m.put("totalDueNow", "SETTLE_NOW".equals(mode) ? difference.add(tax) : BigDecimal.ZERO.setScale(2));
        m.put("creditNow", "CREDIT".equals(mode) ? difference.negate() : BigDecimal.ZERO.setScale(2));
        m.put("alreadyInvoiced", invoice == null ? BigDecimal.ZERO.setScale(2) : invoice.getTotal());
        m.put("alreadyPaid", invoice != null && "PAID".equals(invoice.getStatus()) ? invoice.getTotal() : BigDecimal.ZERO.setScale(2));
        m.put("monthlyPriceAfter", newRecurring.add(newRecurring.multiply(to.getTaxRate()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)));
        return m;
    }

    public List<UpgradeCharge> forCustomer(String customerId) { return charges.findByCustomerIdOrderByCreatedAtDesc(customerId); }

    public List<UpgradeCharge> recent() { return charges.findTop200ByOrderByCreatedAtDesc(); }
}
