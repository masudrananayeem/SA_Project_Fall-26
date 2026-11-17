import { useMemo, useState } from 'react';
import { Bell, FileText, Search, Users } from 'lucide-react';
import {
  Badge, Button, Card, Drawer, EstimateBreakdown, Empty, ErrorNote, InvoiceTable, KeyValues, PageHeader, Progress, Spinner, StatusBadge, Table, Tabs,
  currentPeriod, dateTime, get, money, periodLabel, post, qs, qty, timeAgo, toNumber, useAction, useAsync,
  type BillingOutcome, type BudgetEvaluation, type CreditAccount, type CustomerNotification, type Estimate, type Invoice, type LateAdjustment, type Subscription, type UsageSummary,
} from '@meterflow/shared';
import { planLabel, usePlans, useSubscriptions, type Navigate } from '../common';

export function CustomersPage({ selected, navigate }: { selected?: string; navigate: Navigate }) {
  const subs = useSubscriptions();
  const plans = usePlans();
  const [filter, setFilter] = useState('');

  const customers = useMemo(() => {
    const map = new Map<string, Subscription[]>();
    subs.data?.forEach((s) => map.set(s.customerId, [...(map.get(s.customerId) || []), s]));
    return [...map.entries()]
      .map(([id, list]) => {
        const sorted = list.slice().sort((a, b) => b.periodStart.localeCompare(a.periodStart));
        return { id, current: sorted.find((s) => s.status !== 'CANCELLED') || sorted[0], count: list.length };
      })
      .filter((c) => c.id.toLowerCase().includes(filter.toLowerCase()))
      .sort((a, b) => a.id.localeCompare(b.id));
  }, [subs.data, filter]);

  const customerId = selected ? decodeURIComponent(selected) : undefined;

  return (
    <>
      <PageHeader title="Customers" subtitle="Everyone with a subscription. Open a customer to see live spend, invoices, credits, budget and notifications." />
      <ErrorNote error={subs.error} onRetry={subs.reload} />
      <Card className="flush">
        <div className="toolbar">
          <div className="input-icon search">
            <Search size={16} />
            <input placeholder="Search customer ID" value={filter} onChange={(e) => setFilter(e.target.value)} />
          </div>
          <span className="muted small">{customers.length} customers</span>
        </div>
        <div className="flush-body">
          {subs.loading && !subs.data ? (
            <Spinner />
          ) : customers.length ? (
            <Table>
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Current plan</th>
                  <th>Status</th>
                  <th>Seats</th>
                  <th>Billing period</th>
                  <th>Subscriptions</th>
                </tr>
              </thead>
              <tbody>
                {customers.map((c) => (
                  <tr key={c.id} className="clickable" onClick={() => navigate(`customers/${encodeURIComponent(c.id)}`)}>
                    <td className="strong">{c.id}</td>
                    <td>{planLabel(plans.data, c.current.planId)}</td>
                    <td>
                      <StatusBadge status={c.current.status} /> {c.current.trialing && <Badge tone="info">Trial</Badge>}
                    </td>
                    <td>{c.current.seatCount}</td>
                    <td className="muted">
                      {c.current.periodStart} → {c.current.periodEnd}
                    </td>
                    <td>{c.count}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          ) : (
            <Empty icon={<Users size={22} />} title="No customers yet">
              Customers appear when they get a subscription.
            </Empty>
          )}
        </div>
      </Card>
      <Drawer open={!!customerId} title={customerId} subtitle="Customer 360" onClose={() => navigate('customers')}>
        {customerId && <CustomerDetail customerId={customerId} subs={(subs.data || []).filter((s) => s.customerId === customerId)} navigate={navigate} />}
      </Drawer>
    </>
  );
}

function CustomerDetail({ customerId, subs, navigate }: { customerId: string; subs: Subscription[]; navigate: Navigate }) {
  const [tab, setTab] = useState<'spend' | 'invoices' | 'account' | 'activity'>('spend');
  const period = currentPeriod();
  const plans = usePlans();
  const estimate = useAsync(() => get<Estimate>(`/usage/estimate${qs({ customerId, period })}`), [customerId]);
  const summary = useAsync(() => get<UsageSummary>(`/usage/summary${qs({ customerId })}`), [customerId]);
  const invoices = useAsync(() => get<Invoice[]>(`/invoices${qs({ customerId })}`), [customerId]);
  const { busy, run } = useAction();
  const [outcome, setOutcome] = useState<BillingOutcome | null>(null);

  const billNow = async () => {
    const r = await run('bill', () => post<BillingOutcome>(`/invoices/run/customer${qs({ customerId, period })}`), (o) =>
      o.status === 'CREATED' ? 'Invoice created and finalised.' : o.status === 'EXISTING' ? 'Invoice for this period already exists (idempotent).' : `Skipped: ${o.reason || o.status}`,
    );
    if (r) {
      setOutcome(r);
      await invoices.reload();
    }
  };

  return (
    <div className="stack">
      <div className="btn-row">
        <Button variant="primary" icon={<FileText size={16} />} loading={busy === 'bill'} onClick={billNow}>
          Bill {periodLabel(period)} now
        </Button>
        <Button onClick={() => estimate.reload()} loading={estimate.loading && !!estimate.data}>
          Refresh estimate
        </Button>
      </div>
      {outcome?.invoiceId && (
        <div className="alert alert-good">
          <div>
            Invoice {outcome.status === 'CREATED' ? 'created' : 'already existed'} — {money(outcome.total, outcome.currency)}.{' '}
            <a href={`#/invoices/${outcome.invoiceId}`}>Open invoice →</a>
          </div>
        </div>
      )}
      <Tabs
        value={tab}
        onChange={setTab}
        tabs={[
          { value: 'spend', label: 'Live spend' },
          { value: 'invoices', label: 'Invoices' },
          { value: 'account', label: 'Account' },
          { value: 'activity', label: 'Activity' },
        ]}
      />

      {tab === 'spend' && (
        <div className="stack">
          {estimate.error ? (
            <ErrorNote error={estimate.error} onRetry={estimate.reload} />
          ) : !estimate.data ? (
            <Spinner />
          ) : (
            <>
              <div className="stats">
                <div className="stat">
                  <div className="stat-label">Estimated {periodLabel(estimate.data.period)}</div>
                  <div className="stat-value">{money(estimate.data.estimatedAmount, estimate.data.currency)}</div>
                  <div className="stat-hint">as of {dateTime(estimate.data.asOf)}</div>
                </div>
                <div className="stat">
                  <div className="stat-label">Billable {estimate.data.billableMetric || 'usage'}</div>
                  <div className="stat-value">{qty(estimate.data.billableQuantity)}</div>
                  <div className="stat-hint">{estimate.data.eventCount ?? 0} events · {estimate.data.aggregation || 'sum'}</div>
                </div>
              </div>
              <EstimateBreakdown estimate={estimate.data} />
            </>
          )}
          {summary.data && (
            <div>
              <h4 className="section-title">Usage this month</h4>
              <KeyValues items={[['Events', summary.data.eventCount.toLocaleString()], ...Object.entries(summary.data.usage).map(([k, v]) => [k, qty(v)] as [string, string])]} />
            </div>
          )}
        </div>
      )}

      {tab === 'invoices' && (invoices.loading && !invoices.data ? <Spinner /> : <InvoiceTable invoices={invoices.data || []} showCustomer={false} onOpen={(i) => navigate(`invoices/${i.id}`)} />)}

      {tab === 'account' && <AccountTab customerId={customerId} subs={subs} planName={(id) => planLabel(plans.data, id)} />}
      {tab === 'activity' && <ActivityTab customerId={customerId} />}
    </div>
  );
}

function AccountTab({ customerId, subs, planName }: { customerId: string; subs: Subscription[]; planName: (id: string) => string }) {
  const period = currentPeriod();
  const credits = useAsync(() => get<CreditAccount>(`/credits${qs({ customerId })}`), [customerId]);
  const budget = useAsync<BudgetEvaluation>(async () => {
    const b = await get<BudgetEvaluation>(`/budgets${qs({ customerId, period })}`);
    if (b.status === 'NO_BUDGET') return b;
    return get<BudgetEvaluation>(`/budgets/evaluate${qs({ customerId, period })}`);
  }, [customerId]);
  return (
    <div className="stack">
      <div>
        <h4 className="section-title">Subscriptions</h4>
        <Table className="compact">
          <thead>
            <tr>
              <th>Plan</th>
              <th>Status</th>
              <th>Period</th>
              <th>Seats</th>
            </tr>
          </thead>
          <tbody>
            {subs.map((s) => (
              <tr key={s.id} className="clickable" onClick={() => (window.location.hash = `/subscriptions/${s.id}`)}>
                <td>{planName(s.planId)}</td>
                <td>
                  <StatusBadge status={s.status} />
                </td>
                <td className="muted">
                  {s.periodStart} → {s.periodEnd}
                </td>
                <td>{s.seatCount}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      </div>
      <KeyValues
        items={[
          ['Prepaid credit balance', credits.data ? money(credits.data.balance, credits.data.currency) : '…'],
          [
            `Budget ${periodLabel(period)}`,
            budget.data?.status === 'NO_BUDGET' ? 'Not set' : budget.data ? `${money(budget.data.budget, budget.data.currency)} · ${toNumber(budget.data.percentUsed)}% used` : '…',
          ],
        ]}
      />
      {budget.data && budget.data.status !== 'NO_BUDGET' && <Progress value={toNumber(budget.data.percentUsed)} />}
      <ErrorNote error={credits.error || budget.error} />
    </div>
  );
}

function ActivityTab({ customerId }: { customerId: string }) {
  const notes = useAsync(() => get<CustomerNotification[]>(`/notifications${qs({ customerId })}`), [customerId]);
  const late = useAsync(() => get<LateAdjustment[]>(`/late-adjustments${qs({ customerId })}`), [customerId]);
  return (
    <div className="stack">
      <div>
        <h4 className="section-title">Notifications</h4>
        {notes.loading && !notes.data ? (
          <Spinner />
        ) : notes.data?.length ? (
          <div className="list">
            {notes.data.map((n) => (
              <div key={n.id} className="list-item">
                <span className="list-icon">
                  <Bell size={15} />
                </span>
                <div style={{ flex: 1 }}>
                  <div>{n.message}</div>
                  <div className="muted small">
                    {n.eventType} · {timeAgo(n.createdAt)}
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <Empty title="No notifications">Invoice, payment, dunning and budget events appear here.</Empty>
        )}
      </div>
      <div>
        <h4 className="section-title">Late usage ledger</h4>
        <p className="muted small">Events that arrived after their month closed (within 72 h). They are billed on the next invoice.</p>
        {late.data?.length ? (
          <Table className="compact">
            <thead>
              <tr>
                <th>Event</th>
                <th>Usage period</th>
                <th>Status</th>
                <th>Received</th>
              </tr>
            </thead>
            <tbody>
              {late.data.map((l) => (
                <tr key={l.id}>
                  <td className="mono small">{l.usageEventId.slice(0, 8)}</td>
                  <td>{l.billingPeriod}</td>
                  <td>
                    <StatusBadge status={l.status} />
                  </td>
                  <td className="muted">{dateTime(l.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          <Empty title="No late usage" />
        )}
      </div>
    </div>
  );
}
