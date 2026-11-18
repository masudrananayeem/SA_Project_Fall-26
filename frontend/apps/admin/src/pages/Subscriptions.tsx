import { useMemo, useState } from 'react';
import { CalendarClock, Pause, Play, Plus, XCircle } from 'lucide-react';
import {
  Alert, Badge, Button, Card, Drawer, Empty, ErrorNote, Field, KeyValues, Modal, PageHeader, Spinner, StatusBadge, Table, Tabs,
  billingModeLabel, currentPeriod, date, daysLabel, get, isoDay, money, post, qs, toNumber, useAction, useAsync,
  type PlanChange, type Subscription, type SubscriptionAnalyticsRow,
} from '@meterflow/shared';
import { CustomerInput, PlanSelect, customerIds, latestPlans, planLabel, usePlans, useSubscriptions, type Navigate } from '../common';

export function SubscriptionsPage({ selected, navigate }: { selected?: string; navigate: Navigate }) {
  const subs = useSubscriptions();
  const plans = usePlans();
  const [creating, setCreating] = useState(false);
  const [status, setStatus] = useState<'all' | 'ACTIVE' | 'UNPAID' | 'PAUSED' | 'CANCELLED'>('all');
  const analytics = useAsync(() => get<SubscriptionAnalyticsRow[]>('/subscriptions/analytics'), []);
  const tenure = (id: string) => analytics.data?.find((r) => r.subscriptionId === id);

  const rows = useMemo(
    () =>
      (subs.data || [])
        .filter(
          (s) =>
            status === 'all' ||
            s.status === status ||
            (status === 'UNPAID' && ['PENDING_PAYMENT', 'BLOCKED', 'PAUSED_DUNNING'].includes(s.status)),
        )
        .sort((a, b) => b.periodStart.localeCompare(a.periodStart)),
    [subs.data, status],
  );
  const sub = subs.data?.find((s) => s.id === selected);

  return (
    <>
      <PageHeader
        title="Subscriptions"
        subtitle="Which customer is on which plan version. Change plans now (prorated) or at period end, adjust seats, pause, resume or cancel."
        actions={
          <Button variant="primary" icon={<Plus size={16} />} onClick={() => setCreating(true)}>
            New subscription
          </Button>
        }
      />
      <ErrorNote error={subs.error} onRetry={subs.reload} />
      <Card className="flush">
        <div className="toolbar">
          <Tabs
            value={status}
            onChange={setStatus}
            tabs={[
              { value: 'all', label: 'All' },
              { value: 'ACTIVE', label: 'Active' },
              { value: 'UNPAID', label: 'Unpaid / blocked' },
              { value: 'PAUSED', label: 'Paused' },
              { value: 'CANCELLED', label: 'Cancelled' },
            ]}
          />
          <span className="muted small">{rows.length} shown</span>
        </div>
        <div className="flush-body">
          {subs.loading && !subs.data ? (
            <Spinner />
          ) : rows.length ? (
            <Table>
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Plan</th>
                  <th>Status</th>
                  <th>On this plan</th>
                  <th className="hide-sm">Customer for</th>
                  <th>Period</th>
                  <th className="hide-sm">Seats</th>
                  <th className="hide-sm">Trial</th>
                  <th>Scheduled change</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((s) => (
                  <tr key={s.id} className="clickable" onClick={() => navigate(`subscriptions/${s.id}`)}>
                    <td className="strong">{s.customerId}</td>
                    <td>
                      {planLabel(plans.data, s.planId)}
                      <div className="muted small">{billingModeLabel(plans.data?.find((p) => p.id === s.planId)?.billingMode)}</div>
                    </td>
                    <td>
                      <StatusBadge status={s.status} />
                    </td>
                    <td>
                      <b>{daysLabel(tenure(s.id)?.daysOnPlan)}</b>
                      {tenure(s.id) && <div className="muted small">since {date(tenure(s.id)!.onPlanSince)}</div>}
                    </td>
                    <td className="hide-sm">{daysLabel(tenure(s.id)?.daysAsCustomer)}</td>
                    <td className="muted">
                      {s.periodStart} → {s.periodEnd}
                    </td>
                    <td className="hide-sm">{s.seatCount}</td>
                    <td className="hide-sm">{s.trialEnd ? <Badge tone={s.trialing ? 'info' : 'neutral'}>{s.trialing ? `until ${date(s.trialEnd)}` : s.trialStatus || 'ended'}</Badge> : '—'}</td>
                    <td className="muted">{s.scheduledPlanId ? `${planLabel(plans.data, s.scheduledPlanId)} on ${date(s.scheduledChangeDate)}` : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          ) : (
            <Empty icon={<CalendarClock size={22} />} title="No subscriptions" action={<Button variant="primary" onClick={() => setCreating(true)}>Create subscription</Button>} />
          )}
        </div>
      </Card>

      <Drawer open={!!selected} title={sub ? sub.customerId : 'Subscription'} subtitle={sub ? planLabel(plans.data, sub.planId) : undefined} onClose={() => navigate('subscriptions')}>
        {sub ? <SubscriptionDetail sub={sub} onChanged={subs.reload} /> : subs.loading ? <Spinner /> : <Alert tone="warn">Subscription not found.</Alert>}
      </Drawer>

      {creating && (
        <CreateSubscription
          customers={customerIds(subs.data)}
          onClose={() => setCreating(false)}
          onCreated={async (s) => {
            setCreating(false);
            await subs.reload();
            navigate(`subscriptions/${s.id}`);
          }}
        />
      )}
    </>
  );
}

function CreateSubscription({ customers, onClose, onCreated }: { customers: string[]; onClose: () => void; onCreated: (s: Subscription) => void }) {
  const plans = usePlans();
  const [customerId, setCustomerId] = useState('customer');
  const [planId, setPlanId] = useState('');
  const [periodStart, setPeriodStart] = useState(`${currentPeriod()}-01`);
  const [periodEnd, setPeriodEnd] = useState(isoDay(365));
  const [seats, setSeats] = useState('1');
  const [trial, setTrial] = useState(false);
  const [trialEnd, setTrialEnd] = useState(isoDay(14));
  const [autoConvert, setAutoConvert] = useState(true);
  const { busy, run } = useAction();

  const defaultPlan = planId || latestPlans(plans.data).find((p) => p.planKey === 'starter')?.id || latestPlans(plans.data)[0]?.id || '';

  async function create() {
    const s = await run(
      'create',
      () =>
        post<Subscription>('/subscriptions', {
          customerId: customerId.trim(),
          planId: defaultPlan,
          periodStart,
          periodEnd,
          seatCount: Number(seats) || 1,
          trialEnd: trial ? trialEnd : null,
          trialAutoConvert: autoConvert,
        }),
      (x) => `Subscription created for ${x.customerId}.`,
    );
    if (s) onCreated(s);
  }

  return (
    <Modal
      open
      title="New subscription"
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={busy === 'create'} disabled={!customerId.trim() || !defaultPlan} onClick={create}>
            Create subscription
          </Button>
        </>
      }
    >
      <div className="form-grid">
        <Field label="Customer ID" hint="The demo portal login “customer” sees customer ID customer" wide>
          <CustomerInput value={customerId} onChange={setCustomerId} customers={customers} />
        </Field>
        <Field label="Plan version" wide>
          <PlanSelect plans={plans.data} value={defaultPlan} onChange={setPlanId} />
        </Field>
        <Field label="Period start">
          <input type="date" value={periodStart} onChange={(e) => setPeriodStart(e.target.value)} />
        </Field>
        <Field label="Period end">
          <input type="date" value={periodEnd} onChange={(e) => setPeriodEnd(e.target.value)} />
        </Field>
        <Field label="Seats">
          <input type="number" min="1" value={seats} onChange={(e) => setSeats(e.target.value)} />
        </Field>
        <Field label="Free trial">
          <label className="check">
            <input type="checkbox" checked={trial} onChange={(e) => setTrial(e.target.checked)} /> Start with a trial
          </label>
        </Field>
        {trial && (
          <>
            <Field label="Trial ends">
              <input type="date" value={trialEnd} onChange={(e) => setTrialEnd(e.target.value)} />
            </Field>
            <Field label="After trial">
              <label className="check">
                <input type="checkbox" checked={autoConvert} onChange={(e) => setAutoConvert(e.target.checked)} /> Convert to paid automatically
              </label>
            </Field>
          </>
        )}
      </div>
    </Modal>
  );
}

function SubscriptionDetail({ sub, onChanged }: { sub: Subscription; onChanged: () => Promise<void> }) {
  const plans = usePlans();
  const history = useAsync(() => get<PlanChange[]>(`/subscriptions/${sub.id}/plan-changes`), [sub.id]);
  const [planId, setPlanId] = useState('');
  const [timing, setTiming] = useState<'IMMEDIATE' | 'PERIOD_END'>('IMMEDIATE');
  const [seats, setSeats] = useState(String(sub.seatCount));
  const { busy, run } = useAction();
  const current = plans.data?.find((p) => p.id === sub.planId);
  const candidates = (plans.data || []).filter((p) => p.id !== sub.planId && (!current || p.currency === current.currency));

  const refresh = async () => {
    await Promise.all([onChanged(), history.reload()]);
  };

  return (
    <div className="stack">
      <div className="row-between">
        <StatusBadge status={sub.status} />
        <div className="btn-row">
          {sub.status === 'ACTIVE' && (
            <Button size="sm" icon={<Pause size={14} />} loading={busy === 'pause'} onClick={() => run('pause', () => post(`/subscriptions/${sub.id}/pause`), 'Subscription paused').then(refresh)}>
              Pause
            </Button>
          )}
          {(sub.status === 'PAUSED' || sub.status === 'PAUSED_DUNNING') && (
            <Button size="sm" icon={<Play size={14} />} loading={busy === 'resume'} onClick={() => run('resume', () => post(`/subscriptions/${sub.id}/resume`), 'Subscription resumed').then(refresh)}>
              Resume
            </Button>
          )}
          {sub.status !== 'CANCELLED' && (
            <Button
              size="sm"
              variant="danger"
              icon={<XCircle size={14} />}
              loading={busy === 'cancel'}
              onClick={() => window.confirm(`Cancel ${sub.customerId}'s subscription?`) && run('cancel', () => post(`/subscriptions/${sub.id}/cancel`), 'Subscription cancelled').then(refresh)}
            >
              Cancel
            </Button>
          )}
        </div>
      </div>
      <KeyValues
        items={[
          ['Customer', sub.customerId],
          ['Plan', planLabel(plans.data, sub.planId)],
          ['Recurring', current ? `${money(current.flatFee, current.currency)} + usage` : '—'],
          ['Period', `${sub.periodStart} → ${sub.periodEnd}`],
          ['Seats', sub.seatCount],
          ['Trial', sub.trialEnd ? `${sub.trialing ? 'Active until' : 'Ended'} ${date(sub.trialEnd)}${sub.trialAutoConvert ? ' · auto-convert' : ''}` : 'None'],
          ['Scheduled change', sub.scheduledPlanId ? `${planLabel(plans.data, sub.scheduledPlanId)} on ${date(sub.scheduledChangeDate)}` : '—'],
          ['Subscription ID', <span className="mono small">{sub.id}</span>],
        ]}
      />

      {sub.status !== 'CANCELLED' && (
        <Card>
          <h4 className="section-title">Change plan</h4>
          <p className="muted small">Immediate changes post a prorated credit/charge on this month’s invoice. Period-end changes start on the 1st of next month.</p>
          <div className="form-grid">
            <Field label="New plan version" wide>
              <select value={planId} onChange={(e) => setPlanId(e.target.value)}>
                <option value="">Select a plan…</option>
                {candidates.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} — {p.planKey} v{p.version}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="When">
              <select value={timing} onChange={(e) => setTiming(e.target.value as 'IMMEDIATE' | 'PERIOD_END')}>
                <option value="IMMEDIATE">Now (prorated)</option>
                <option value="PERIOD_END">At period end</option>
              </select>
            </Field>
          </div>
          <div className="btn-row" style={{ marginTop: 12 }}>
            <Button
              variant="primary"
              disabled={!planId}
              loading={busy === 'change'}
              onClick={() =>
                run('change', () => post<{ timing: string; prorationDelta?: number; currency?: string; effectiveDate?: string }>(`/subscriptions/${sub.id}/change-plan`, { planId, timing }), (r) =>
                  r.timing === 'IMMEDIATE' ? `Plan changed now. Proration ${money(r.prorationDelta, r.currency)} added to this month.` : `Change scheduled for ${date(r.effectiveDate)}.`,
                ).then(refresh)
              }
            >
              Change plan
            </Button>
          </div>
        </Card>
      )}

      {sub.status !== 'CANCELLED' && (
        <div className="inline-form">
          <Field label="Seats">
            <input type="number" min="1" value={seats} onChange={(e) => setSeats(e.target.value)} />
          </Field>
          <Button loading={busy === 'seats'} disabled={Number(seats) < 1 || Number(seats) === sub.seatCount} onClick={() => run('seats', () => post(`/subscriptions/${sub.id}/seats${qs({ count: seats })}`), 'Seat count updated').then(refresh)}>
            Update seats
          </Button>
        </div>
      )}

      <div>
        <h4 className="section-title">Plan change history</h4>
        {history.loading && !history.data ? (
          <Spinner />
        ) : history.data?.length ? (
          <Table className="compact">
            <thead>
              <tr>
                <th>Effective</th>
                <th>Change</th>
                <th>Kind</th>
                <th className="num">Proration</th>
              </tr>
            </thead>
            <tbody>
              {history.data.map((c) => (
                <tr key={c.id}>
                  <td>{date(c.effectiveDate)}</td>
                  <td>
                    {planLabel(plans.data, c.fromPlanId)} → {planLabel(plans.data, c.toPlanId)}
                    {c.explanation && <div className="muted small">{c.explanation}</div>}
                  </td>
                  <td>
                    <Badge>{c.kind}</Badge>
                  </td>
                  <td className="num">{toNumber(c.proratedDelta) === 0 ? '—' : money(c.proratedDelta, c.currency)}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          <Empty title="No plan changes yet" />
        )}
      </div>
    </div>
  );
}
