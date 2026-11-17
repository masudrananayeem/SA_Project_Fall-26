import { useState } from 'react';
import { Check, Sparkles } from 'lucide-react';
import {
  Alert, Badge, Button, Card, Empty, ErrorNote, Field, Modal, PageHeader, Spinner,
  date, get, money, post, toNumber, useAction, useAsync,
  type Plan, type Subscription,
} from '@meterflow/shared';
import { catalogue, chargesOf, headlinePrice, priceLines } from '../plans';

export function PlansPage({ customerId, navigate }: { customerId: string; navigate: (to: string) => void }) {
  const plans = useAsync(() => get<Plan[]>('/catalogue/plans'), []);
  const subs = useAsync(() => get<Subscription[]>('/subscriptions'), [customerId]);
  const [chosen, setChosen] = useState<Plan | null>(null);
  const current = (subs.data || []).slice().sort((a, b) => b.periodStart.localeCompare(a.periodStart)).find((s) => s.status !== 'CANCELLED');
  const currentPlan = plans.data?.find((p) => p.id === current?.planId);
  const list = catalogue(plans.data);

  return (
    <>
      <PageHeader
        eyebrow={current ? 'Change plan' : 'Get started'}
        title={current ? 'Plans' : 'Choose your plan'}
        subtitle={current ? 'Switch any time. Upgrades can start now (prorated) or next month.' : 'Pick the plan that fits your usage. You pay a monthly platform fee plus what you use; VAT is added on the invoice.'}
      />
      <ErrorNote error={plans.error || subs.error} onRetry={plans.reload} />
      {current?.status === 'PAUSED_DUNNING' && <Alert tone="bad">Your subscription is paused because of an unpaid invoice. Pay it on the Invoices page to resume.</Alert>}
      {plans.loading && !plans.data ? (
        <Spinner label="Loading plans…" />
      ) : !list.length ? (
        <Card>
          <Empty title="No plans available yet">Please contact support.</Empty>
        </Card>
      ) : (
        <div className="plan-grid">
          {list.map((p, i) => {
            const isCurrent = currentPlan?.planKey === p.planKey;
            const outdated = isCurrent && currentPlan && currentPlan.id !== p.id;
            return (
              <div key={p.id} className={`plan-card${isCurrent ? ' current' : ''}${i === 1 && !isCurrent ? ' featured' : ''}`}>
                <div className="row-between">
                  <h3>{p.name}</h3>
                  {isCurrent ? <Badge tone="accent">Current</Badge> : i === 1 ? <Badge tone="info"><Sparkles size={12} /> Popular</Badge> : null}
                </div>
                <div className="plan-price">
                  {money(headlinePrice(p), p.currency)}
                  <span>/month</span>
                </div>
                <div className="muted small">
                  + usage · billed on {p.metric.replace(/_/g, ' ')} ({p.aggregation || 'sum'}) · VAT {toNumber(p.taxRate)}%
                </div>
                <ul className="plan-features">
                  {priceLines(p).map((l) => (
                    <li key={l}>
                      <Check size={15} />
                      <span>{l}</span>
                    </li>
                  ))}
                </ul>
                {outdated && <div className="muted small">You are on v{currentPlan!.version}; v{p.version} is the latest.</div>}
                <Button
                  variant={isCurrent && !outdated ? 'secondary' : 'primary'}
                  className="btn-block"
                  disabled={(isCurrent && !outdated) || subs.loading}
                  onClick={() => setChosen(p)}
                >
                  {isCurrent && !outdated ? 'Your plan' : current ? (outdated ? `Move to v${p.version}` : 'Switch to this plan') : 'Choose plan'}
                </Button>
              </div>
            );
          })}
        </div>
      )}
      {current && (
        <p className="muted small" style={{ marginTop: 18 }}>
          Current period {date(current.periodStart)} → {date(current.periodEnd)}. Manage or cancel your subscription on <a href="#/account">Plan &amp; account</a>.
        </p>
      )}
      {chosen && (
        <ConfirmPlan
          plan={chosen}
          current={current}
          onClose={() => setChosen(null)}
          onDone={async () => {
            setChosen(null);
            await subs.reload();
            navigate('home');
          }}
        />
      )}
    </>
  );
}

function ConfirmPlan({ plan, current, onClose, onDone }: { plan: Plan; current?: Subscription; onClose: () => void; onDone: () => void }) {
  const [timing, setTiming] = useState<'IMMEDIATE' | 'PERIOD_END'>('PERIOD_END');
  const [seats, setSeats] = useState('1');
  const { busy, run } = useAction();
  const perSeat = chargesOf(plan).some((c) => c.type === 'per_seat');

  const confirm = async () => {
    const r = current
      ? await run(
          'go',
          () => post<{ timing: string; prorationDelta?: number; currency?: string; effectiveDate?: string }>(`/subscriptions/${current.id}/change-plan`, { planId: plan.id, timing }),
          (x) => (x.timing === 'IMMEDIATE' ? `You are now on ${plan.name}. A proration of ${money(x.prorationDelta, x.currency)} appears on this month’s invoice.` : `${plan.name} starts on ${date(x.effectiveDate)}.`),
        )
      : await run('go', () => post<Subscription>('/subscriptions/self', { planId: plan.id, seatCount: perSeat ? Number(seats) || 1 : undefined }), `Welcome to ${plan.name}! Your subscription is active.`);
    if (r) onDone();
  };

  return (
    <Modal
      open
      title={current ? `Switch to ${plan.name}` : `Subscribe to ${plan.name}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={busy === 'go'} onClick={confirm}>
            {current ? 'Confirm change' : 'Start subscription'}
          </Button>
        </>
      }
    >
      <div className="stack-sm">
        <div className="plan-price">
          {money(headlinePrice(plan), plan.currency)}
          <span>/month + usage</span>
        </div>
        <ul className="plan-features">
          {priceLines(plan).map((l) => (
            <li key={l}>
              <Check size={15} />
              <span>{l}</span>
            </li>
          ))}
        </ul>
        {current ? (
          <Field label="When should the change happen?">
            <select value={timing} onChange={(e) => setTiming(e.target.value as 'IMMEDIATE' | 'PERIOD_END')}>
              <option value="PERIOD_END">From the 1st of next month</option>
              <option value="IMMEDIATE">Now (the difference for the rest of this month is prorated)</option>
            </select>
          </Field>
        ) : (
          <>
            {perSeat && (
              <Field label="Seats">
                <input type="number" min="1" value={seats} onChange={(e) => setSeats(e.target.value)} />
              </Field>
            )}
            <Alert>Your subscription starts today. Usage you send from now on is billed at the end of each month.</Alert>
          </>
        )}
      </div>
    </Modal>
  );
}
