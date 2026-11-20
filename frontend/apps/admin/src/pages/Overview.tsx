import { useMemo } from 'react';
import { AlertOctagon, Ban, CalendarClock, CircleDollarSign, CreditCard, FileText, Rocket, Wallet, XCircle } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import {
  Button, Card, CardHeader, Empty, ErrorNote, InvoiceTable, PageHeader, Spinner, Stat, StatusBadge,
  METHOD_LABEL, billingModeLabel, chartPalette, currentPeriod, dateTime, daysLabel, get, isoDay, money, periodLabel, post, timeAgo, toNumber, uid, useAction, useAsync, useTheme,
  type DunningCase, type IngestResult, type Invoice, type PaymentRecord, type Plan, type Subscription, type SubscriptionAnalyticsRow, type UsageEventInput,
} from '@meterflow/shared';
import { latestPlans, type Navigate } from '../common';

interface Data {
  invoices: Invoice[];
  subs: Subscription[];
  dunning: DunningCase[];
  plans: Plan[];
  payments: PaymentRecord[];
  analytics: SubscriptionAnalyticsRow[];
}

const OPEN_DUNNING = ['RETRYING', 'PAST_DUE', 'SUSPENDED'];

export function OverviewPage({ navigate }: { navigate: Navigate }) {
  const data = useAsync<Data>(async () => {
    const [invoices, subs, dunning, plans, payments, analytics] = await Promise.all([
      get<Invoice[]>('/invoices'),
      get<Subscription[]>('/subscriptions'),
      get<DunningCase[]>('/dunning'),
      get<Plan[]>('/catalogue/plans'),
      get<PaymentRecord[]>('/payments'),
      get<SubscriptionAnalyticsRow[]>('/subscriptions/analytics'),
    ]);
    return { invoices, subs, dunning, plans, payments, analytics };
  }, []);
  const { busy, run } = useAction();
  const { theme } = useTheme();
  const pal = chartPalette(theme, 'admin');

  const period = currentPeriod();
  const m = useMemo(() => {
    const d = data.data;
    if (!d) return null;
    const sum = (xs: Invoice[]) => xs.reduce((a, i) => a + toNumber(i.total), 0);
    const byPeriod = new Map<string, { invoiced: number; collected: number }>();
    d.invoices.forEach((i) => {
      const row = byPeriod.get(i.period) || { invoiced: 0, collected: 0 };
      row.invoiced += toNumber(i.total);
      if (i.status === 'PAID') row.collected += toNumber(i.total);
      byPeriod.set(i.period, row);
    });
    const chart = [...byPeriod.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .slice(-6)
      .map(([p, v]) => ({ period: periodLabel(p).replace(/ \d{4}$/, ''), ...v }));
    return {
      invoiced: sum(d.invoices),
      collected: sum(d.invoices.filter((i) => i.status === 'PAID')),
      outstanding: sum(d.invoices.filter((i) => i.status === 'FINALISED')),
      outstandingCount: d.invoices.filter((i) => i.status === 'FINALISED').length,
      active: d.subs.filter((s) => s.status === 'ACTIVE').length,
      trialing: d.subs.filter((s) => s.trialing).length,
      openDunning: d.dunning.filter((c) => OPEN_DUNNING.includes(c.status)),
      planKeys: latestPlans(d.plans).length,
      unpaidAccounts: d.analytics.filter((r) => ['PENDING_PAYMENT', 'BLOCKED', 'PAUSED_DUNNING'].includes(r.status)).length,
      distribution: (() => {
        const map = new Map<string, { name: string; mode: string | null; count: number; days: number }>();
        d.analytics.forEach((r) => {
          const key = r.planKey || r.planId;
          const row = map.get(key) || { name: r.planName || key, mode: r.billingMode, count: 0, days: 0 };
          row.count++;
          row.days += r.daysOnPlan;
          map.set(key, row);
        });
        return [...map.values()].sort((a, b) => b.count - a.count);
      })(),
      chart,
      thisPeriod: d.invoices.filter((i) => i.period === period),
    };
  }, [data.data, period]);

  const demoCustomerActive = data.data?.subs.some((s) => s.customerId === 'customer' && s.status === 'ACTIVE');

  async function prepareDemo() {
    await run(
      'demo',
      async () => {
        const plans = data.data?.plans || (await get<Plan[]>('/catalogue/plans'));
        const plan = latestPlans(plans).find((p) => p.planKey === 'starter') || latestPlans(plans)[0];
        if (!plan) throw new Error('Create a pricing plan first.');
        if (!demoCustomerActive) {
          await post<Subscription>('/subscriptions', {
            customerId: 'customer',
            planId: plan.id,
            periodStart: `${period}-01`,
            periodEnd: isoDay(365),
          });
        }
        // 1,800 API calls spread over this month so far (2 batches of 900).
        const start = Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth(), 1);
        const span = Math.max(Date.now() - 60_000 - start, 60_000);
        const regions = ['ap-south-1', 'ap-southeast-1', 'eu-west-1'];
        const endpoints = ['/v1/chat', '/v1/embeddings', '/v1/images'];
        let accepted = 0;
        for (let b = 0; b < 2; b++) {
          const events: UsageEventInput[] = Array.from({ length: 900 }, (_, i) => ({
            customerId: 'customer',
            metric: 'api_calls',
            quantity: 1,
            timestamp: new Date(start + Math.floor(((b * 900 + i) / 1800) * span)).toISOString(),
            idempotencyKey: uid('demo'),
            properties: { region: regions[i % 3], endpoint: endpoints[(i * 7) % 3] },
          }));
          const r = await post<IngestResult>('/usage/events:batch', { events });
          accepted += r.accepted;
        }
        return { accepted, plan };
      },
      (r) => `Demo customer is on ${r.plan.name} v${r.plan.version} with ${r.accepted.toLocaleString()} usage events this month.`,
    );
    await data.reload();
  }

  return (
    <>
      <PageHeader
        eyebrow={periodLabel(period)}
        title="Billing overview"
        subtitle="Revenue, collections and risk across every customer. Data refreshes when you open a page."
        actions={
          <>
            <Button onClick={() => data.reload()} loading={data.loading && !!data.data}>
              Refresh
            </Button>
            <Button variant="primary" icon={<FileText size={16} />} onClick={() => navigate('billing')}>
              Run billing
            </Button>
          </>
        }
      />
      <ErrorNote error={data.error} onRetry={data.reload} />
      {!m ? (
        data.loading && <Spinner label="Loading workspace…" />
      ) : (
        <div className="stack">
          <div className="stats">
            <Stat label="Invoiced (all time)" value={money(m.invoiced)} hint={`${data.data!.invoices.length} invoices`} icon={<CircleDollarSign size={17} />} />
            <Stat label="Collected" value={money(m.collected)} hint="Paid invoices" icon={<Wallet size={17} />} tone="good" />
            <Stat label="Outstanding" value={money(m.outstanding)} hint={`${m.outstandingCount} awaiting payment`} icon={<FileText size={17} />} tone={m.outstanding > 0 ? 'warn' : undefined} />
            <Stat label="Active subscriptions" value={m.active} hint={`${m.trialing} in trial · ${m.planKeys} plans`} icon={<CalendarClock size={17} />} />
            <Stat label="Open dunning cases" value={m.openDunning.length} hint="Retrying, past due or suspended" icon={<AlertOctagon size={17} />} tone={m.openDunning.length ? 'bad' : undefined} />
            <Stat label="Unpaid / blocked accounts" value={m.unpaidAccounts} hint="Awaiting prepaid payment or blocked" icon={<Ban size={17} />} tone={m.unpaidAccounts ? 'warn' : undefined} />
          </div>

          {!demoCustomerActive && (
            <Card className="callout">
              <div className="callout-body">
                <span className="callout-icon">
                  <Rocket size={20} />
                </span>
                <div>
                  <h3>Set up the demo customer</h3>
                  <p className="muted">
                    Subscribes the customer account <code>customer</code> to the Starter plan and sends 1,800 usage events for this month. Then sign in to the customer portal as{' '}
                    <code>customer</code> to see live spend, or run billing here.
                  </p>
                </div>
                <Button variant="primary" loading={busy === 'demo'} onClick={prepareDemo}>
                  Prepare demo
                </Button>
              </div>
            </Card>
          )}

          <div className="grid-3">
            <Card>
              <CardHeader title="Revenue by period" subtitle="Invoiced vs collected, last six billing periods" />
              {m.chart.length ? (
                <div className="chart-box">
                  <ResponsiveContainer>
                    <BarChart data={m.chart} margin={{ left: 0, right: 8, top: 8 }}>
                      <CartesianGrid vertical={false} stroke={pal.grid} />
                      <XAxis dataKey="period" tickLine={false} axisLine={false} fontSize={12} tick={{ fill: pal.axis }} />
                      <YAxis tickLine={false} axisLine={false} fontSize={12} width={70} tick={{ fill: pal.axis }} tickFormatter={(v) => `৳${Number(v).toLocaleString()}`} />
                      <Tooltip formatter={(v) => money(Number(v))} cursor={{ fill: pal.cursor }} contentStyle={pal.tooltip} />
                      <Bar dataKey="invoiced" name="Invoiced" fill={pal.accentSoft} radius={[6, 6, 0, 0]} maxBarSize={48} />
                      <Bar dataKey="collected" name="Collected" fill={pal.accent} radius={[6, 6, 0, 0]} maxBarSize={48} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <Empty icon={<CircleDollarSign size={22} />} title="No revenue yet">
                  Run billing to generate the first invoices.
                </Empty>
              )}
            </Card>
            <Card>
              <CardHeader title="Dunning watchlist" subtitle="Payments being retried" actions={<Button size="sm" variant="ghost" onClick={() => navigate('dunning')}>Open</Button>} />
              {m.openDunning.length ? (
                <div className="list">
                  {m.openDunning.slice(0, 6).map((c) => (
                    <div key={c.id} className="list-item">
                      <div style={{ flex: 1 }}>
                        <b>{c.customerId}</b>
                        <div className="muted small">
                          Attempt {c.attempt} · next {dateTime(c.nextAttemptAt)}
                        </div>
                      </div>
                      <StatusBadge status={c.status} />
                    </div>
                  ))}
                </div>
              ) : (
                <Empty title="All payments healthy">No invoice is in dunning.</Empty>
              )}
            </Card>
          </div>

          <Card className="flush">
            <CardHeader title="Recent invoices" subtitle="Click an invoice to inspect lines and trace usage" actions={<Button size="sm" variant="ghost" onClick={() => navigate('invoices')}>View all</Button>} />
            <div className="flush-body">
              <InvoiceTable invoices={data.data!.invoices.slice(0, 6)} onOpen={(i) => navigate(`invoices/${i.id}`)} />
            </div>
          </Card>

          <div className="grid-2">
            <Card>
              <CardHeader title="Customers per plan" subtitle="Live subscriptions and how long customers stay on each plan" actions={<Button size="sm" variant="ghost" onClick={() => navigate('subscriptions')}>Subscriptions</Button>} />
              {m.distribution.length ? (
                <div>
                  {m.distribution.map((p) => {
                    const total = m.distribution.reduce((a, x) => a + x.count, 0);
                    return (
                      <div key={p.name} className="dist-row">
                        <div>
                          <div className="strong">{p.name}</div>
                          <div className="muted small">{billingModeLabel(p.mode)} · avg {daysLabel(Math.round(p.days / p.count))} on plan</div>
                        </div>
                        <span className="prop-bar">
                          <span style={{ width: `${(p.count * 100) / total}%` }} />
                        </span>
                        <b>
                          {p.count} customer{p.count === 1 ? '' : 's'}
                        </b>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <Empty title="No subscriptions yet">Create one on the Subscriptions page or use “Prepare demo”.</Empty>
              )}
            </Card>
            <Card>
              <CardHeader title="Latest payments" subtitle="Updates when customers pay" actions={<Button size="sm" variant="ghost" onClick={() => navigate('payments')}>All payments</Button>} />
              {data.data!.payments.length ? (
                <div className="list">
                  {data.data!.payments.slice(0, 6).map((p) => (
                    <div key={p.id} className="list-item">
                      <span className="list-icon">{p.status === 'SUCCEEDED' ? <CreditCard size={15} /> : <XCircle size={15} />}</span>
                      <div style={{ flex: 1 }}>
                        <b>{p.customerId}</b> <span className="muted">· {METHOD_LABEL[p.method] || p.method}{p.kind === 'TOP_UP' ? ' · credit' : p.kind === 'UPGRADE' ? ' · upgrade' : ''}</span>
                        <div className="muted small">{timeAgo(p.createdAt)}</div>
                      </div>
                      <span className={p.status === 'SUCCEEDED' ? 'amount-in' : 'amount-fail'}>{money(p.amount, p.currency)}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <Empty icon={<CreditCard size={22} />} title="No payments yet">Customer payments appear here as soon as they pay.</Empty>
              )}
            </Card>
          </div>
        </div>
      )}
    </>
  );
}
