import { useEffect, useMemo, useRef, useState } from 'react';
import { AlertOctagon, ArrowUpCircle, Ban, CalendarClock, CheckCircle2, CircleDollarSign, RefreshCw, Search, ShieldCheck, Wallet, XCircle } from 'lucide-react';
import {
  Alert, Badge, Button, Card, CardHeader, Empty, ErrorNote, PageHeader, Spinner, Stat, StatusBadge, Table, Tabs,
  METHOD_LABEL, PayUpgradeModal, dateTime, get, invoiceNo, money, payableFromCharge, periodLabel, post, timeAgo, useAction, useAsync,
  type PaymentRecord, type PaymentSummary, type SubscriptionAnalyticsRow, type UpgradeCharge,
} from '@meterflow/shared';
import type { Navigate } from '../common';

type Filter = 'all' | 'SUCCEEDED' | 'FAILED' | 'TOP_UP';

export function PaymentsPage({ navigate }: { navigate: Navigate }) {
  const feed = useAsync(() => get<PaymentRecord[]>('/payments'), []);
  const summary = useAsync(() => get<PaymentSummary>('/payments/summary'), []);
  const standing = useAsync(() => get<SubscriptionAnalyticsRow[]>('/subscriptions/analytics'), []);
  const upgrades = useAsync(() => get<UpgradeCharge[]>('/billing/upgrade-charges').catch(() => [] as UpgradeCharge[]), []);
  const [marking, setMarking] = useState<UpgradeCharge | null>(null);
  const [filter, setFilter] = useState<Filter>('all');
  const [q, setQ] = useState('');
  const [fresh, setFresh] = useState<Set<string>>(new Set());
  const known = useRef<Set<string> | null>(null);
  const { busy, run } = useAction();

  // Live feed: refresh every 8 seconds and highlight rows that just arrived.
  useEffect(() => {
    const t = setInterval(() => {
      void feed.reload();
      void summary.reload();
      void upgrades.reload();
    }, 8000);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useEffect(() => {
    if (!feed.data) return;
    const ids = new Set(feed.data.map((p) => p.id));
    if (known.current) setFresh(new Set([...ids].filter((id) => !known.current!.has(id))));
    known.current = ids;
  }, [feed.data]);

  const rows = useMemo(
    () =>
      (feed.data || []).filter((p) => {
        if (q && !p.customerId.toLowerCase().includes(q.toLowerCase())) return false;
        if (filter === 'TOP_UP') return p.kind === 'TOP_UP';
        if (filter !== 'all') return p.status === filter;
        return true;
      }),
    [feed.data, filter, q],
  );
  const attention = (standing.data || []).filter((r) => r.status === 'BLOCKED' || r.status === 'PENDING_PAYMENT' || r.status === 'PAUSED_DUNNING');
  const s = summary.data;

  return (
    <>
      <PageHeader
        title="Payments"
        subtitle="Every payment as it happens: customer checkouts (card, mobile wallet, bank), card-on-file charges, retries and prepaid top-ups. Refreshes every 8 seconds."
        actions={
          <Button icon={<RefreshCw size={16} />} loading={feed.loading && !!feed.data} onClick={() => (feed.reload(), summary.reload(), standing.reload())}>
            Refresh
          </Button>
        }
      />
      <ErrorNote error={feed.error || summary.error} onRetry={feed.reload} />
      <div className="stats" style={{ marginBottom: 20 }}>
        <Stat label="Received today" value={s ? money(s.today.amount) : '…'} hint={s ? `${s.today.count} payment(s)` : undefined} icon={<CircleDollarSign size={17} />} tone="good" />
        <Stat label="Last 7 days" value={s ? money(s.last7Days.amount) : '…'} hint={s ? `${s.last7Days.count} payment(s)` : undefined} icon={<Wallet size={17} />} />
        <Stat label="Last 30 days" value={s ? money(s.last30Days.amount) : '…'} hint={s ? `${s.last30Days.count} payment(s)` : undefined} icon={<CalendarClock size={17} />} />
        <Stat label="Failed today" value={s ? s.failedToday : '…'} icon={<XCircle size={17} />} tone={s && s.failedToday > 0 ? 'bad' : undefined} />
      </div>
      <div className="grid-3">
        <Card className="flush">
          <div className="toolbar">
            <div className="input-icon search">
              <Search size={16} />
              <input placeholder="Customer ID" value={q} onChange={(e) => setQ(e.target.value)} />
            </div>
            <Tabs
              value={filter}
              onChange={setFilter}
              tabs={[
                { value: 'all', label: 'All' },
                { value: 'SUCCEEDED', label: 'Received' },
                { value: 'FAILED', label: 'Failed' },
                { value: 'TOP_UP', label: 'Top-ups' },
              ]}
            />
            <span className="spacer" />
            <span className="env-pill">
              <span className="dot live" /> Live
            </span>
          </div>
          <div className="flush-body">
            {feed.loading && !feed.data ? (
              <Spinner />
            ) : rows.length ? (
              <Table className="cards-sm">
                <thead>
                  <tr>
                    <th>When</th>
                    <th>Customer</th>
                    <th>For</th>
                    <th>Method</th>
                    <th>Status</th>
                    <th className="num">Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((p) => (
                    <tr key={p.id} className={fresh.has(p.id) ? 'feed-row-new' : undefined}>
                      <td>
                        <div>{timeAgo(p.createdAt)}</div>
                        <div className="muted small">{dateTime(p.createdAt)}</div>
                      </td>
                      <td>
                        <a href={`#/customers/${encodeURIComponent(p.customerId)}`} onClick={(e) => (e.preventDefault(), navigate(`customers/${encodeURIComponent(p.customerId)}`))} className="strong">
                          {p.customerId}
                        </a>
                      </td>
                      <td>
                        {p.kind === 'UPGRADE' ? (
                          <Badge tone="warn">Plan upgrade balance</Badge>
                        ) : p.invoiceId ? (
                          <a href={`#/invoices/${p.invoiceId}`} onClick={(e) => (e.preventDefault(), navigate(`invoices/${p.invoiceId}`))} className="mono">
                            {invoiceNo(p.invoiceId)}
                          </a>
                        ) : (
                          <Badge tone="accent">Prepaid credit</Badge>
                        )}
                        {p.message && p.kind !== 'UPGRADE' && <div className="muted small">{p.message}</div>}
                      </td>
                      <td>
                        <span className="method-chip">{METHOD_LABEL[p.method] || p.method}</span>
                      </td>
                      <td>
                        <StatusBadge status={p.status} />
                      </td>
                      <td className={`num ${p.status === 'SUCCEEDED' ? 'amount-in' : 'amount-fail'}`}>{money(p.amount, p.currency)}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            ) : (
              <Empty icon={<Wallet size={22} />} title="No payments yet">
                When a customer pays an invoice in the portal it appears here immediately.
              </Empty>
            )}
          </div>
        </Card>
        <div className="stack">
          <Card>
            <CardHeader title={<span className="row"><ShieldCheck size={18} /> Account standing</span>} subtitle={`Postpaid accounts are blocked after ${s?.blockAfterUnpaidInvoices ?? 2} unpaid invoices; prepaid accounts wait for payment`} />
            {attention.length ? (
              <div className="list">
                {attention.map((r) => (
                  <div key={r.subscriptionId} className="list-item">
                    <span className="list-icon">{r.status === 'BLOCKED' ? <Ban size={15} /> : <AlertOctagon size={15} />}</span>
                    <div style={{ flex: 1 }}>
                      <a href={`#/customers/${encodeURIComponent(r.customerId)}`} className="strong">
                        {r.customerId}
                      </a>
                      <div className="muted small">{r.planName}</div>
                    </div>
                    <StatusBadge status={r.status} />
                  </div>
                ))}
              </div>
            ) : (
              <Alert tone="good">Every account is in good standing.</Alert>
            )}
            <div className="btn-row" style={{ marginTop: 12 }}>
              <Button
                size="sm"
                icon={<CheckCircle2 size={14} />}
                loading={busy === 'enf'}
                onClick={() =>
                  run('enf', () => post<{ blocked: string[]; unblocked: string[] }>('/payments/standing/enforce'), (r) => `Checked: ${r.blocked.length} blocked, ${r.unblocked.length} unblocked.`).then(() => standing.reload())
                }
              >
                Run check now
              </Button>
              <Button
                size="sm"
                icon={<RefreshCw size={14} />}
                loading={busy === 'ren'}
                onClick={() => run('ren', () => post<{ renewed: number }>('/invoices/prepaid/renew'), (r) => `Prepaid renewal: ${r.renewed} invoice(s) issued.`).then(() => standing.reload())}
              >
                Run prepaid renewal
              </Button>
            </div>
          </Card>
          <Card>
            <CardHeader
              title={<span className="row"><ArrowUpCircle size={18} /> Plan upgrade balances</span>}
              subtitle={`Mid-month upgrades of paid packages: the customer pays only the difference for the remaining days (due within ${s?.upgradeGraceDays ?? 3} days)`}
            />
            {(upgrades.data || []).filter((u) => u.status === 'DUE').length ? (
              <div className="list">
                {(upgrades.data || [])
                  .filter((u) => u.status === 'DUE')
                  .map((u) => (
                    <div key={u.id} className="list-item">
                      <span className="list-icon">
                        <ArrowUpCircle size={15} />
                      </span>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <a href={`#/customers/${encodeURIComponent(u.customerId)}`} className="strong">
                          {u.customerId}
                        </a>
                        <div className="muted small">
                          {u.fromPlan} → {u.toPlan} · {periodLabel(u.period)} · paid {money(u.alreadyPaid, u.currency)}
                        </div>
                      </div>
                      <Button size="sm" onClick={() => setMarking(u)}>
                        {money(u.total, u.currency)} due
                      </Button>
                    </div>
                  ))}
              </div>
            ) : (
              <Alert tone="good">No upgrade balance is waiting for payment.</Alert>
            )}
            {!!(upgrades.data || []).filter((u) => u.status === 'PAID').length && (
              <div className="muted small" style={{ marginTop: 10 }}>
                {(upgrades.data || []).filter((u) => u.status === 'PAID').length} upgrade balance(s) already paid.
              </div>
            )}
          </Card>
          {marking && (
            <PayUpgradeModal
              charge={payableFromCharge(marking)}
              admin
              laterLabel="Close"
              intro={<Alert>Record this balance as paid only when the customer has paid outside the portal (for example by bank transfer).</Alert>}
              onClose={() => setMarking(null)}
              onPaid={() => {
                setMarking(null);
                void Promise.all([upgrades.reload(), feed.reload(), summary.reload(), standing.reload()]);
              }}
            />
          )}
          <Card>
            <CardHeader title="How payments work" />
            <ul className="plain-list">
              <li>
                <b>Postpaid</b> plans: invoice at month end, <i>payment pending</i> until the customer pays. Two unpaid invoices block usage automatically.
              </li>
              <li>
                <b>Prepaid</b> plans: the package is invoiced when the customer subscribes and on the 1st of each month; usage starts after payment.
              </li>
              <li>
                <b>Upgrades</b> in the middle of a paid month: the customer pays only the difference for the days left. If it is not paid within {s?.upgradeGraceDays ?? 3} days the
                service waits for payment.
              </li>
              <li>Nothing is charged automatically; admins can charge the card on file or mark an invoice paid.</li>
            </ul>
          </Card>
        </div>
      </div>
    </>
  );
}
