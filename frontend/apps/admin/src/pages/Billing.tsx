import { useState } from 'react';
import { FlaskConical, GitCompare, Layers, PlayCircle, RotateCcw, User } from 'lucide-react';
import {
  Alert, Badge, Button, Card, CardHeader, Field, KeyValues, PageHeader, StatusBadge, Table,
  currentPeriod, get, money, periodLabel, post, qs, qty, toNumber, useAction,
  type BatchRunResult, type BillingOutcome, type BillingRunSummary, type VersionComparison,
} from '@meterflow/shared';
import { CustomerInput, PlanSelect, customerIds, usePlans, useSubscriptions, type Navigate } from '../common';

function OutcomeLines({ o }: { o: BillingOutcome }) {
  if (!o.lines) return null;
  return (
    <Table className="compact">
      <thead>
        <tr>
          <th>Line</th>
          <th className="num">Qty</th>
          <th className="num">Amount</th>
        </tr>
      </thead>
      <tbody>
        {o.lines.map((l, i) => (
          <tr key={i}>
            <td>
              <div className="strong">{l.description}</div>
              <div className="muted small">{l.rule}</div>
            </td>
            <td className="num">{l.quantity === undefined ? '—' : qty(l.quantity)}</td>
            <td className="num">{money(l.amount, o.currency)}</td>
          </tr>
        ))}
      </tbody>
      <tfoot>
        <tr>
          <td colSpan={2}>Subtotal · tax{o.taxRule ? ` (${o.taxRule})` : ''}</td>
          <td className="num">
            {money(o.subtotal, o.currency)} · {money(o.tax, o.currency)}
          </td>
        </tr>
        {toNumber(o.creditApplied) > 0 && (
          <tr>
            <td colSpan={2}>Credit applied</td>
            <td className="num">− {money(o.creditApplied, o.currency)}</td>
          </tr>
        )}
        <tr className="total-row">
          <td colSpan={2}>Total</td>
          <td className="num">{money(o.total, o.currency)}</td>
        </tr>
      </tfoot>
    </Table>
  );
}

export function BillingPage({ navigate }: { navigate: Navigate }) {
  const subs = useSubscriptions();
  const plans = usePlans();
  const customers = customerIds(subs.data);
  const { busy, run } = useAction();

  const [period, setPeriod] = useState(currentPeriod());
  const [summary, setSummary] = useState<BillingRunSummary | null>(null);

  const [custPeriod, setCustPeriod] = useState(currentPeriod());
  const [customerId, setCustomerId] = useState('customer');
  const [outcome, setOutcome] = useState<BillingOutcome | null>(null);

  const [batchPeriod, setBatchPeriod] = useState(currentPeriod(-1));
  const [batch, setBatch] = useState<BatchRunResult | null>(null);

  const [simCustomer, setSimCustomer] = useState('customer');
  const [simPeriod, setSimPeriod] = useState(currentPeriod());
  const [simPlan, setSimPlan] = useState('');
  const [sim, setSim] = useState<BillingOutcome | null>(null);
  const [cmp, setCmp] = useState<VersionComparison | null>(null);

  return (
    <>
      <PageHeader
        title="Billing runs"
        subtitle="Generate invoices for a period. Every run is idempotent: running the same period twice never creates a second invoice."
      />
      <div className="grid-2">
        <Card>
          <CardHeader title={<span className="row"><PlayCircle size={18} /> Run billing for all customers</span>} subtitle="Sequential run over every active subscription (small data sets)" />
          <div className="inline-form">
            <Field label="Billing period">
              <input type="month" value={period} onChange={(e) => setPeriod(e.target.value)} />
            </Field>
            <Button
              variant="primary"
              loading={busy === 'all'}
              onClick={async () => {
                const r = await run('all', () => post<BillingRunSummary>(`/invoices/run${qs({ period })}`), (s) => `${s.created} created, ${s.alreadyExisted} already existed, ${s.skipped} skipped.`);
                if (r) setSummary(r);
              }}
            >
              Run {periodLabel(period)}
            </Button>
          </div>
          {summary && (
            <div className="stack-sm" style={{ marginTop: 14 }}>
              <KeyValues items={[['Created', summary.created], ['Already existed', summary.alreadyExisted], ['Skipped', summary.skipped], ['Status', <StatusBadge status={summary.status} />]]} />
              {summary.failed.length > 0 && <Alert tone="bad">{summary.failed.join('; ')}</Alert>}
              <div>
                <Button size="sm" variant="ghost" onClick={() => navigate('invoices')}>
                  View invoices →
                </Button>
              </div>
            </div>
          )}
        </Card>

        <Card>
          <CardHeader title={<span className="row"><User size={18} /> Bill one customer</span>} subtitle="Creates, finalises and (with auto-pay) collects the invoice" />
          <div className="form-grid">
            <Field label="Customer ID">
              <CustomerInput value={customerId} onChange={setCustomerId} customers={customers} />
            </Field>
            <Field label="Billing period">
              <input type="month" value={custPeriod} onChange={(e) => setCustPeriod(e.target.value)} />
            </Field>
          </div>
          <div className="btn-row" style={{ marginTop: 12 }}>
            <Button
              variant="primary"
              loading={busy === 'one'}
              disabled={!customerId.trim()}
              onClick={async () => {
                const r = await run('one', () => post<BillingOutcome>(`/invoices/run/customer${qs({ customerId, period: custPeriod })}`), (o) =>
                  o.status === 'CREATED' ? 'Invoice created.' : o.status === 'EXISTING' ? 'Invoice already existed: nothing was billed twice.' : `Skipped: ${o.reason || ''}`,
                );
                if (r) setOutcome(r);
              }}
            >
              Bill customer
            </Button>
          </div>
          {outcome && (
            <div className="stack-sm" style={{ marginTop: 14 }}>
              <div className="row-between">
                <StatusBadge status={outcome.status} />
                {outcome.invoiceId && (
                  <Button size="sm" variant="ghost" onClick={() => navigate(`invoices/${outcome.invoiceId}`)}>
                    Open invoice →
                  </Button>
                )}
              </div>
              {outcome.reason && <Alert tone="warn">{outcome.reason}</Alert>}
              <OutcomeLines o={outcome} />
            </div>
          )}
        </Card>

        <Card>
          <CardHeader title={<span className="row"><Layers size={18} /> Partitioned batch job</span>} subtitle="Spring Batch: partitioned, chunked, restartable. Use for a closed month." />
          <div className="inline-form">
            <Field label="Closed period">
              <input type="month" value={batchPeriod} onChange={(e) => setBatchPeriod(e.target.value)} />
            </Field>
            <Button
              variant="primary"
              loading={busy === 'batch'}
              onClick={async () => {
                const r = await run('batch', () => post<BatchRunResult>(`/invoices/batch/run${qs({ period: batchPeriod })}`), (b) => `Batch job ${b.status.toLowerCase()}.`);
                if (r) setBatch(r);
              }}
            >
              Launch job
            </Button>
            <Button icon={<RotateCcw size={16} />} loading={busy === 'recover'} onClick={() => run('recover', () => post<{ recoveredExecutions: number }>('/invoices/batch/recover'), (r) => `${r.recoveredExecutions} stale execution(s) recovered.`)}>
              Recover stale runs
            </Button>
          </div>
          {batch && (
            <div style={{ marginTop: 14 }} className="stack-sm">
              <KeyValues items={[['Job', batch.jobName], ['Period', batch.period], ['Status', <StatusBadge status={batch.status} />], ['Exit', batch.exitStatus || '—'], ['Execution', batch.executionId ?? '—']]} />
              <span className="muted small">{batch.message}</span>
            </div>
          )}
        </Card>

        <Card>
          <CardHeader title={<span className="row"><FlaskConical size={18} /> Price simulator</span>} subtitle="Bill a past or current month under any plan version without creating an invoice" />
          <div className="form-grid">
            <Field label="Customer ID">
              <CustomerInput value={simCustomer} onChange={setSimCustomer} customers={customers} />
            </Field>
            <Field label="Period">
              <input type="month" value={simPeriod} onChange={(e) => setSimPeriod(e.target.value)} />
            </Field>
            <Field label="Plan version" wide>
              <PlanSelect plans={plans.data} value={simPlan} onChange={setSimPlan} allowEmpty />
            </Field>
          </div>
          <div className="btn-row" style={{ marginTop: 12 }}>
            <Button
              variant="primary"
              icon={<FlaskConical size={16} />}
              loading={busy === 'sim'}
              onClick={async () => {
                setCmp(null);
                const r = await run('sim', () => get<BillingOutcome>(`/invoices/simulate${qs({ customerId: simCustomer, period: simPeriod, planId: simPlan })}`));
                if (r) setSim(r);
              }}
            >
              Simulate
            </Button>
            <Button
              icon={<GitCompare size={16} />}
              loading={busy === 'cmp'}
              onClick={async () => {
                setSim(null);
                const r = await run('cmp', () => get<VersionComparison>(`/invoices/simulate/versions${qs({ customerId: simCustomer, period: simPeriod })}`));
                if (r) setCmp(r);
              }}
            >
              Compare all versions
            </Button>
          </div>
          {sim && (
            <div className="stack-sm" style={{ marginTop: 14 }}>
              <div className="row-between">
                <span className="strong">{sim.plan}</span>
                <Badge tone="info">Simulation only</Badge>
              </div>
              {sim.reason && <Alert tone="warn">{sim.reason}</Alert>}
              <OutcomeLines o={sim} />
            </div>
          )}
          {cmp && (
            <div style={{ marginTop: 14 }}>
              <Table className="compact">
                <thead>
                  <tr>
                    <th>Version</th>
                    <th className="num">Subtotal</th>
                    <th className="num">Tax</th>
                    <th className="num">Total</th>
                  </tr>
                </thead>
                <tbody>
                  {cmp.simulations.map((s) => (
                    <tr key={s.planId}>
                      <td>
                        v{s.planVersion} {s.current && <Badge tone="accent">current</Badge>}
                      </td>
                      <td className="num">{money(s.subtotal, s.currency)}</td>
                      <td className="num">{money(s.tax, s.currency)}</td>
                      <td className="num strong">{money(s.total, s.currency)}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </div>
          )}
        </Card>
      </div>
    </>
  );
}
