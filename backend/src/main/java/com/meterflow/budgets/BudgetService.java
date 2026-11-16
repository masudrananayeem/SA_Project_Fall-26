package com.meterflow.budgets;

import com.meterflow.metering.LiveSpendService;
import com.meterflow.metering.UsageRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Customer budgets with 50/80/100% alerts (FR7). Evaluated right after each ingestion batch
 * (asynchronously, via the UsageRecorded outbox event) and by a sweep every minute.
 */
@Service
public class BudgetService {
    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);
    static final int[] THRESHOLDS = {50, 80, 100};

    private final UsageBudgetRepository repo;
    private final LiveSpendService liveSpend;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;

    public BudgetService(UsageBudgetRepository repo, LiveSpendService liveSpend, ApplicationEventPublisher events,
                         PlatformTransactionManager transactionManager) {
        this.repo = repo;
        this.liveSpend = liveSpend;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public UsageBudget set(String customer, String period, BigDecimal amount, String currency) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Budget must be positive");
        YearMonth.parse(period);
        if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a three-letter ISO code");
        var budget = repo.findByCustomerIdAndPeriod(customer, period).orElseGet(() -> new UsageBudget(customer, period, amount, currency));
        if (!budget.getCurrency().equals(currency)) throw new IllegalArgumentException("Budget currency cannot change within a period");
        budget.update(amount);
        return repo.save(budget);
    }

    public Optional<UsageBudget> find(String customer, String period) { return repo.findByCustomerIdAndPeriod(customer, period); }

    @Transactional
    public Map<String, Object> evaluateIfConfigured(String customer, String period) {
        if (repo.findByCustomerIdAndPeriod(customer, period).isEmpty()) return Map.of("status", "NO_BUDGET");
        return evaluate(customer, period);
    }

    /** Pure threshold rule, shared with tests: which not-yet-notified thresholds are now crossed. */
    static List<Integer> newlyCrossed(BigDecimal spend, BigDecimal budget, UsageBudget state) {
        int percent = percentUsed(spend, budget);
        List<Integer> crossed = new ArrayList<>();
        for (int threshold : THRESHOLDS) if (percent >= threshold && !state.wasNotified(threshold)) crossed.add(threshold);
        return crossed;
    }

    static int percentUsed(BigDecimal spend, BigDecimal budget) {
        return spend.multiply(BigDecimal.valueOf(100)).divide(budget, 0, RoundingMode.DOWN).intValue();
    }

    @Transactional
    public Map<String, Object> evaluate(String customer, String period) {
        YearMonth.parse(period);
        var budget = repo.findByCustomerIdAndPeriod(customer, period).orElseThrow(() -> new IllegalArgumentException("Budget not configured"));
        var estimate = liveSpend.estimate(customer, period);
        BigDecimal amount = new BigDecimal(estimate.get("estimatedAmount").toString());
        if (estimate.containsKey("plan") && !budget.getCurrency().equals(estimate.get("currency")))
            throw new IllegalStateException("Budget and plan currency mismatch");
        List<Integer> fired = newlyCrossed(amount, budget.getAmount(), budget);
        for (int threshold : fired) {
            budget.mark(threshold);
            events.publishEvent(new BudgetThresholdReached(customer, period, threshold, amount, budget.getAmount(), budget.getCurrency()));
        }
        repo.save(budget);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customer);
        result.put("period", period);
        result.put("budget", budget.getAmount());
        result.put("currency", budget.getCurrency());
        result.put("estimatedAmount", amount);
        result.put("percentUsed", percentUsed(amount, budget.getAmount()));
        result.put("notifiedThresholds", budget.getNotified());
        result.put("newAlerts", fired);
        return result;
    }

    /** Runs after the ingestion transaction commits; failures are retried from the publication registry. */
    @ApplicationModuleListener
    public void on(UsageRecorded recorded) {
        for (String customer : recorded.customerIds())
            for (String period : recorded.periods())
                evaluateIfConfigured(customer, period);
    }

    /** Sweep of current-period budgets; default every 60 seconds (FR7: refreshed within 1 minute). */
    @Scheduled(fixedDelayString = "${meterflow.budgets.refresh-ms:60000}", initialDelayString = "${meterflow.budgets.initial-delay-ms:30000}")
    public void refreshCurrentPeriodBudgets() {
        String period = YearMonth.now(ZoneOffset.UTC).toString();
        for (var budget : repo.findByPeriod(period)) {
            try {
                // One transaction per customer so alert events are captured by the publication registry.
                transactions.executeWithoutResult(status -> evaluateIfConfigured(budget.getCustomerId(), period));
            } catch (RuntimeException e) {
                log.warn("Budget evaluation failed for customer {}: {}", budget.getCustomerId(), e.getMessage());
            }
        }
    }
}
