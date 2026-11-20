import { useMemo, useState } from 'react';
import { Bell, CalendarCheck, Clock, CreditCard, FileText, Receipt, Search, UserPlus, Users } from 'lucide-react';
import {
  Alert, Badge, BalanceCard, Button, Card, Drawer, EstimateBreakdown, Empty, ErrorNote, Field, InvoiceTable, KeyValues, METHOD_LABEL, Modal, PageHeader, Progress, Spinner, Stat, StatusBadge, Table, Tabs, UsageBreakdownView,
  currentPeriod, date, dateTime, get, isoDay, money, periodLabel, post, qs, qty, timeAgo, toNumber, useAction, useAsync,
  type Account, type BalanceDue, type BillingOutcome, type PaymentRecord, type UsageBreakdown, type BudgetEvaluation, type CreditAccount, type CustomerNotification, type Estimate, type Invoice, type LateAdjustment, type Plan, type Subscription, type UsageSummary,
} from '@meterflow/shared';
import { PlanSelect, latestPlans, planLabel, usePlans, type Navigate } from '../common';

interface Row {
  id: string;
  account?: Account;
  current?: Subscription;
  subCount: number;
  billed: number;
  paid: number;
  outstanding: number;
  invoiceCount: number;
  currency: string;
}

type Filter = 'all' | 'active' | 'none' | 'overdue';

interface Data {
  accounts: Account[];
  subs: Subscription[];
  invoices: Invoice[];
}

/** Live estimate for one row; the backend caches estimates for 30 s. */
function EstimateCell({ customerId, enabled }: { customerId: string; enabled: boolean }) {
  const est = useAsync(() => (enabled ? get<Estimate>(`/usage/estimate${qs({ customerId })}`) : Promise.resolve(undefined)), [customerId, enabled]);
  if (!enabled) return <span className="muted">—</span>;
  if (est.loading && !est.data) return <span className="muted">…</span>;
  if (!est.data?.lines) return <span className="muted">—</span>;
  return <span className="strong">{money(est.data.estimatedAmount, est.data.currency)}</span>;
}

export function CustomersPage({ selected, navigate }: { selected?: string; navigate: Navigate }) {
  const data = useAsync<Data>(async () => {
    const [accounts, subs, invoices] = await Promise.all([get<Account[]>('/customers'), get<Subscription[]>('/subscriptions'), get<Invoice[]>('/invoices')]);
    return { accounts, subs, invoices };
  }, []);
  const plans = usePlans();
  const [q, setQ] = useState('');
  const [filter, setFilter] = useState<Filter>('all');
  const [creating, setCreating] = useState(false);

  const rows = useMemo<Row[]>(() => {
    const d = data.data;
    if (!d) return [];
    const ids = new Set<string>();
    d.accounts.forEach((a) => ids.add(a.customerId));
    d.subs.forEach((s) => ids.add(s.customerId));
    d.invoices.forEach((i) => ids.add(i.customerId));
    return [...ids].map((id) => {
      const subs = d.subs.filter((s) => s.customerId === id).sort((a, b) => b.periodStart.localeCompare(a.periodStart));
      const inv = d.invoices.filter((i) => i.customerId === id);
      const sum = (xs: Invoice[]) => xs.reduce((a, i) => a + toNumber(i.total), 0);
      return {
        id,
        account: d.accounts.find((a) => a.customerId === id),
        current: subs.find((s) => s.status !== 'CANCELLED') || subs[0],
        subCount: subs.length,
        billed: sum(inv),
        paid: sum(inv.filter((i) => i.status === 'PAID')),
        outstanding: sum(inv.filter((i) => i.status === 'FINALISED')),
        invoiceCount: inv.length,
        currency: inv[0]?.currency || 'BDT',
      };
    });
  }, [data.data]);

  const shown = rows
    .filter((r) => {
      const text = `${r.id} ${r.account?.displayName || ''} ${r.account?.company || ''} ${r.account?.email || ''}`.toLowerCase();
      if (q && !text.includes(q.toLowerCase())) return false;
      if (filter === 'active') return r.current?.status === 'ACTIVE';
      if (filter === 'none') return !r.current || r.current.status === 'CANCELLED';
      if (filter === 'overdue') return r.outstanding > 0;
      return true;
    })
    .sort((a, b) => (b.account?.createdAt || '').localeCompare(a.account?.createdAt || '') || a.id.localeCompare(b.id));

  const totals = {
    customers: rows.length,
    active: rows.filter((r) => r.current?.status === 'ACTIVE').length,
    billed: rows.reduce((a, r) => a + r.billed, 0),
    outstanding: rows.reduce((a, r) => a + r.outstanding, 0),
  };
  const customerId = selected ? decodeURIComponent(selected) : undefined;
  const selectedRow = rows.find((r) => r.id === customerId);

  return (
    <>
      <PageHeader
        title="Customers"
        subtitle="Every customer: self sign-ups, admin-created logins and customers billed by ID. Open one to see live spend, invoices, credits, budget and notifications."
        actions={
          <>
            <Button onClick={() => data.reload()}>Refresh</Button>
            <Button variant="primary" icon={<UserPlus size={16} />} onClick={() => setCreating(true)}>
              New customer
            </Button>
          </>
        }
      />
      <ErrorNote error={data.error} onRetry={data.reload} />
      <div className="stats" style={{ marginBottom: 20 }}>
        <Stat label="Customers" value={totals.customers} hint={`${data.data?.accounts.length ?? 0} with a login`} icon={<Users size={17} />} />
        <Stat label="Active subscriptions" value={totals.active} icon={<CalendarCheck size={17} />} tone="good" />
        <Stat label="Billed to date" value={money(totals.billed)} icon={<Receipt size={17} />} />
        <Stat label="Outstanding" value={money(totals.outstanding)} icon={<Clock size={17} />} tone={totals.outstanding > 0 ? 'warn' : undefined} />
      </div>
      <Card className="flush">
        <div className="toolbar">
          <div className="input-icon search">
            <Search size={16} />
            <input placeholder="Name, company, email or customer ID" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <Tabs
            value={filter}
            onChange={setFilter}
            tabs={[
              { value: 'all', label: 'All' },
              { value: 'active', label: 'Active' },
              { value: 'none', label: 'No plan' },
              { value: 'overdue', label: 'Owes money' },
            ]}
          />
          <span className="spacer" />
          <span className="muted small">{shown.length} shown</span>
        </div>
        <div className="flush-body">
          {data.loading && !data.data ? (
            <Spinner />
          ) : shown.length ? (
            <Table>
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Plan</th>
                  <th>Status</th>
                  <th className="num">This month (est.)</th>
                  <th className="num">Billed</th>
                  <th className="num">Outstanding</th>
                  <th className="hide-sm">Login</th>
                  <th className="hide-sm">Joined</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((r) => (
                  <tr key={r.id} className="clickable" onClick={() => navigate(`customers/${encodeURIComponent(r.id)}`)}>
                    <td>
                      <div className="cell-person">
                        <span className="avatar sm">{(r.account?.displayName || r.id).slice(0, 2).toUpperCase()}</span>
                        <div>
                          <div className="strong">{r.account?.displayName || r.id}</div>
                          <div className="muted small">
                            {r.account?.company ? `${r.account.company} · ` : ''}
                            <span className="mono">{r.id}</span>
                          </div>
                        </div>
                      </div>
                    </td>
                    <td>{r.current ? planLabel(plans.data, r.current.planId) : <span className="muted">No plan</span>}</td>
                    <td>{r.current ? <StatusBadge status={r.current.status} /> : <Badge>None</Badge>}</td>
                    <td className="num">
                      <EstimateCell customerId={r.id} enabled={r.current?.status === 'ACTIVE'} />
                    </td>
                    <td className="num">{money(r.billed, r.currency)}</td>
                    <td className="num">{r.outstanding > 0 ? <span className="text-warn strong">{money(r.outstanding, r.currency)}</span> : '—'}</td>
                    <td className="hide-sm">{r.account ? <Badge tone={r.account.enabled ? 'good' : 'bad'}>{r.account.enabled ? 'Active' : 'Disabled'}</Badge> : r.id === 'customer' ? <Badge tone="info">Demo</Badge> : <span className="muted">—</span>}</td>
                    <td className="hide-sm muted">{date(r.account?.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          ) : (
            <Empty icon={<Users size={22} />} title="No customers match" action={<Button variant="primary" onClick={() => setCreating(true)}>Add a customer</Button>}>
              Customers appear when they sign up in the customer portal, when you create one here, or when they get a subscription.
            </Empty>
          )}
        </div>
      </Card>
      <Drawer open={!!customerId} title={selectedRow?.account?.displayName || customerId} subtitle={`Customer 360 · ${customerId}`} onClose={() => navigate('customers')}>
        {customerId && (
          <CustomerDetail
            customerId={customerId}
            account={selectedRow?.account}
            subs={(data.data?.subs || []).filter((s) => s.customerId === customerId)}
            plans={plans.data}
            navigate={navigate}
            onChanged={data.reload}
          />
        )}
      </Drawer>
      {creating && (
        <NewCustomer
          plans={plans.data}
          onClose={() => setCreating(false)}
          onCreated={async (id) => {
            setCreating(false);
            await data.reload();
            navigate(`customers/${encodeURIComponent(id)}`);
          }}
        />
      )}
    </>
  );
}

function NewCustomer({ plans, onClose, onCreated }: { plans: Plan[] | undefined; onClose: () => void; onCreated: (id: string) => void }) {
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [company, setCompany] = useState('');
  const [password, setPassword] = useState('');
  const [planId, setPlanId] = useState('');
  const { busy, run } = useAction();
  const effectivePlan = planId === 'none' ? '' : planId || latestPlans(plans).find((p) => p.planKey === 'starter')?.id || '';

  const create = async () => {
    const id = username.trim().toLowerCase();
    const ok = await run(
      'create',
      async () => {
        await post<Account>('/customers', { username: id, password, displayName: displayName.trim(), email: email.trim(), company: company.trim() || undefined });
        if (effectivePlan) await post<Subscription>('/subscriptions', { customerId: id, planId: effectivePlan, periodStart: isoDay(0), periodEnd: isoDay(365) });
        return id;
      },
      `Customer ${id} created${effectivePlan ? ' and subscribed' : ''}. They can sign in to the customer portal now.`,
    );
    if (ok) onCreated(ok);
  };

  return (
    <Modal
      open
      title="New customer"
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={busy === 'create'} disabled={!username.trim() || !displayName.trim() || !email.trim() || password.length < 8} onClick={create}>
            Create customer
          </Button>
        </>
      }
    >
      <div className="form-grid">
        <Field label="Customer ID (login)" hint="Lowercase letters, digits, - or _">
          <input value={username} onChange={(e) => setUsername(e.target.value.toLowerCase())} placeholder="acme-ltd" />
        </Field>
        <Field label="Temporary password" hint="At least 8 characters; share it securely">
          <input value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Field label="Contact name">
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        </Field>
        <Field label="Email">
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Company" wide>
          <input value={company} onChange={(e) => setCompany(e.target.value)} />
        </Field>
        <Field label="Subscribe to plan" wide>
          <select value={planId || effectivePlan} onChange={(e) => setPlanId(e.target.value)}>
            <option value="none">Don’t subscribe now</option>
            {(plans || []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} — {p.planKey} v{p.version}
              </option>
            ))}
          </select>
        </Field>
      </div>
    </Modal>
  );
}

function CustomerDetail({ customerId, account, subs, plans, navigate, onChanged }: { customerId: string; account?: Account; subs: Subscription[]; plans: Plan[] | undefined; navigate: Navigate; onChanged: () => Promise<void> }) {
  const live = subs.find((s) => s.status !== 'CANCELLED');
  const { busy, run } = useAction();
  const [planId, setPlanId] = useState('');
  return (
    <div className="stack">
      {account ? (
        <div className="profile-strip">
          <KeyValues
            items={[
              ['Contact', account.displayName],
              ['Email', account.email ? <a href={`mailto:${account.email}`}>{account.email}</a> : '—'],
              ['Company', account.company || '—'],
              ['Joined', date(account.createdAt)],
            ]}
          />
          <Button
            size="sm"
            variant={account.enabled ? 'danger' : 'secondary'}
            loading={busy === 'en'}
            onClick={() => run('en', () => post<Account>(`/customers/${encodeURIComponent(customerId)}/enabled${qs({ value: !account.enabled })}`), account.enabled ? 'Login disabled.' : 'Login enabled.').then(onChanged)}
          >
            {account.enabled ? 'Disable login' : 'Enable login'}
          </Button>
        </div>
      ) : (
        <Alert>{customerId === 'customer' ? 'Built-in demo customer (login from configuration).' : 'No portal login for this customer ID. Create one with “New customer” using the same ID.'}</Alert>
      )}
      {!live && (
        <div className="inline-form">
          <Field label="Subscribe this customer">
            <PlanSelect plans={plans} value={planId || latestPlans(plans)[0]?.id || ''} onChange={setPlanId} />
          </Field>
          <Button
            variant="primary"
            loading={busy === 'sub'}
            onClick={() =>
              run('sub', () => post<Subscription>('/subscriptions', { customerId, planId: planId || latestPlans(plans)[0]?.id, periodStart: isoDay(0), periodEnd: isoDay(365) }), 'Subscription created.').then(onChanged)
            }
          >
            Subscribe
          </Button>
        </div>
      )}
      <CustomerTabs customerId={customerId} subs={subs} navigate={navigate} />
    </div>
  );
}

function CustomerTabs({ customerId, subs, navigate }: { customerId: string; subs: Subscription[]; navigate: Navigate }) {
  const [tab, setTab] = useState<'spend' | 'api' | 'invoices' | 'payments' | 'account' | 'activity'>('spend');
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
          { value: 'api', label: 'API usage' },
          { value: 'payments', label: 'Payments' },
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
      {tab === 'api' && <ApiUsageTab customerId={customerId} metric={estimate.data?.billableMetric} aggregation={estimate.data?.aggregation} />}
      {tab === 'payments' && <PaymentsTab customerId={customerId} />}
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

function ApiUsageTab({ customerId, metric, aggregation }: { customerId: string; metric?: string; aggregation?: string }) {
  const data = useAsync(() => get<UsageBreakdown>(`/usage/breakdown${qs({ customerId })}`), [customerId]);
  if (data.loading && !data.data) return <Spinner label="Reading usage…" />;
  if (data.error) return <ErrorNote error={data.error} onRetry={data.reload} />;
  return <UsageBreakdownView data={data.data!} app="admin" billedMetric={metric} aggregation={aggregation} />;
}

function PaymentsTab({ customerId }: { customerId: string }) {
  const list = useAsync(() => get<PaymentRecord[]>(`/payments${qs({ customerId })}`), [customerId]);
  const balance = useAsync(() => get<BalanceDue>(`/payments/balance${qs({ customerId })}`), [customerId]);
  if (list.loading && !list.data) return <Spinner />;
  const card = balance.data && (
    <BalanceCard
      admin
      balance={balance.data}
      onChanged={() => {
        void Promise.all([balance.reload(), list.reload()]);
      }}
    />
  );
  if (!list.data?.length)
    return (
      <div className="stack">
        {card}
        <Empty icon={<CreditCard size={22} />} title="No payments from this customer yet" />
      </div>
    );
  return (
    <div className="stack">
    {card}
    <Table className="compact">
      <thead>
        <tr>
          <th>When</th>
          <th>For</th>
          <th>Method</th>
          <th>Status</th>
          <th className="num">Amount</th>
        </tr>
      </thead>
      <tbody>
        {list.data.map((p) => (
          <tr key={p.id}>
            <td className="muted">{dateTime(p.createdAt)}</td>
            <td>
              {p.kind === 'UPGRADE' ? (
                <Badge tone="warn">Plan upgrade balance</Badge>
              ) : p.invoiceId ? (
                <a href={`#/invoices/${p.invoiceId}`} className="mono">INV-{p.invoiceId.slice(0, 8).toUpperCase()}</a>
              ) : (
                <Badge tone="accent">Prepaid credit</Badge>
              )}
            </td>
            <td>{METHOD_LABEL[p.method] || p.method}</td>
            <td>
              <StatusBadge status={p.status} />
            </td>
            <td className={`num ${p.status === 'SUCCEEDED' ? 'amount-in' : 'amount-fail'}`}>{money(p.amount, p.currency)}</td>
          </tr>
        ))}
      </tbody>
    </Table>
    </div>
  );
}
