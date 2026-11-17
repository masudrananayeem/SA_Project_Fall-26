import { useEffect, useState } from 'react';
import { BellRing, PiggyBank, Save } from 'lucide-react';
import {
  Alert, Button, Card, CardHeader, Empty, ErrorNote, Field, PageHeader, Progress, Spinner,
  currentPeriod, get, money, periodLabel, post, qs, timeAgo, toNumber, useAction, useAsync,
  type BudgetEvaluation, type CustomerNotification, type Estimate,
} from '@meterflow/shared';

const THRESHOLDS = [50, 80, 100];

export function BudgetPage({ customerId }: { customerId: string }) {
  const period = currentPeriod();
  const estimate = useAsync(() => get<Estimate>(`/usage/estimate${qs({ customerId, period })}`), [customerId]);
  const budget = useAsync<BudgetEvaluation>(async () => {
    const b = await get<BudgetEvaluation>(`/budgets${qs({ customerId, period })}`);
    return b.status === 'NO_BUDGET' ? b : get<BudgetEvaluation>(`/budgets/evaluate${qs({ customerId, period })}`);
  }, [customerId]);
  const alerts = useAsync(() => get<CustomerNotification[]>(`/notifications${qs({ customerId })}`).then((n) => n.filter((x) => x.eventType.startsWith('usage.'))), [customerId]);
  const [amount, setAmount] = useState('');
  const { busy, run } = useAction();
  const currency = estimate.data?.currency || budget.data?.currency || 'BDT';
  const hasBudget = budget.data && budget.data.status !== 'NO_BUDGET';
  const notified = (budget.data?.notifiedThresholds || '').split(',').filter(Boolean).map(Number);

  useEffect(() => {
    if (hasBudget && !amount) setAmount(String(toNumber(budget.data!.budget)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasBudget]);

  const save = async () => {
    const r = await run('save', () => post('/budgets', { customerId, period, amount: Number(amount), currency }), `Budget for ${periodLabel(period)} saved.`);
    if (r === undefined) return;
    // Evaluating the budget publishes threshold events; notifications are written asynchronously after that.
    await budget.reload();
    await alerts.reload();
    setTimeout(() => void alerts.reload(), 1500);
  };

  const pct = toNumber(budget.data?.percentUsed);

  return (
    <>
      <PageHeader title="Budget & alerts" subtitle={`Set a spending limit for ${periodLabel(period)}. We notify you when your estimated bill reaches 50%, 80% and 100% of it.`} />
      <ErrorNote error={budget.error || estimate.error} onRetry={budget.reload} />
      <div className="grid-3">
        <Card>
          <CardHeader title={<span className="row"><PiggyBank size={18} /> {periodLabel(period)}</span>} subtitle="Compared with your live estimate" />
          {budget.loading && !budget.data ? (
            <Spinner />
          ) : hasBudget ? (
            <div className="stack">
              <div className="row-between">
                <div>
                  <div className="big-number">{pct}%</div>
                  <div className="muted">
                    {money(budget.data!.estimatedAmount, currency)} of {money(budget.data!.budget, currency)} used
                  </div>
                </div>
                {pct >= 100 ? <Alert tone="bad">Budget exceeded</Alert> : pct >= 80 ? <Alert tone="warn">Close to your limit</Alert> : <Alert tone="good">On track</Alert>}
              </div>
              <Progress value={pct} />
              <div className="threshold-row">
                {THRESHOLDS.map((t) => (
                  <div key={t} className={`threshold${notified.includes(t) ? ' hit' : ''}`}>
                    <b>{t}%</b>
                    <span>{notified.includes(t) ? 'Alert sent' : `at ${money((toNumber(budget.data!.budget) * t) / 100, currency)}`}</span>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <Empty icon={<PiggyBank size={22} />} title="No budget for this month">
              Your estimated bill is {estimate.data ? money(estimate.data.estimatedAmount, currency) : '…'} so far.
            </Empty>
          )}
        </Card>
        <Card>
          <CardHeader title={hasBudget ? 'Change budget' : 'Set budget'} subtitle="Changing it resets the alerts for this month" />
          <div className="stack-sm">
            <Field label={`Monthly budget (${currency})`}>
              <input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="e.g. 500" />
            </Field>
            {estimate.data && (
              <div className="btn-row">
                {[1.25, 1.5, 2].map((f) => {
                  const v = Math.max(Math.ceil((toNumber(estimate.data!.estimatedAmount) * f) / 10) * 10, 100);
                  return (
                    <Button key={f} size="sm" variant="subtle" onClick={() => setAmount(String(v))}>
                      {money(v, currency)}
                    </Button>
                  );
                })}
              </div>
            )}
            <div>
              <Button variant="primary" icon={<Save size={16} />} loading={busy === 'save'} disabled={!(Number(amount) > 0)} onClick={save}>
                Save budget
              </Button>
            </div>
          </div>
        </Card>
      </div>
      <Card style={{ marginTop: 20 }}>
        <CardHeader title={<span className="row"><BellRing size={18} /> Budget alerts</span>} subtitle="Also delivered to your webhook endpoint if one is configured" />
        {alerts.loading && !alerts.data ? (
          <Spinner />
        ) : alerts.data?.length ? (
          <div className="list">
            {alerts.data.map((n) => (
              <div key={n.id} className="list-item">
                <span className="list-icon">
                  <BellRing size={15} />
                </span>
                <div style={{ flex: 1 }}>
                  <div>{n.message}</div>
                  <div className="muted small">{timeAgo(n.createdAt)}</div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <Empty title="No alerts yet" />
        )}
      </Card>
    </>
  );
}
