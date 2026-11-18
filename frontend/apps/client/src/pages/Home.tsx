import { useEffect, useState } from 'react';
import { AccountStatusBanner, useAccountState } from '../status';
import { ArrowRight, Layers, PiggyBank, RefreshCw } from 'lucide-react';
import {
  Button, Card, CardHeader, EstimateBreakdown, ErrorNote, InvoiceTable, PageHeader, Progress, Spinner,
  currentPeriod, get, money, periodLabel, qs, qty, toNumber, useAsync,
  type BudgetEvaluation, type Estimate, type Invoice,
} from '@meterflow/shared';

export function useLiveEstimate(customerId: string) {
  const period = currentPeriod();
  const estimate = useAsync(() => get<Estimate>(`/usage/estimate${qs({ customerId, period })}`), [customerId]);
  const every = estimate.data?.refreshSeconds || 30;
  const [left, setLeft] = useState(every);
  useEffect(() => {
    setLeft(every);
    const t = setInterval(() => {
      setLeft((s) => {
        if (s <= 1) {
          void estimate.reload();
          return every;
        }
        return s - 1;
      });
    }, 1000);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [every, customerId]);
  return { estimate, left };
}

export function HomePage({ customerId, navigate }: { customerId: string; navigate: (to: string) => void }) {
  const period = currentPeriod();
  const { estimate, left } = useLiveEstimate(customerId);
  const invoices = useAsync(() => get<Invoice[]>(`/invoices${qs({ customerId })}`), [customerId]);
  const budget = useAsync<BudgetEvaluation>(async () => {
    const b = await get<BudgetEvaluation>(`/budgets${qs({ customerId, period })}`);
    return b.status === 'NO_BUDGET' ? b : get<BudgetEvaluation>(`/budgets/evaluate${qs({ customerId, period })}`);
  }, [customerId]);
  const account = useAccountState(customerId);

  const e = estimate.data;
  const hasPlan = !!e?.lines;
  const prepaid = account.plan?.billingMode === 'PREPAID';

  return (
    <>
      <PageHeader eyebrow={periodLabel(period)} title={`Welcome back, ${customerId}`} subtitle="Your usage and spend for the current billing period." />
      <div className="stack">
        <AccountStatusBanner
          state={{
            ...account,
            reload: async () => {
              await Promise.all([account.reload(), invoices.reload(), estimate.reload()]);
            },
          }}
        />

        <ErrorNote error={estimate.error} onRetry={estimate.reload} />
        {!account.loading && !account.subscription && (
          <Card className="callout">
            <div className="callout-body">
              <span className="callout-icon">
                <Layers size={20} />
              </span>
              <div>
                <h3>Choose a plan to get started</h3>
                <p className="muted">You don’t have an active subscription yet. Pick a plan, then send usage events with your customer ID <code>{customerId}</code> and watch your bill here live.</p>
              </div>
              <Button variant="primary" onClick={() => navigate('plans')}>
                See plans
              </Button>
            </div>
          </Card>
        )}
        <div className="hero">
          <Card className="hero-main">
            <div className="row-between">
              <span className="hero-label">
                <span className="dot live" /> {prepaid ? 'Prepaid package · usage this month' : 'Estimated bill so far'}
              </span>
              <span className="refresh-ring">
                <RefreshCw size={12} className={estimate.loading ? 'spin' : undefined} /> updates in {left}s
              </span>
            </div>
            {!e ? (
              <div className="hero-amount">…</div>
            ) : (
              <>
                <div className="hero-amount">{money(e.estimatedAmount, e.currency)}</div>
                <div className="hero-sub">
                  {hasPlan
                    ? `${e.plan} · ${prepaid ? 'paid in advance' : 'pay after use'} · includes ${toNumber(e.taxRate)}% VAT`
                    : account.subscription
                      ? 'Usage is paused until your account is paid up'
                      : e.note || 'No active subscription for this period'}
                </div>
                {hasPlan && (
                  <div className="hero-meta">
                    <div>
                      <span>Billable {e.billableMetric}</span>
                      <b>{qty(e.billableQuantity)}</b>
                    </div>
                    <div>
                      <span>Usage events</span>
                      <b>{(e.eventCount ?? 0).toLocaleString()}</b>
                    </div>
                    <div>
                      <span>Subtotal</span>
                      <b>{money(e.estimatedSubtotal, e.currency)}</b>
                    </div>
                  </div>
                )}
              </>
            )}
          </Card>
          <Card>
            <CardHeader title={<span className="row"><PiggyBank size={18} /> Monthly budget</span>} actions={<Button size="sm" variant="ghost" onClick={() => navigate('budget')}>Manage</Button>} />
            {budget.loading && !budget.data ? (
              <Spinner />
            ) : budget.data?.status === 'NO_BUDGET' || !budget.data ? (
              <div className="stack-sm">
                <p className="muted" style={{ margin: 0 }}>
                  Set a budget to get alerts at 50%, 80% and 100% of your spend.
                </p>
                <div>
                  <Button variant="subtle" icon={<ArrowRight size={16} />} onClick={() => navigate('budget')}>
                    Set a budget
                  </Button>
                </div>
              </div>
            ) : (
              <div className="stack-sm">
                <div className="big-number" style={{ fontSize: 34 }}>
                  {toNumber(budget.data.percentUsed)}%
                </div>
                <Progress value={toNumber(budget.data.percentUsed)} />
                <div className="muted small">
                  {money(budget.data.estimatedAmount, budget.data.currency)} of {money(budget.data.budget, budget.data.currency)}
                </div>
              </div>
            )}
          </Card>
        </div>

        <div className="grid-2">
          <Card>
            <CardHeader title="How your estimate is calculated" subtitle="Same rules as your invoice. The invoice at month end will match this." />
            {e ? <EstimateBreakdown estimate={e} /> : <Spinner />}
          </Card>
          <Card className="flush">
            <CardHeader title="Recent invoices" actions={<Button size="sm" variant="ghost" onClick={() => navigate('invoices')}>All invoices</Button>} />
            <div className="flush-body">
              {invoices.loading && !invoices.data ? (
                <Spinner />
              ) : (
                <InvoiceTable invoices={(invoices.data || []).slice(0, 5)} showCustomer={false} onOpen={(i) => navigate(`invoices/${i.id}`)} empty="Your first invoice is created at the end of the billing period." />
              )}
            </div>
          </Card>
        </div>
      </div>
    </>
  );
}
