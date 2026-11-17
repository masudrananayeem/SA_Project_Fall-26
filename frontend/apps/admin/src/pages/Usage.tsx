import { useState } from 'react';
import { Copy, Repeat, Send, Sigma } from 'lucide-react';
import {
  Alert, Badge, Button, Card, CardHeader, Field, PageHeader, Table,
  currentPeriod, dateTime, get, parseJsonObject, post, qs, qty, uid, useAction,
  type Aggregation, type IngestResult, type UsageEventInput, type UsageSummary,
} from '@meterflow/shared';
import { CustomerInput, customerIds, useSubscriptions } from '../common';

type TimeMode = 'now' | 'spread' | 'late';

function monthStartIso() {
  const d = new Date();
  return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1)).toISOString().slice(0, 16);
}

export function UsagePage() {
  const subs = useSubscriptions();
  const customers = customerIds(subs.data);
  return (
    <>
      <PageHeader
        title="Usage metering"
        subtitle="Send usage events, prove idempotency with duplicates, and query billable metrics with any aggregation and property filter."
      />
      <div className="grid-2">
        <IngestCard customers={customers} />
        <div className="stack">
          <AggregateCard customers={customers} />
          <SummaryCard customers={customers} />
        </div>
      </div>
    </>
  );
}

function IngestCard({ customers }: { customers: string[] }) {
  const [customerId, setCustomerId] = useState('customer');
  const [metric, setMetric] = useState('api_calls');
  const [count, setCount] = useState('100');
  const [quantity, setQuantity] = useState('1');
  const [props, setProps] = useState('{"region":"ap-south-1","endpoint":"/v1/chat"}');
  const [mode, setMode] = useState<TimeMode>('now');
  const [last, setLast] = useState<UsageEventInput[] | null>(null);
  const [result, setResult] = useState<{ r: IngestResult; replay: boolean } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { busy, run } = useAction();

  function build(): UsageEventInput[] | null {
    const properties = parseJsonObject(props);
    if (properties === null) {
      setError('Properties must be a JSON object.');
      return null;
    }
    const n = Math.min(Math.max(Number(count) || 1, 1), 1000);
    const now = Date.now();
    const start = Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth(), 1);
    // Late event: 23:00 UTC on the last day of the previous month.
    const lateTs = start - 3600_000;
    return Array.from({ length: n }, (_, i) => ({
      customerId: customerId.trim(),
      metric: metric.trim(),
      quantity: Number(quantity) || 1,
      timestamp: new Date(mode === 'late' ? lateTs - i * 1000 : mode === 'spread' ? start + Math.floor((i / n) * Math.max(now - 60_000 - start, 1)) : now - i * 1000).toISOString(),
      idempotencyKey: uid('ui'),
      properties,
    }));
  }

  async function send(events: UsageEventInput[], replay: boolean) {
    setError(null);
    const r = await run(replay ? 'replay' : 'send', () => post<IngestResult>('/usage/events:batch', { events }), (x) =>
      replay ? `Re-sent ${x.received}: ${x.duplicates} recognised as duplicates.` : `${x.accepted} events accepted.`,
    );
    if (r) {
      setResult({ r, replay });
      if (!replay) setLast(events);
    }
  }

  return (
    <Card>
      <CardHeader title="Ingest usage events" subtitle="POST /api/usage/events:batch · up to 1,000 per call · each event needs a unique idempotency key" />
      <div className="stack">
        <div className="form-grid">
          <Field label="Customer ID">
            <CustomerInput value={customerId} onChange={setCustomerId} customers={customers} />
          </Field>
          <Field label="Metric">
            <input value={metric} onChange={(e) => setMetric(e.target.value)} />
          </Field>
          <Field label="Number of events">
            <input type="number" min="1" max="1000" value={count} onChange={(e) => setCount(e.target.value)} />
          </Field>
          <Field label="Quantity per event">
            <input type="number" min="0.00000001" step="any" value={quantity} onChange={(e) => setQuantity(e.target.value)} />
          </Field>
          <Field label="Event timestamps" wide>
            <select value={mode} onChange={(e) => setMode(e.target.value as TimeMode)}>
              <option value="now">Now (last few seconds)</option>
              <option value="spread">Spread across this month</option>
              <option value="late">Late: previous month (accepted only within 72 h after month close)</option>
            </select>
          </Field>
          <Field label="Properties (JSON)" wide hint="Used by plan metric filters and unique_count aggregation">
            <input value={props} onChange={(e) => setProps(e.target.value)} />
          </Field>
        </div>
        {error && <div className="alert alert-bad compact">{error}</div>}
        <div className="btn-row">
          <Button
            variant="primary"
            icon={<Send size={16} />}
            loading={busy === 'send'}
            disabled={!customerId.trim() || !metric.trim()}
            onClick={() => {
              const ev = build();
              if (ev) void send(ev, false);
            }}
          >
            Send events
          </Button>
          <Button icon={<Repeat size={16} />} loading={busy === 'replay'} disabled={!last} onClick={() => last && send(last, true)}>
            Re-send same batch
          </Button>
        </div>
        {result && (
          <div className="stack-sm">
            <div className="stats compact-stats">
              <div className="stat">
                <div className="stat-label">Accepted</div>
                <div className="stat-value">{result.r.accepted}</div>
              </div>
              <div className="stat">
                <div className="stat-label">Duplicates ignored</div>
                <div className="stat-value">{result.r.duplicates}</div>
              </div>
              <div className="stat">
                <div className="stat-label">Late (next invoice)</div>
                <div className="stat-value">{result.r.lateAccepted}</div>
              </div>
              <div className="stat">
                <div className="stat-label">Rejected</div>
                <div className="stat-value">{result.r.rejected}</div>
              </div>
            </div>
            {result.replay && result.r.duplicates === result.r.received && <Alert tone="good">Idempotency works: every event in the re-sent batch was recognised as a duplicate and nothing was double-counted.</Alert>}
            {result.r.rejectionDetails.length > 0 && (
              <Alert tone="warn">
                {result.r.rejectionDetails[0].reason}
                {result.r.rejectionDetails.length > 1 && ` (+${result.r.rejectionDetails.length - 1} more)`}
              </Alert>
            )}
          </div>
        )}
        <details className="versions">
          <summary>
            <Copy size={12} /> Example request body
          </summary>
          <pre className="code">{JSON.stringify({ events: [{ customerId, metric, quantity: Number(quantity) || 1, timestamp: new Date().toISOString(), idempotencyKey: 'order-123-call-1', properties: parseJsonObject(props) || {} }] }, null, 2)}</pre>
        </details>
      </div>
    </Card>
  );
}

function AggregateCard({ customers }: { customers: string[] }) {
  const [customerId, setCustomerId] = useState('customer');
  const [metric, setMetric] = useState('api_calls');
  const [operation, setOperation] = useState('sum');
  const [distinct, setDistinct] = useState('');
  const [filters, setFilters] = useState('');
  const [from, setFrom] = useState(monthStartIso());
  const [to, setTo] = useState(() => new Date(Date.now() + 60_000).toISOString().slice(0, 16));
  const [result, setResult] = useState<Aggregation | null>(null);
  const { busy, run } = useAction();

  const query = async () => {
    if (parseJsonObject(filters) === null) return run('agg', () => Promise.reject(new Error('Filters must be a JSON object.')));
    const r = await run('agg', () =>
      get<Aggregation>(
        `/usage/aggregate${qs({
          customerId,
          metric,
          operation,
          distinctProperty: distinct,
          filters: filters.trim() || undefined,
          from: new Date(`${from}Z`).toISOString(),
          to: new Date(`${to}Z`).toISOString(),
        })}`,
      ),
    );
    if (r) setResult(r);
  };

  return (
    <Card>
      <CardHeader title="Query a billable metric" subtitle="sum · count · maximum · unique_count · latest_value, with property filters" />
      <div className="stack-sm">
        <div className="form-grid">
          <Field label="Customer ID">
            <CustomerInput value={customerId} onChange={setCustomerId} customers={customers} />
          </Field>
          <Field label="Metric">
            <input value={metric} onChange={(e) => setMetric(e.target.value)} />
          </Field>
          <Field label="Aggregation">
            <select value={operation} onChange={(e) => setOperation(e.target.value)}>
              {['sum', 'count', 'maximum', 'unique_count', 'latest_value'].map((o) => (
                <option key={o}>{o}</option>
              ))}
            </select>
          </Field>
          <Field label="Distinct property" hint="For unique_count, e.g. endpoint">
            <input value={distinct} onChange={(e) => setDistinct(e.target.value)} placeholder="optional" />
          </Field>
          <Field label="From (UTC)">
            <input type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} />
          </Field>
          <Field label="To (UTC)">
            <input type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} />
          </Field>
          <Field label="Property filters (JSON)" wide>
            <input value={filters} onChange={(e) => setFilters(e.target.value)} placeholder='{"region":"ap-south-1"}' />
          </Field>
        </div>
        <div>
          <Button icon={<Sigma size={16} />} loading={busy === 'agg'} onClick={query}>
            Run query
          </Button>
        </div>
        {result && (
          <div className="result-box">
            <div className="big-number">{qty(result.value)}</div>
            <div className="muted">
              {result.aggregation} of {result.metric} over {result.matchedEventCount.toLocaleString()} matching events
              {Object.keys(result.filters || {}).length > 0 && (
                <>
                  {' '}
                  · filters{' '}
                  {Object.entries(result.filters).map(([k, v]) => (
                    <Badge key={k}>
                      {k}={v}
                    </Badge>
                  ))}
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}

function SummaryCard({ customers }: { customers: string[] }) {
  const [customerId, setCustomerId] = useState('customer');
  const [summary, setSummary] = useState<UsageSummary | null>(null);
  const { busy, run } = useAction();
  const load = async () => {
    const r = await run('sum', () => get<UsageSummary>(`/usage/summary${qs({ customerId })}`));
    if (r) setSummary(r);
  };
  return (
    <Card>
      <CardHeader title={`Month-to-date usage · ${currentPeriod()}`} subtitle="Totals per metric for one customer" />
      <div className="inline-form">
        <Field label="Customer ID">
          <CustomerInput value={customerId} onChange={setCustomerId} customers={customers} />
        </Field>
        <Button loading={busy === 'sum'} onClick={load}>
          Load
        </Button>
      </div>
      {summary && (
        <div style={{ marginTop: 14 }}>
          {Object.keys(summary.usage).length ? (
            <Table className="compact">
              <thead>
                <tr>
                  <th>Metric</th>
                  <th className="num">Total</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(summary.usage).map(([k, v]) => (
                  <tr key={k}>
                    <td>{k}</td>
                    <td className="num">{qty(v)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          ) : (
            <Alert>No usage this month.</Alert>
          )}
          <div className="muted small" style={{ marginTop: 8 }}>
            {summary.eventCount.toLocaleString()} events · as of {dateTime(summary.asOf)}
          </div>
        </div>
      )}
    </Card>
  );
}
