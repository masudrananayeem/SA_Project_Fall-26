package com.meterflow.invoicing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meterflow.catalogue.Plan;
import com.meterflow.catalogue.PlanRepository;
import com.meterflow.catalogue.PricingService;
import com.meterflow.credits.CreditAccountRepository;
import com.meterflow.credits.CreditNoteRepository;
import com.meterflow.credits.CreditService;
import com.meterflow.metering.*;
import com.meterflow.subscriptions.PlanChange;
import com.meterflow.subscriptions.PlanChangeRepository;
import com.meterflow.subscriptions.Subscription;
import com.meterflow.subscriptions.SubscriptionRepository;

import java.lang.reflect.Field;
import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Wires the real UsageService, InvoiceBillingRun and SimulationBillingRun to in-memory
 * repositories (Mockito answers backed by collections). Only the database is replaced; the
 * ingestion rules, late-usage ledger, pricing and billing steps are the production code.
 */
public class BillingTestWorld {
    public final InMemoryUsageEventStore store = new InMemoryUsageEventStore();
    public final Map<UUID, Plan> plans = new HashMap<>();
    public final Map<String, Subscription> subscriptions = new LinkedHashMap<>();
    public final Map<String, Invoice> invoices = new HashMap<>();
    public final Map<UUID, Invoice> invoicesById = new HashMap<>();
    public final Map<UUID, List<InvoiceLine>> lines = new HashMap<>();
    public final Map<String, List<LateUsageAdjustment>> ledger = new HashMap<>();
    public final List<PlanChange> planChanges = new ArrayList<>();
    public final List<Object> published = new ArrayList<>();

    public final PricingService pricing = new PricingService();
    public final UsageService usage;
    public final InvoiceBillingRun billing;
    public final SimulationBillingRun simulation;
    public final PrepaidBillingRun prepaid;

    public BillingTestWorld(boolean allowOpenPeriod) {
        var eventRepo = mock(UsageEventRepository.class);
        when(eventRepo.findMetricWindow(any(), any(), any(), any())).thenAnswer(inv ->
                store.window(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
        when(eventRepo.findAllById(any())).thenAnswer(inv -> {
            Iterable<UUID> ids = inv.getArgument(0);
            List<UsageEvent> found = new ArrayList<>();
            for (UUID id : ids) { var e = store.byId(id); if (e != null) found.add(e); }
            return found;
        });

        var ledgerRepo = mock(LateUsageAdjustmentRepository.class);
        Set<LateUsageAdjustment> known = Collections.newSetFromMap(new IdentityHashMap<>());
        when(ledgerRepo.saveAll(any())).thenAnswer(inv -> {
            Iterable<LateUsageAdjustment> rows = inv.getArgument(0);
            List<LateUsageAdjustment> saved = new ArrayList<>();
            for (var row : rows) {
                if (known.add(row)) ledger.computeIfAbsent(row.getCustomerId(), k -> new ArrayList<>()).add(row);
                saved.add(row);
            }
            return saved;
        });
        when(ledgerRepo.findByCustomerIdAndStatus(any(), any())).thenAnswer(inv -> ledger.getOrDefault(inv.<String>getArgument(0), List.of())
                .stream().filter(r -> r.getStatus().equals(inv.getArgument(1))).toList());
        when(ledgerRepo.findByCustomerIdAndBillingPeriodAndStatus(any(), any(), any())).thenAnswer(inv -> ledger.getOrDefault(inv.<String>getArgument(0), List.of())
                .stream().filter(r -> r.getBillingPeriod().equals(inv.getArgument(1)) && r.getStatus().equals(inv.getArgument(2))).toList());

        var subRepo = mock(SubscriptionRepository.class);
        when(subRepo.findTopByCustomerIdAndStatusOrderByPeriodStartDesc(any(), any())).thenAnswer(inv -> {
            var s = subscriptions.get(inv.<String>getArgument(0));
            return s != null && s.getStatus().equals(inv.getArgument(1)) ? Optional.of(s) : Optional.empty();
        });
        when(subRepo.findByCustomerIdOrderByPeriodStartDesc(any())).thenAnswer(inv -> {
            var s = subscriptions.get(inv.<String>getArgument(0));
            return s == null ? List.of() : List.of(s);
        });
        when(subRepo.findByStatus(any())).thenAnswer(inv -> subscriptions.values().stream()
                .filter(s -> s.getStatus().equals(inv.getArgument(0))).toList());

        var planRepo = mock(PlanRepository.class);
        when(planRepo.findById(any())).thenAnswer(inv -> Optional.ofNullable(plans.get(inv.<UUID>getArgument(0))));

        var changeRepo = mock(PlanChangeRepository.class);
        when(changeRepo.findBySubscriptionIdAndInvoiceIdIsNullAndEffectiveDateBefore(any(), any())).thenAnswer(inv -> planChanges.stream()
                .filter(c -> c.getSubscriptionId().equals(inv.getArgument(0)) && c.getInvoiceId() == null
                        && c.getEffectiveDate().isBefore(inv.getArgument(1))).toList());
        when(changeRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var invoiceRepo = mock(InvoiceRepository.class);
        when(invoiceRepo.findByCustomerIdAndPeriod(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(invoices.get(inv.getArgument(0) + "|" + inv.getArgument(1))));
        when(invoiceRepo.saveAndFlush(any())).thenAnswer(inv -> {
            Invoice invoice = inv.getArgument(0);
            String key = invoice.getCustomerId() + "|" + invoice.getPeriod();
            if (invoice.getId() == null) {
                if (invoices.containsKey(key)) throw new org.springframework.dao.DataIntegrityViolationException("uk_invoice_customer_period");
                setId(invoice, UUID.randomUUID());
            }
            invoices.put(key, invoice);
            invoicesById.put(invoice.getId(), invoice);
            return invoice;
        });
        when(invoiceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepo.findById(any())).thenAnswer(inv -> Optional.ofNullable(invoicesById.get(inv.<UUID>getArgument(0))));

        var lineRepo = mock(InvoiceLineRepository.class);
        when(lineRepo.findByInvoiceId(any())).thenAnswer(inv -> lines.getOrDefault(inv.<UUID>getArgument(0), List.of()));
        when(lineRepo.saveAll(any())).thenAnswer(inv -> {
            Iterable<InvoiceLine> rows = inv.getArgument(0);
            List<InvoiceLine> saved = new ArrayList<>();
            for (var row : rows) {
                if (row.getId() == null) setId(row, UUID.randomUUID());
                lines.computeIfAbsent(row.getInvoiceId(), k -> new ArrayList<>()).add(row);
                saved.add(row);
            }
            return saved;
        });

        var accounts = mock(CreditAccountRepository.class);
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var credits = new CreditService(accounts, mock(CreditNoteRepository.class));

        usage = new UsageService(eventRepo, new ObjectMapper(), store, ledgerRepo, published::add);
        var calculator = new BillableUsageCalculator(new ObjectMapper());
        billing = new InvoiceBillingRun(subRepo, planRepo, eventRepo, pricing, changeRepo, ledgerRepo, invoiceRepo, lineRepo,
                calculator, credits, published::add, true, allowOpenPeriod);
        simulation = new SimulationBillingRun(subRepo, planRepo, eventRepo, pricing, changeRepo, ledgerRepo, invoiceRepo, lineRepo,
                calculator, credits);
        prepaid = new PrepaidBillingRun(subRepo, planRepo, eventRepo, pricing, changeRepo, ledgerRepo, invoiceRepo, lineRepo,
                calculator, credits, published::add);
    }

    public static void setId(Object entity, UUID id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public Plan addPlan(Plan plan) {
        setId(plan, UUID.randomUUID());
        plans.put(plan.getId(), plan);
        return plan;
    }

    public Subscription subscribe(String customer, Plan plan, java.time.LocalDate start) {
        var s = new Subscription(customer, plan.getId(), start, start.plusYears(1));
        setId(s, UUID.randomUUID());
        subscriptions.put(customer, s);
        return s;
    }

    public List<InvoiceLine> linesOf(Invoice invoice) { return lines.getOrDefault(invoice.getId(), List.of()); }
}
