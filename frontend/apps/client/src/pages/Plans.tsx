import { useState } from 'react';
import { CalendarClock, Check, CreditCard, Sparkles, Wallet } from 'lucide-react';
import {
  Alert, Badge, Button, Card, Empty, ErrorNote, Field, Modal, PageHeader, PayInvoiceModal, Spinner, TopUpModal,
  billingModeLabel, currentPeriod, date, get, money, post, qs, toNumber, useAction, useAsync, useToast,
  type Invoice, type Plan, type Subscription,
} from '@meterflow/shared';
import { catalogue, chargesOf, headlinePrice, priceLines } from '../plans';

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** The prepaid package invoice is issued asynchronously right after subscribing; wait for it briefly. */
async function waitForPackageInvoice(customerId: string): Promise<Invoice | null> {
  for (let i = 0; i < 12; i++) {
    const list = await get<Invoice[]>(`/invoices${qs({ customerId })}`);
    const inv = list.find((x) => x.period === currentPeriod() && x.status === 'FINALISED' && toNumber(x.total) > 0);
    if (inv) return inv;
    if (list.some((x) => x.period === currentPeriod() && x.status === 'PAID')) return null;
    await sleep(700);
  }
  return null;
}

export function PlansPage({ customerId, navigate }: { customerId: string; navigate: (to: string) => void }) {
  const plans = useAsync(() => get<Plan[]>('/catalogue/plans'), []);
  const subs = useAsync(() => get<Subscription[]>('/subscriptions'), [customerId]);
  const [chosen, setChosen] = useState<Plan | null>(null);
  const [payInvoice, setPayInvoice] = useState<Invoice | null>(null);
  const [advance, setAdvance] = useState<Plan | null>(null);
  const [mode, setMode] = useState<'all' | 'POSTPAID' | 'PREPAID'>('all');
  const current = (subs.data || [])
    .slice()
    .sort((a, b) => b.periodStart.localeCompare(a.periodStart))
    .find((s) => s.status !== 'CANCELLED' && s.status !== 'EXPIRED');
  const currentPlan = plans.data?.find((p) => p.id === current?.planId);
  const list = catalogue(plans.data).filter((p) => mode === 'all' || (p.billingMode || 'POSTPAID') === mode);

  const done = async () => {
    await subs.reload();
    navigate('home');
  };

  return (
    <>
      <PageHeader
        eyebrow={current ? 'Change plan' : 'Get started'}
        title={current ? 'Plans' : 'Choose your plan'}
        subtitle={
          current
            ? 'Switch any time. Changes can start now (prorated) or next month.'
            : 'Pay-after-use plans send an invoice at the end of each month. Prepaid packages are paid first and then used. VAT is added on every invoice.'
        }
        actions={
          <div className="tabs" role="tablist">
            {(['all', 'POSTPAID', 'PREPAID'] as const).map((m) => (
              <button key={m} className={mode === m ? 'tab active' : 'tab'} onClick={() => setMode(m)}>
                {m === 'all' ? 'All plans' : billingModeLabel(m)}
              </button>
            ))}
          </div>
        }
      />
      <ErrorNote error={plans.error || subs.error} onRetry={plans.reload} />
      {(current?.status === 'PAUSED_DUNNING' || current?.status === 'BLOCKED') && (
        <Alert tone="bad">Your account is blocked for unpaid invoices. Pay them on the Invoices page; plan changes are possible again afterwards.</Alert>
      )}
      {plans.loading && !plans.data ? (
        <Spinner label="Loading plans…" />
      ) : !list.length ? (
        <Card>
          <Empty title="No plans in this category">Try “All plans”.</Empty>
        </Card>
      ) : (
        <div className="plan-grid">
          {list.map((p, i) => {
            const isCurrent = currentPlan?.planKey === p.planKey;
            const outdated = isCurrent && currentPlan && currentPlan.id !== p.id;
            const prepaid = p.billingMode === 'PREPAID';
            return (
              <div key={p.id} className={`plan-card${isCurrent ? ' current' : ''}${i === 1 && !isCurrent ? ' featured' : ''}`}>
                <div className="row-between">
                  <h3>{p.name}</h3>
                  {isCurrent ? (
                    <Badge tone="accent">Current</Badge>
                  ) : i === 1 ? (
                    <Badge tone="info">
                      <Sparkles size={12} /> Popular
                    </Badge>
                  ) : null}
                </div>
                <div>
                  <Badge tone={prepaid ? 'warn' : 'info'}>
                    {prepaid ? <Wallet size={12} /> : <CalendarClock size={12} />} {billingModeLabel(p.billingMode)}
                  </Badge>
                </div>
                <div className="plan-price">
                  {money(headlinePrice(p), p.currency)}
                  <span>/month{prepaid ? ' up front' : ' + usage'}</span>
                </div>
                <div className="muted small">
                  {prepaid ? 'Pay first, then use. ' : 'Use first, invoice at month end. '}
                  Billed on {p.metric.replace(/_/g, ' ')} ({p.aggregation || 'sum'}) · VAT {toNumber(p.taxRate)}%
                </div>
                <ul className="plan-features">
                  {priceLines(p).map((l) => (
                    <li key={l}>
                      <Check size={15} />
                      <span>{l}</span>
                    </li>
                  ))}
                  {prepaid ? (
                    <li>
                      <Check size={15} />
                      <span>Service starts as soon as the package is paid</span>
                    </li>
                  ) : (
                    <li>
                      <Check size={15} />
                      <span>Account is blocked after 2 unpaid monthly invoices</span>
                    </li>
                  )}
                </ul>
                {outdated && (
                  <div className="muted small">
                    You are on v{currentPlan!.version}; v{p.version} is the latest.
                  </div>
                )}
                <Button variant={isCurrent && !outdated ? 'secondary' : 'primary'} className="btn-block" disabled={(isCurrent && !outdated) || subs.loading} onClick={() => setChosen(p)}>
                  {isCurrent && !outdated ? 'Your plan' : current ? (outdated ? `Move to v${p.version}` : 'Switch to this plan') : prepaid ? 'Buy package' : 'Start plan'}
                </Button>
              </div>
            );
          })}
        </div>
      )}
      {current && (
        <p className="muted small" style={{ marginTop: 18 }}>
          Current period {date(current.periodStart)} → {date(current.periodEnd)}. Manage or cancel your subscription on <a href="#/account">Account</a>.
        </p>
      )}
      {chosen && (
        <ConfirmPlan
          plan={chosen}
          current={current}
          customerId={customerId}
          onClose={() => setChosen(null)}
          onSubscribed={async (r) => {
            setChosen(null);
            await subs.reload();
            if (r.invoice) setPayInvoice(r.invoice);
            else if (r.advance) setAdvance(chosen);
            else navigate('home');
          }}
          onChanged={async () => {
            setChosen(null);
            await done();
          }}
        />
      )}
      {payInvoice && (
        <PayInvoiceModal
          invoice={payInvoice}
          laterLabel="Pay later"
          intro={<Alert tone="warn">This is a prepaid package: pay now to start using the service. If you pay later, usage stays paused until you do.</Alert>}
          onClose={() => {
            setPayInvoice(null);
            navigate('home');
          }}
          onPaid={() => {
            setPayInvoice(null);
            void done();
          }}
        />
      )}
      {advance && (
        <TopUpModal
          title="Pay an advance now"
          currency={advance.currency}
          suggested={Math.ceil(headlinePrice(advance) * (1 + toNumber(advance.taxRate) / 100))}
          intro={<Alert>You chose to pay in advance. This credit is used automatically for your monthly invoices.</Alert>}
          onClose={() => {
            setAdvance(null);
            navigate('home');
          }}
          onDone={() => {
            setAdvance(null);
            void done();
          }}
        />
      )}
    </>
  );
}

function ConfirmPlan({
  plan, current, customerId, onClose, onSubscribed, onChanged,
}: {
  plan: Plan;
  current?: Subscription;
  customerId: string;
  onClose: () => void;
  onSubscribed: (r: { invoice?: Invoice | null; advance?: boolean }) => void;
  onChanged: () => void;
}) {
  const [timing, setTiming] = useState<'IMMEDIATE' | 'PERIOD_END'>('PERIOD_END');
  const [seats, setSeats] = useState('1');
  const [payNow, setPayNow] = useState(true);
  const [stage, setStage] = useState<string | null>(null);
  const { busy, run } = useAction();
  const toast = useToast();
  const perSeat = chargesOf(plan).some((c) => c.type === 'per_seat');
  const prepaid = plan.billingMode === 'PREPAID';
  const packageTotal = headlinePrice(plan) * (1 + toNumber(plan.taxRate) / 100);

  const confirm = async () => {
    if (current) {
      const r = await run(
        'go',
        () => post<{ timing: string; prorationDelta?: number; currency?: string; effectiveDate?: string }>(`/subscriptions/${current.id}/change-plan`, { planId: plan.id, timing }),
        (x) => (x.timing === 'IMMEDIATE' ? `You are now on ${plan.name}. A proration of ${money(x.prorationDelta, x.currency)} appears on this month’s invoice.` : `${plan.name} starts on ${date(x.effectiveDate)}.`),
      );
      if (r) onChanged();
      return;
    }
    const sub = await run('go', () => post<Subscription>('/subscriptions/self', { planId: plan.id, seatCount: perSeat ? Number(seats) || 1 : undefined }));
    if (!sub) return;
    if (prepaid) {
      setStage('Preparing your package invoice…');
      const invoice = await waitForPackageInvoice(customerId);
      setStage(null);
      if (!invoice) toast.info('Your package invoice is being prepared. You can pay it on the Invoices page.');
      else toast.success(`${plan.name} reserved. Pay ${money(invoice.total, invoice.currency)} to start.`);
      onSubscribed({ invoice: payNow ? invoice : null });
    } else {
      toast.success(`Welcome to ${plan.name}! You can use the service now.`);
      onSubscribed({ advance: payNow });
    }
  };

  return (
    <Modal
      open
      title={current ? `Switch to ${plan.name}` : prepaid ? `Buy ${plan.name}` : `Start ${plan.name}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" icon={!current && payNow ? <CreditCard size={16} /> : undefined} loading={busy === 'go' || !!stage} onClick={confirm}>
            {current ? 'Confirm change' : prepaid ? (payNow ? 'Continue to payment' : 'Reserve, pay later') : payNow ? 'Start and pay advance' : 'Start plan'}
          </Button>
        </>
      }
    >
      <div className="stack-sm">
        <div className="plan-price">
          {money(headlinePrice(plan), plan.currency)}
          <span>/month{prepaid ? ' up front' : ' + usage'}</span>
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
          <>
            <Field label="When should the change happen?">
              <select value={timing} onChange={(e) => setTiming(e.target.value as 'IMMEDIATE' | 'PERIOD_END')}>
                <option value="PERIOD_END">From the 1st of next month</option>
                <option value="IMMEDIATE">Now (the difference for the rest of this month is prorated)</option>
              </select>
            </Field>
            {prepaid && <Alert>Prepaid packages are paid at the start of each month. From the next month you pay before using the service.</Alert>}
          </>
        ) : (
          <>
            {perSeat && (
              <Field label="Seats">
                <input type="number" min="1" value={seats} onChange={(e) => setSeats(e.target.value)} />
              </Field>
            )}
            <div className="pay-methods" role="radiogroup" aria-label="When do you want to pay?">
              <label className={`pay-method${payNow ? ' selected' : ''}`}>
                <input type="radio" checked={payNow} onChange={() => setPayNow(true)} />
                <span className="pay-icon">
                  <CreditCard size={18} />
                </span>
                <span>
                  <b>{prepaid ? `Pay now — ${money(packageTotal, plan.currency)} incl. VAT` : 'Pay an advance now'}</b>
                  <span className="muted small">{prepaid ? 'Service starts right after payment' : 'Buy credit now; it pays your monthly invoices automatically'}</span>
                </span>
              </label>
              <label className={`pay-method${!payNow ? ' selected' : ''}`}>
                <input type="radio" checked={!payNow} onChange={() => setPayNow(false)} />
                <span className="pay-icon">
                  <CalendarClock size={18} />
                </span>
                <span>
                  <b>{prepaid ? 'Pay later' : 'Pay at the end of the month'}</b>
                  <span className="muted small">
                    {prepaid ? 'The package is reserved; usage is refused until you pay it' : 'Use the service now and pay the invoice when it arrives'}
                  </span>
                </span>
              </label>
            </div>
            {stage && <Alert>{stage}</Alert>}
          </>
        )}
      </div>
    </Modal>
  );
}
