import { useState } from 'react';
import { Activity, Send, Sigma } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import {
  Alert, Button, Card, CardHeader, Empty, ErrorNote, Field, PageHeader, Spinner, Stat, Table,
  chartPalette, dateTime, get, parseJsonObject, post, qs, qty, toNumber, uid, useAction, useAsync, useTheme,
  type Aggregation, type Estimate, type IngestResult, type UsageSummary,
} from '@meterflow/shared';

const DAYS = 14;

export function UsagePage({ customerId }: { customerId: string }) {
  const pal = chartPalette(useTheme().theme, 'client');
  const summary = useAsync(() => get<UsageSummary>(`/usage/summary${qs({ customerId })}`), [customerId]);
  const estimate = useAsync(() => get<Estimate>(`/usage/estimate${qs({ customerId })}`), [customerId]);
  const metric = estimate.data?.billableMetric || 'api_calls';
  const daily = useAsync(async () => {
    const today = new Date();
    const base = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate());
    const days = Array.from({ length: DAYS }, (_, i) => base - (DAYS - 1 - i) * 86400000);
    const values = await Promise.all(
      days.map((d) =>
        get<Aggregation>(`/usage/aggregate${qs({ customerId, metric, operation: 'sum', from: new Date(d).toISOString(), to: new Date(d + 86400000).toISOString() })}`).then((a) => toNumber(a.value)),
      ),
    );
    return days.map((d, i) => ({ day: new Date(d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', timeZone: 'UTC' }), value: values[i] }));
  }, [customerId, metric]);

  return (
    <>
      <PageHeader title="Usage" subtitle="Everything your account sent this month. Billing uses exactly these events." actions={<Button onClick={() => (summary.reload(), daily.reload(), estimate.reload())}>Refresh</Button>} />
      <ErrorNote error={summary.error || daily.error} onRetry={() => (summary.reload(), daily.reload())} />
      <div className="stack">
        <div className="stats">
          <Stat label="Events this month" value={summary.data ? summary.data.eventCount.toLocaleString() : '…'} icon={<Activity size={17} />} hint={summary.data ? `as of ${dateTime(summary.data.asOf)}` : undefined} />
          {summary.data &&
            Object.entries(summary.data.usage).map(([k, v]) => <Stat key={k} label={`Total ${k}`} value={qty(v)} hint={k === metric ? `Billable metric · ${estimate.data?.aggregation || 'sum'}` : 'Not billed on your plan'} />)}
          {estimate.data?.billableQuantity !== undefined && <Stat label="Billable quantity" value={qty(estimate.data.billableQuantity)} hint="After your plan’s aggregation and filters" />}
        </div>
        <Card>
          <CardHeader title={`Daily ${metric}`} subtitle={`Last ${DAYS} days (UTC)`} />
          {daily.loading && !daily.data ? (
            <Spinner />
          ) : daily.data?.some((d) => d.value > 0) ? (
            <div className="chart-box">
              <ResponsiveContainer>
                <BarChart data={daily.data} margin={{ left: 0, right: 8, top: 8 }}>
                  <CartesianGrid vertical={false} stroke={pal.grid} />
                  <XAxis dataKey="day" tickLine={false} axisLine={false} fontSize={12} interval="preserveStartEnd" tick={{ fill: pal.axis }} />
                  <YAxis tickLine={false} axisLine={false} fontSize={12} width={60} allowDecimals={false} tick={{ fill: pal.axis }} />
                  <Tooltip formatter={(v) => [qty(Number(v)), metric]} cursor={{ fill: pal.cursor }} contentStyle={pal.tooltip} />
                  <Bar dataKey="value" fill={pal.accent} radius={[6, 6, 0, 0]} maxBarSize={40} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <Empty icon={<Activity size={22} />} title="No usage in the last two weeks" />
          )}
        </Card>
        <div className="grid-2">
          <QueryCard customerId={customerId} metric={metric} />
          <TestEventsCard customerId={customerId} metric={metric} onSent={() => (summary.reload(), daily.reload(), estimate.reload())} />
        </div>
      </div>
    </>
  );
}

function QueryCard({ customerId, metric }: { customerId: string; metric: string }) {
  const [operation, setOperation] = useState('count');
  const [distinct, setDistinct] = useState('endpoint');
  const [filters, setFilters] = useState('');
  const [result, setResult] = useState<Aggregation | null>(null);
  const { busy, run } = useAction();
  const monthStart = new Date(Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth(), 1)).toISOString();
  return (
    <Card>
      <CardHeader title="Explore your usage" subtitle="Month to date, any aggregation, optional property filter" />
      <div className="form-grid">
        <Field label="Aggregation">
          <select value={operation} onChange={(e) => setOperation(e.target.value)}>
            {['sum', 'count', 'maximum', 'unique_count', 'latest_value'].map((o) => (
              <option key={o}>{o}</option>
            ))}
          </select>
        </Field>
        <Field label="Distinct property" hint="Used by unique_count">
          <input value={distinct} onChange={(e) => setDistinct(e.target.value)} />
        </Field>
        <Field label="Filter (JSON)" wide>
          <input value={filters} onChange={(e) => setFilters(e.target.value)} placeholder='{"region":"ap-south-1"}' />
        </Field>
      </div>
      <div className="btn-row" style={{ marginTop: 12 }}>
        <Button
          icon={<Sigma size={16} />}
          loading={busy === 'q'}
          onClick={async () => {
            if (parseJsonObject(filters) === null) return run('q', () => Promise.reject(new Error('Filter must be a JSON object.')));
            const r = await run('q', () =>
              get<Aggregation>(`/usage/aggregate${qs({ customerId, metric, operation, distinctProperty: distinct, filters: filters.trim() || undefined, from: monthStart, to: new Date(Date.now() + 60000).toISOString() })}`),
            );
            if (r) setResult(r);
          }}
        >
          Run
        </Button>
      </div>
      {result && (
        <div className="result-box" style={{ marginTop: 14 }}>
          <div className="big-number">{qty(result.value)}</div>
          <div className="muted">
            {result.aggregation} of {result.metric} · {result.matchedEventCount.toLocaleString()} matching events
          </div>
        </div>
      )}
    </Card>
  );
}

function TestEventsCard({ customerId, metric, onSent }: { customerId: string; metric: string; onSent: () => void }) {
  const [count, setCount] = useState('25');
  const [result, setResult] = useState<IngestResult | null>(null);
  const { busy, run } = useAction();
  return (
    <Card>
      <CardHeader title="Send test usage" subtitle="Your own systems normally send events through the API. Use this to see live spend move." />
      <div className="inline-form">
        <Field label="Events">
          <input type="number" min="1" max="1000" value={count} onChange={(e) => setCount(e.target.value)} />
        </Field>
        <Button
          variant="primary"
          icon={<Send size={16} />}
          loading={busy === 'send'}
          onClick={async () => {
            const n = Math.min(Math.max(Number(count) || 1, 1), 1000);
            const now = Date.now();
            const events = Array.from({ length: n }, (_, i) => ({
              customerId,
              metric,
              quantity: 1,
              timestamp: new Date(now - i * 1000).toISOString(),
              idempotencyKey: uid('portal'),
              properties: { region: 'ap-south-1', endpoint: ['/v1/chat', '/v1/embeddings', '/v1/images'][i % 3] },
            }));
            const r = await run('send', () => post<IngestResult>('/usage/events:batch', { events }), (x) => (x.accepted ? `${x.accepted} events recorded.` : 'No events were accepted.'));
            if (r) {
              setResult(r);
              onSent();
            }
          }}
        >
          Send
        </Button>
      </div>
      {result && result.rejectionDetails.length > 0 && (
        <div style={{ marginTop: 14 }}>
          <Alert tone="bad">{result.rejectionDetails[0].reason}</Alert>
        </div>
      )}
      {result && (
        <div style={{ marginTop: 14 }}>
          <Table className="compact">
            <tbody>
              <tr>
                <td>Accepted</td>
                <td className="num strong">{result.accepted}</td>
              </tr>
              <tr>
                <td>Duplicates ignored</td>
                <td className="num">{result.duplicates}</td>
              </tr>
              <tr>
                <td>Rejected</td>
                <td className="num">{result.rejected}</td>
              </tr>
            </tbody>
          </Table>
        </div>
      )}
      <div style={{ marginTop: 12 }}>
        <Alert>Live spend is cached for up to 30 seconds, so the estimate catches up shortly after you send events.</Alert>
      </div>
    </Card>
  );
}
