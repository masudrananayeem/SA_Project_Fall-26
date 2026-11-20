import { useEffect, useState } from 'react';
import { CalendarClock, Check, Layers, PlusCircle, Save, UserCircle, Wallet, XCircle } from 'lucide-react';
import {
  Alert, Badge, Button, Card, CardHeader, Empty, ErrorNote, Field, KeyValues, Modal, PageHeader, Spinner, Stat, StatusBadge, Table, TopUpModal, billingModeLabel,
  date, dateTime, get, money, post, put, qs, toNumber, useAction, useAsync,
  type Account, type CreditAccount, type CreditNote, type Plan, type PlanChange, type Subscription,
} from '@meterflow/shared';
import { priceLines } from '../plans';
import { AmountDueCard, useAccountState } from '../status';

export function AccountPage({ customerId, navigate }: { customerId: string; navigate: (to: string) => void }) {
  const subs = useAsync(() => get<Subscription[]>('/subscriptions'), [customerId]);
  const plans = useAsync(() => get<Plan[]>('/catalogue/plans'), []);
  const credit = useAsync(() => get<CreditAccount>(`/credits${qs({ customerId })}`), [customerId]);
  const notes = useAsync(() => get<CreditNote[]>(`/credits/notes${qs({ customerId })}`), [customerId]);
  const sorted = (subs.data || []).slice().sort((a, b) => b.periodStart.localeCompare(a.periodStart));
  const current = sorted.find((s) => s.status !== 'CANCELLED');
  const plan = plans.data?.find((p) => p.id === current?.planId);
  const scheduled = plans.data?.find((p) => p.id === current?.scheduledPlanId);
  const history = useAsync(() => (current ? get<PlanChange[]>(`/subscriptions/${current.id}/plan-changes`) : Promise.resolve([] as PlanChange[])), [current?.id]);
  const account = useAccountState(customerId);
  const [cancelling, setCancelling] = useState(false);
  const [topUp, setTopUp] = useState(false);
  const planName = (id: string) => {
    const p = plans.data?.find((x) => x.id === id);
    return p ? `${p.name} v${p.version}` : id.slice(0, 8);
  };

  return (
    <>
      <PageHeader title="Account" subtitle="Your profile, subscription, prices and prepaid credit." />
      <ErrorNote error={subs.error || plans.error} onRetry={subs.reload} />
      <div style={{ marginBottom: 18 }}>
        <AmountDueCard
          state={{
            ...account,
            reload: async () => {
              await Promise.all([account.reload(), subs.reload(), credit.reload(), history.reload()]);
            },
          }}
        />
      </div>
      <div className="grid-3">
        <div className="stack">
          <Card>
            <CardHeader
              title={<span className="row"><CalendarClock size={18} /> Subscription</span>}
              actions={
                current && (
                  <>
                    <Button size="sm" icon={<Layers size={14} />} onClick={() => navigate('plans')}>
                      Change plan
                    </Button>
                    <Button size="sm" variant="danger" icon={<XCircle size={14} />} onClick={() => setCancelling(true)}>
                      Cancel
                    </Button>
                  </>
                )
              }
            />
            {subs.loading && !subs.data ? (
              <Spinner />
            ) : !current ? (
              <Empty title="No active subscription" action={<Button variant="primary" onClick={() => navigate('plans')}>Choose a plan</Button>}>
                {sorted.length ? 'Your previous subscription was cancelled.' : 'Pick a plan to start using MeterFlow.'}
              </Empty>
            ) : (
              <div className="stack">
                <div className="row-between">
                  <div>
                    <div className="strong" style={{ fontSize: 18 }}>
                      {plan ? plan.name : 'Plan'} {plan && <Badge tone="accent">v{plan.version}</Badge>}{' '}
                      {plan && <Badge tone={plan.billingMode === 'PREPAID' ? 'warn' : 'info'}>{billingModeLabel(plan.billingMode)}</Badge>}
                    </div>
                    <div className="muted">
                      {date(current.periodStart)} → {date(current.periodEnd)} · {current.seatCount} seat{current.seatCount > 1 ? 's' : ''}
                    </div>
                  </div>
                  <StatusBadge status={current.status} />
                </div>
                {current.trialing && <Badge tone="info">Free trial until {date(current.trialEnd)}</Badge>}
                {current.status === 'PAUSED_DUNNING' && <Alert tone="bad">Paused because an invoice is unpaid. Paying it resumes the service.</Alert>}
                {scheduled && (
                  <Alert>
                    Your plan changes to <b>{scheduled.name} v{scheduled.version}</b> on {date(current.scheduledChangeDate)}.
                  </Alert>
                )}
                {plan && (
                  <ul className="plan-features">
                    {priceLines(plan).map((l) => (
                      <li key={l}>
                        <Check size={15} />
                        <span>{l}</span>
                      </li>
                    ))}
                    <li>
                      <Check size={15} />
                      <span>VAT {toNumber(plan.taxRate)}% added on each invoice</span>
                    </li>
                  </ul>
                )}
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
          <ProfileCard customerId={customerId} />
        </div>
        <div className="stack">
          <Stat label="Prepaid credit" value={credit.data ? money(credit.data.balance, credit.data.currency) : '…'} hint="Applied automatically to your next invoice" icon={<Wallet size={17} />} tone="good" />
          <Button variant="primary" icon={<PlusCircle size={16} />} onClick={() => setTopUp(true)}>
            Add prepaid credit
          </Button>
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
      {topUp && (
        <TopUpModal
          currency={credit.data?.currency || plan?.currency || 'BDT'}
          onClose={() => setTopUp(false)}
          onDone={() => {
            setTopUp(false);
            void credit.reload();
          }}
        />
      )}
      {cancelling && current && <CancelModal sub={current} onClose={() => setCancelling(false)} onDone={async () => (setCancelling(false), await subs.reload())} />}
    </>
  );
}

function CancelModal({ sub, onClose, onDone }: { sub: Subscription; onClose: () => void; onDone: () => void }) {
  const { busy, run } = useAction();
  return (
    <Modal
      open
      title="Cancel subscription?"
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Keep subscription
          </Button>
          <Button variant="danger" loading={busy === 'c'} onClick={async () => (await run('c', () => post(`/subscriptions/${sub.id}/cancel`), 'Subscription cancelled.')) !== undefined && onDone()}>
            Cancel subscription
          </Button>
        </>
      }
    >
      <p style={{ margin: 0 }}>Usage already recorded this month is still billed on the next invoice. You can subscribe again at any time from the Plans page.</p>
    </Modal>
  );
}

function ProfileCard({ customerId }: { customerId: string }) {
  const account = useAsync(() => get<Account>('/account'), [customerId]);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [company, setCompany] = useState('');
  const { busy, run } = useAction();
  useEffect(() => {
    if (account.data) {
      setName(account.data.displayName || '');
      setEmail(account.data.email || '');
      setCompany(account.data.company || '');
    }
  }, [account.data]);

  return (
    <Card>
      <CardHeader title={<span className="row"><UserCircle size={18} /> Profile</span>} subtitle={account.data?.createdAt ? `Member since ${date(account.data.createdAt)}` : undefined} />
      {account.loading && !account.data ? (
        <Spinner />
      ) : account.error ? (
        <ErrorNote error={account.error} onRetry={account.reload} />
      ) : !account.data?.stored ? (
        <KeyValues items={[['Customer ID', customerId], ['Account', 'Built-in demo account (profile not editable)']]} />
      ) : (
        <div className="stack-sm">
          <div className="form-grid">
            <Field label="Customer ID">
              <input value={customerId} disabled />
            </Field>
            <Field label="Name">
              <input value={name} onChange={(e) => setName(e.target.value)} />
            </Field>
            <Field label="Email">
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            </Field>
            <Field label="Company">
              <input value={company} onChange={(e) => setCompany(e.target.value)} />
            </Field>
          </div>
          <div>
            <Button
              variant="primary"
              icon={<Save size={16} />}
              loading={busy === 'save'}
              disabled={!name.trim() || !email.trim()}
              onClick={() => run('save', () => put<Account>('/account', { displayName: name, email, company }), 'Profile saved.').then(() => account.reload())}
            >
              Save profile
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}
