import { CalendarClock, Wallet } from 'lucide-react';
import {
  Badge, Card, CardHeader, Empty, ErrorNote, KeyValues, PageHeader, Spinner, Stat, StatusBadge, Table,
  date, dateTime, get, money, qs, toNumber, useAsync,
  type ChargeDefinition, type CreditAccount, type CreditNote, type Plan, type PlanChange, type Subscription,
} from '@meterflow/shared';

function chargeList(p: Plan): ChargeDefinition[] {
  try {
    return p.chargeDefinitionsJson ? (JSON.parse(p.chargeDefinitionsJson) as ChargeDefinition[]) : [];
  } catch {
    return [];
  }
}

export function AccountPage({ customerId }: { customerId: string }) {
  const subs = useAsync(() => get<Subscription[]>('/subscriptions'), [customerId]);
  const plans = useAsync(() => get<Plan[]>('/catalogue/plans'), []);
  const credit = useAsync(() => get<CreditAccount>(`/credits${qs({ customerId })}`), [customerId]);
  const notes = useAsync(() => get<CreditNote[]>(`/credits/notes${qs({ customerId })}`), [customerId]);
  const sorted = (subs.data || []).slice().sort((a, b) => b.periodStart.localeCompare(a.periodStart));
  const current = sorted.find((s) => s.status !== 'CANCELLED') || sorted[0];
  const plan = plans.data?.find((p) => p.id === current?.planId);
  const scheduled = plans.data?.find((p) => p.id === current?.scheduledPlanId);
  const history = useAsync(() => (current ? get<PlanChange[]>(`/subscriptions/${current.id}/plan-changes`) : Promise.resolve([] as PlanChange[])), [current?.id]);
  const planName = (id: string) => {
    const p = plans.data?.find((x) => x.id === id);
    return p ? `${p.name} v${p.version}` : id.slice(0, 8);
  };
  const charges = plan ? chargeList(plan) : [];

  return (
    <>
      <PageHeader title="Plan & credits" subtitle="Your subscription, the prices that apply to you, and any prepaid credit." />
      <ErrorNote error={subs.error || plans.error} onRetry={subs.reload} />
      <div className="grid-3">
        <Card>
          <CardHeader title={<span className="row"><CalendarClock size={18} /> Subscription</span>} />
          {subs.loading && !subs.data ? (
            <Spinner />
          ) : !current ? (
            <Empty title="No subscription">Contact your account manager to get started.</Empty>
          ) : (
            <div className="stack">
              <div className="row-between">
                <div>
                  <div className="strong" style={{ fontSize: 18 }}>
                    {plan ? plan.name : 'Plan'} {plan && <Badge tone="accent">v{plan.version}</Badge>}
                  </div>
                  <div className="muted">
                    {current.periodStart} → {current.periodEnd}
                  </div>
                </div>
                <StatusBadge status={current.status} />
              </div>
              {current.trialing && <Badge tone="info">Free trial until {date(current.trialEnd)}</Badge>}
              {scheduled && (
                <div className="alert alert-info">
                  <div>
                    Your plan changes to <b>{scheduled.name} v{scheduled.version}</b> on {date(current.scheduledChangeDate)}.
                  </div>
                </div>
              )}
              {plan &&
                (charges.length ? (
                  <Table className="compact">
                    <thead>
                      <tr>
                        <th>Charge</th>
                        <th>Type</th>
                        <th className="num">Price</th>
                      </tr>
                    </thead>
                    <tbody>
                      {charges.map((c, i) => (
                        <tr key={i}>
                          <td>{c.name}</td>
                          <td className="muted">{c.type.replace(/_/g, ' ')}</td>
                          <td className="num">{c.tiers?.length ? c.tiers.map((t) => `≤${t.upTo ?? '∞'}: ${t.unitPrice}`).join(' · ') : money(c.amount, plan.currency)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </Table>
                ) : (
                  <KeyValues
                    items={[
                      ['Monthly fee', money(plan.flatFee, plan.currency)],
                      ['Price per unit', `${money(plan.unitPrice, plan.currency)} per ${plan.metric}`],
                      ['Included free', `${toNumber(plan.freeAllowance).toLocaleString()} ${plan.metric}`],
                      ['Minimum', money(plan.minimumCommitment, plan.currency)],
                      ['VAT', `${toNumber(plan.taxRate)}%`],
                      ['Seats', current.seatCount],
                    ]}
                  />
                ))}
              <div>
                <h4 className="section-title">Plan changes</h4>
                {history.data?.length ? (
                  <Table className="compact">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Change</th>
                        <th className="num">Adjustment</th>
                      </tr>
                    </thead>
                    <tbody>
                      {history.data.map((c) => (
                        <tr key={c.id}>
                          <td>{date(c.effectiveDate)}</td>
                          <td>
                            {planName(c.fromPlanId)} → {planName(c.toPlanId)}
                          </td>
                          <td className="num">{toNumber(c.proratedDelta) === 0 ? '—' : money(c.proratedDelta, c.currency)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </Table>
                ) : (
                  <p className="muted small">No changes yet.</p>
                )}
              </div>
            </div>
          )}
        </Card>
        <div className="stack">
          <Stat label="Prepaid credit" value={credit.data ? money(credit.data.balance, credit.data.currency) : '…'} hint="Applied automatically to your next invoice" icon={<Wallet size={17} />} tone="good" />
          <Card>
            <CardHeader title="Credit notes" />
            {notes.data?.length ? (
              <div className="list">
                {notes.data.map((n) => (
                  <div key={n.id} className="list-item">
                    <div style={{ flex: 1 }}>
                      <div>{n.reason}</div>
                      <div className="muted small">{dateTime(n.createdAt)}</div>
                    </div>
                    <b>{money(n.amount, n.currency)}</b>
                  </div>
                ))}
              </div>
            ) : (
              <Empty title="No credit notes" />
            )}
          </Card>
        </div>
      </div>
    </>
  );
}
