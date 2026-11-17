import { useState } from 'react';
import { CheckCircle2, Clock, FastForward, ShieldAlert, XCircle } from 'lucide-react';
import {
  Alert, Button, Card, CardHeader, Empty, ErrorNote, Field, PageHeader, Spinner, StatusBadge, Table,
  dateTime, get, invoiceNo, post, qs, useAction, useAsync,
  type DunningCase,
} from '@meterflow/shared';
import { CustomerInput, customerIds, useSubscriptions, type Navigate } from '../common';

const STEPS = [
  { state: 'RETRYING', text: 'Retries on day 1, 3 and 7 after the failed charge' },
  { state: 'PAST_DUE', text: 'Grace period with one final attempt' },
  { state: 'SUSPENDED', text: 'Subscription paused until payment' },
  { state: 'REACTIVATED', text: 'Payment succeeded, subscription resumed' },
];

export function DunningPage({ navigate }: { navigate: Navigate }) {
  const cases = useAsync(() => get<DunningCase[]>('/dunning'), []);
  const subs = useSubscriptions();
  const { busy, run } = useAction();
  const [days, setDays] = useState('0');
  const [customerId, setCustomerId] = useState('customer');

  const rows = (cases.data || []).slice().sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  return (
    <>
      <PageHeader
        title="Dunning"
        subtitle="Failed payments are retried automatically (State pattern). Fast-forward time to watch a case move through the schedule."
        actions={<Button onClick={() => cases.reload()}>Refresh</Button>}
      />
      <div className="steps">
        {STEPS.map((s, i) => (
          <div key={s.state} className="step">
            <span className="step-no">{i + 1}</span>
            <div>
              <StatusBadge status={s.state} />
              <div className="muted small">{s.text}</div>
            </div>
          </div>
        ))}
      </div>
      <div className="grid-2" style={{ marginBottom: 20 }}>
        <Card>
          <CardHeader title={<span className="row"><FastForward size={18} /> Process due retries</span>} subtitle="Runs every retry that is due as of the chosen time (a scheduler also does this)" />
          <div className="inline-form">
            <Field label="As of">
              <select value={days} onChange={(e) => setDays(e.target.value)}>
                <option value="0">Now</option>
                <option value="1">Now + 1 day</option>
                <option value="3">Now + 3 days</option>
                <option value="7">Now + 7 days</option>
                <option value="14">Now + 14 days</option>
                <option value="30">Now + 30 days</option>
              </select>
            </Field>
            <Button
              variant="primary"
              icon={<Clock size={16} />}
              loading={busy === 'process'}
              onClick={() =>
                run('process', () => post<{ processed: number; recovered: number; failedAgain: number }>(`/dunning/process${qs({ asOf: new Date(Date.now() + Number(days) * 86400000).toISOString() })}`), (r) =>
                  `${r.processed} processed: ${r.recovered} recovered, ${r.failedAgain} failed again.`,
                ).then(() => cases.reload())
              }
            >
              Process retries
            </Button>
          </div>
        </Card>
        <Card>
          <CardHeader title={<span className="row"><ShieldAlert size={18} /> Mock payment provider</span>} subtitle="Make a customer's charges decline or succeed to test dunning" />
          <div className="inline-form">
            <Field label="Customer ID">
              <CustomerInput value={customerId} onChange={setCustomerId} customers={customerIds(subs.data)} />
            </Field>
            <Button variant="danger" loading={busy === 'decline'} onClick={() => run('decline', () => post(`/payments/mock/customers/${encodeURIComponent(customerId)}/outcome${qs({ succeed: false })}`), `Charges for ${customerId} will now decline.`)}>
              Decline
            </Button>
            <Button loading={busy === 'accept'} onClick={() => run('accept', () => post(`/payments/mock/customers/${encodeURIComponent(customerId)}/outcome${qs({ succeed: true })}`), `Charges for ${customerId} will now succeed.`)}>
              Succeed
            </Button>
          </div>
        </Card>
      </div>
      <ErrorNote error={cases.error} onRetry={cases.reload} />
      <Card className="flush">
        <CardHeader title="Cases" subtitle={`${rows.length} total`} />
        <div className="flush-body">
          {cases.loading && !cases.data ? (
            <Spinner />
          ) : rows.length ? (
            <Table>
              <thead>
                <tr>
                  <th>Invoice</th>
                  <th>Customer</th>
                  <th>State</th>
                  <th>Attempt</th>
                  <th>Next attempt</th>
                  <th>Opened</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((c) => {
                  const open = ['RETRYING', 'PAST_DUE', 'SUSPENDED'].includes(c.status);
                  return (
                    <tr key={c.id}>
                      <td>
                        <a href={`#/invoices/${c.invoiceId}`} onClick={(e) => (e.preventDefault(), navigate(`invoices/${c.invoiceId}`))} className="mono">
                          {invoiceNo(c.invoiceId)}
                        </a>
                      </td>
                      <td>{c.customerId}</td>
                      <td>
                        <StatusBadge status={c.status} />
                      </td>
                      <td>{c.attempt}</td>
                      <td className="muted">{open ? dateTime(c.nextAttemptAt) : '—'}</td>
                      <td className="muted">{dateTime(c.createdAt)}</td>
                      <td>
                        {open && (
                          <div className="btn-row">
                            <Button size="sm" icon={<CheckCircle2 size={14} />} loading={busy === `ok-${c.id}`} onClick={() => run(`ok-${c.id}`, () => post(`/dunning/${c.invoiceId}/succeeded`), 'Payment recorded. Case resolved.').then(() => cases.reload())}>
                              Paid
                            </Button>
                            <Button size="sm" variant="ghost" icon={<XCircle size={14} />} loading={busy === `no-${c.id}`} onClick={() => run(`no-${c.id}`, () => post(`/dunning/${c.invoiceId}/failed`), 'Failure recorded.').then(() => cases.reload())}>
                              Failed
                            </Button>
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>
          ) : (
            <Empty title="No dunning cases">
              Open an unpaid invoice and choose “Simulate failed payment”, or set the mock provider to decline before billing.
            </Empty>
          )}
        </div>
      </Card>
      <div style={{ marginTop: 16 }}>
        <Alert>Tip: set the provider to Decline for the demo customer, run billing, then fast-forward 1, 3 and 7 days to see RETRYING → PAST_DUE → SUSPENDED.</Alert>
      </div>
    </>
  );
}
