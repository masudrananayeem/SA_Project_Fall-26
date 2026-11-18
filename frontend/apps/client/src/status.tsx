import { useState } from 'react';
import { AlertTriangle, Ban, CheckCircle2, CreditCard, Hourglass } from 'lucide-react';
import {
  Button, PayInvoiceModal, get, money, periodLabel, qs, toNumber, useAsync, currentPeriod,
  type Invoice, type Plan, type Subscription,
} from '@meterflow/shared';

export interface AccountState {
  subscription?: Subscription;
  plan?: Plan;
  unpaid: Invoice[];
  reload: () => Promise<void>;
  loading: boolean;
}

/** The customer's live subscription, its plan and the invoices still waiting for payment. */
export function useAccountState(customerId: string): AccountState {
  const data = useAsync(async () => {
    const [subs, plans, invoices] = await Promise.all([
      get<Subscription[]>('/subscriptions'),
      get<Plan[]>('/catalogue/plans'),
      get<Invoice[]>(`/invoices${qs({ customerId })}`),
    ]);
    const subscription = subs
      .slice()
      .sort((a, b) => b.periodStart.localeCompare(a.periodStart))
      .find((s) => s.status !== 'CANCELLED' && s.status !== 'EXPIRED');
    return {
      subscription,
      plan: plans.find((p) => p.id === subscription?.planId),
      unpaid: invoices.filter((i) => i.status === 'FINALISED' && toNumber(i.total) > 0).sort((a, b) => a.period.localeCompare(b.period)),
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
  const { subscription: sub, plan, unpaid } = state;
  if (!sub) return null;
  const due = unpaid.reduce((a, i) => a + toNumber(i.total), 0);
  const currency = unpaid[0]?.currency || plan?.currency || 'BDT';
  const first = unpaid[0];
  const modal = paying && (
    <PayInvoiceModal
      invoice={paying}
      onClose={() => setPaying(null)}
      onPaid={() => {
        setPaying(null);
        void state.reload();
      }}
    />
  );

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
