import { useState } from 'react';
import { AlertTriangle, ArrowUpCircle, Ban, CheckCircle2, CreditCard, Hourglass } from 'lucide-react';
import {
  BalanceCard, Button, PayInvoiceModal, PayUpgradeModal, date, get, money, periodLabel, qs, toNumber, useAsync, currentPeriod,
  type BalanceDue, type Invoice, type PayableUpgrade, type Plan, type Subscription,
} from '@meterflow/shared';

export interface AccountState {
  subscription?: Subscription;
  plan?: Plan;
  unpaid: Invoice[];
  /** Amount due now (unpaid invoices + plan-upgrade balances), paid this month, credit. */
  balance?: BalanceDue | null;
  reload: () => Promise<void>;
  loading: boolean;
}

/** The customer's live subscription, its plan and the invoices still waiting for payment. */
export function useAccountState(customerId: string): AccountState {
  const data = useAsync(async () => {
    const [subs, plans, invoices, balance] = await Promise.all([
      get<Subscription[]>('/subscriptions'),
      get<Plan[]>('/catalogue/plans'),
      get<Invoice[]>(`/invoices${qs({ customerId })}`),
      get<BalanceDue>(`/payments/balance${qs({ customerId })}`).catch(() => null),
    ]);
    const subscription = subs
      .slice()
      .sort((a, b) => b.periodStart.localeCompare(a.periodStart))
      .find((s) => s.status !== 'CANCELLED' && s.status !== 'EXPIRED');
    return {
      subscription,
      plan: plans.find((p) => p.id === subscription?.planId),
      unpaid: invoices.filter((i) => i.status === 'FINALISED' && toNumber(i.total) > 0).sort((a, b) => a.period.localeCompare(b.period)),
      balance,
    };
  }, [customerId]);
  return { ...(data.data || { unpaid: [] }), reload: data.reload, loading: data.loading };
}

/**
 * Tells the customer, in one line, whether they can use the service and what to pay:
 * prepaid package awaiting payment, account blocked for unpaid invoices, or all good.
 */
export function AccountStatusBanner({ state, compact }: { state: AccountState; compact?: boolean }) {
  const [paying, setPaying] = useState<Invoice | null>(null);
  const [payingUpgrade, setPayingUpgrade] = useState<PayableUpgrade | null>(null);
  const { subscription: sub, plan, unpaid } = state;
  if (!sub) return null;
  const upgrades = state.balance?.upgradeCharges || [];
  const firstUpgrade = upgrades[0];
  const packageUnpaid = unpaid.some((i) => i.period === currentPeriod());
  const due = unpaid.reduce((a, i) => a + toNumber(i.total), 0);
  const currency = unpaid[0]?.currency || plan?.currency || 'BDT';
  const first = unpaid[0];
  const modal = (
    <>
      {paying && (
        <PayInvoiceModal
          invoice={paying}
          onClose={() => setPaying(null)}
          onPaid={() => {
            setPaying(null);
            void state.reload();
          }}
        />
      )}
      {payingUpgrade && (
        <PayUpgradeModal
          charge={payingUpgrade}
          onClose={() => setPayingUpgrade(null)}
          onPaid={() => {
            setPayingUpgrade(null);
            void state.reload();
          }}
        />
      )}
    </>
  );
  const upgradeHero = (tone: 'warn' | 'bad') =>
    firstUpgrade && (
      <>
        {modal}
        <div className={`status-hero ${tone}`}>
          <ArrowUpCircle size={22} />
          <div>
            <b>
              {tone === 'bad' ? 'Service paused: ' : 'Plan upgraded: '}
              {money(firstUpgrade.total, firstUpgrade.currency)} still to pay for {firstUpgrade.toPlan}
            </b>
            <div className="muted small">
              You already paid {money(firstUpgrade.alreadyPaid, firstUpgrade.currency)} for {periodLabel(firstUpgrade.period)} on {firstUpgrade.fromPlan}. The
              rest of this month on the bigger plan costs {money(firstUpgrade.total, firstUpgrade.currency)} incl. VAT.{' '}
              {tone === 'bad' ? 'Usage is refused until it is paid.' : `Please pay by ${date(firstUpgrade.payBy)}; after that the service pauses until it is paid.`}
            </div>
          </div>
          <Button variant="primary" icon={<CreditCard size={16} />} onClick={() => setPayingUpgrade({ ...firstUpgrade, id: firstUpgrade.chargeId })}>
            Pay {money(firstUpgrade.total, firstUpgrade.currency)} now
          </Button>
        </div>
      </>
    );

  if (sub.status === 'PENDING_PAYMENT' && !packageUnpaid && firstUpgrade) return upgradeHero('bad');
  if (sub.status === 'PENDING_PAYMENT') {
    return (
      <>
        {modal}
        <div className="status-hero warn">
          <Hourglass size={22} />
          <div>
            <b>{plan?.name ? `${plan.name}: waiting for payment.` : 'Your prepaid package is waiting for payment.'}</b>
            <div className="muted small">
              Prepaid plans are paid before use: usage is refused until {first ? `${money(first.total, first.currency)} for ${periodLabel(first.period)}` : 'the package invoice'} is paid.
              {!first && ' The invoice is being prepared — refresh in a moment.'}
            </div>
          </div>
          {first && (
            <Button variant="primary" icon={<CreditCard size={16} />} onClick={() => setPaying(first)}>
              Pay {money(first.total, first.currency)} now
            </Button>
          )}
        </div>
      </>
    );
  }
  if (sub.status === 'BLOCKED' || sub.status === 'PAUSED_DUNNING') {
    return (
      <>
        {modal}
        <div className="status-hero bad">
          <Ban size={22} />
          <div>
            <b>{sub.status === 'BLOCKED' ? `Account blocked: ${unpaid.length} unpaid invoice${unpaid.length === 1 ? '' : 's'}.` : 'Service suspended for non-payment.'}</b>
            <div className="muted small">
              You used the service for {unpaid.length} month{unpaid.length === 1 ? '' : 's'} without paying ({money(due, currency)} due). Usage is refused until you pay; paying the oldest invoice usually unblocks the account.
            </div>
          </div>
          {first && (
            <Button variant="primary" icon={<CreditCard size={16} />} onClick={() => setPaying(first)}>
              Pay {periodLabel(first.period)}
            </Button>
          )}
        </div>
      </>
    );
  }
  if (firstUpgrade && !unpaid.length) return upgradeHero('warn');
  if (unpaid.length) {
    return (
      <>
        {modal}
        <div className="status-hero warn">
          <AlertTriangle size={22} />
          <div>
            <b>
              {unpaid.length} invoice{unpaid.length === 1 ? '' : 's'} waiting for payment — {money(due, currency)}
            </b>
            <div className="muted small">Nothing is charged automatically. Postpaid accounts are blocked after 2 unpaid invoices.</div>
          </div>
          <Button variant="primary" icon={<CreditCard size={16} />} onClick={() => setPaying(first)}>
            Pay {periodLabel(first.period)}
          </Button>
        </div>
      </>
    );
  }
  if (compact) return null;
  return (
    <div className="status-hero good">
      <CheckCircle2 size={22} />
      <div>
        <b>{plan?.billingMode === 'PREPAID' ? `Prepaid package paid for ${periodLabel(currentPeriod())}.` : 'Your account is in good standing.'}</b>
        <div className="muted small">
          {plan?.billingMode === 'PREPAID'
            ? 'Your next package invoice is issued on the 1st of next month; pay it to keep using the service.'
            : 'You use the service now and receive an invoice at the end of the month.'}
        </div>
      </div>
    </div>
  );
}

/**
 * "Amount due" card for the customer: what is still to pay now (invoices and plan-upgrade
 * balances), what was paid this month and the credit balance. Postpaid plans also see what the
 * month-end invoice is expected to be after their credit is used.
 */
export function AmountDueCard({ state, estimateTotal, estimateCurrency }: { state: AccountState; estimateTotal?: number | null; estimateCurrency?: string }) {
  const b = state.balance;
  if (!b || !state.subscription) return null;
  const prepaid = state.plan?.billingMode === 'PREPAID';
  const credit = toNumber(b.creditBalance);
  const extra =
    !prepaid && estimateTotal != null ? (
      <div className="balance-foot muted small">
        This month so far (estimate): <b>{money(estimateTotal, estimateCurrency || b.currency)}</b>
        {credit > 0 && (
          <>
            {' '}− credit {money(credit, b.creditCurrency)} = expected to pay at month end{' '}
            <b>{money(Math.max(0, estimateTotal - credit), estimateCurrency || b.currency)}</b>
          </>
        )}
        . Postpaid: invoiced on the 1st, then you pay it.
      </div>
    ) : prepaid ? (
      <div className="balance-foot muted small">Prepaid: each month's package is paid before use. Upgrades in the middle of a month only cost the difference for the days that are left.</div>
    ) : null;
  return <BalanceCard balance={b} onChanged={() => void state.reload()} extra={extra} />;
}
