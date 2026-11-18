import { useState } from 'react';
import { BookOpen, Check, Copy, Plug, Receipt } from 'lucide-react';
import {
  Badge, Button, Card, CardHeader, Code, ErrorNote, KeyValues, PageHeader, Spinner, Table, Tabs, UsageBreakdownView,
  apiBase, billingModeLabel, get, money, qs, toNumber, useAsync,
  type UsageBreakdown,
} from '@meterflow/shared';
import { priceLines } from '../plans';
import { useAccountState } from '../status';

function CopyButton({ text }: { text: string }) {
  const [done, setDone] = useState(false);
  return (
    <Button
      size="sm"
      variant="ghost"
      icon={done ? <Check size={14} /> : <Copy size={14} />}
      onClick={() => {
        void navigator.clipboard?.writeText(text).then(() => {
          setDone(true);
          setTimeout(() => setDone(false), 1500);
        });
      }}
    >
      {done ? 'Copied' : 'Copy'}
    </Button>
  );
}

function monthRange(offset: number) {
  const now = new Date();
  const from = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + offset, 1));
  const to = offset === 0 ? new Date(Date.now() + 60_000) : new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + offset + 1, 1));
  return { from: from.toISOString(), to: to.toISOString() };
}

/**
 * "How do I use the API, and what am I actually using?" — connection details with ready-to-run
 * examples, the exact billing rule of the customer's plan, and a breakdown of the usage received.
 */
export function ApiPage({ customerId }: { customerId: string }) {
  const account = useAccountState(customerId);
  const [range, setRange] = useState<'this' | 'last'>('this');
  const win = monthRange(range === 'this' ? 0 : -1);
  const breakdown = useAsync(() => get<UsageBreakdown>(`/usage/breakdown${qs({ customerId, from: win.from, to: win.to })}`), [customerId, range]);
  const [lang, setLang] = useState<'curl' | 'js' | 'python'>('curl');
  const plan = account.plan;
  const metric = plan?.metric || 'api_calls';
  const base = apiBase() || (typeof location !== 'undefined' ? location.origin : '');
  const endpoint = `${base}/api/usage/events:batch`;
  const example = {
    events: [
      {
        customerId,
        metric,
        quantity: 1,
        timestamp: '2026-11-19T10:15:00Z',
        idempotencyKey: 'req-7f3a91',
        properties: { endpoint: '/v1/chat', region: 'ap-south-1' },
      },
    ],
  };
  const body = JSON.stringify(example, null, 2);
  const snippets = {
    curl: `curl -X POST "${endpoint}" \\\n  -u ${customerId}:YOUR_PASSWORD \\\n  -H "Content-Type: application/json" \\\n  -d '${JSON.stringify(example)}'`,
    js: `await fetch("${endpoint}", {\n  method: "POST",\n  headers: {\n    "Content-Type": "application/json",\n    Authorization: "Basic " + btoa("${customerId}:" + process.env.METERFLOW_PASSWORD),\n  },\n  body: JSON.stringify(${body.replace(/\n/g, '\n  ')}),\n});`,
    python: `import os, requests\n\nrequests.post(\n    "${endpoint}",\n    auth=("${customerId}", os.environ["METERFLOW_PASSWORD"]),\n    json=${body.replace(/\n/g, '\n    ')},\n    timeout=10,\n)`,
  };
  const filters = plan?.metricFiltersJson && plan.metricFiltersJson !== '{}' ? plan.metricFiltersJson : null;

  return (
    <>
      <PageHeader
        title="API & usage"
        subtitle="Send usage events from your application, see exactly how they are billed, and check what your account is using."
      />
      <div className="stack">
        <div className="grid-2">
          <Card>
            <CardHeader title={<span className="row"><Plug size={18} /> Connect your application</span>} subtitle="One HTTPS call per batch of up to 1,000 events" />
            <div className="stack-sm">
              <div className="copy-row">
                <Badge tone="accent">POST</Badge>
                <code>{endpoint}</code>
                <CopyButton text={endpoint} />
              </div>
              <KeyValues
                items={[
                  ['Customer ID', <span className="mono">{customerId}</span>],
                  ['Authentication', 'HTTP Basic: your username and password'],
                  ['Metric to send', <span className="mono">{metric}</span>],
                  ['Duplicates', 'Same idempotencyKey = counted once'],
                ]}
              />
              <Tabs
                value={lang}
                onChange={setLang}
                tabs={[
                  { value: 'curl', label: 'cURL' },
                  { value: 'js', label: 'JavaScript' },
                  { value: 'python', label: 'Python' },
                ]}
              />
              <div style={{ position: 'relative' }}>
                <Code>{snippets[lang]}</Code>
                <div style={{ position: 'absolute', top: 6, right: 6 }}>
                  <CopyButton text={snippets[lang]} />
                </div>
              </div>
            </div>
          </Card>
          <Card>
            <CardHeader title={<span className="row"><BookOpen size={18} /> Event fields</span>} subtitle="What each field means" />
            <Table className="compact">
              <tbody>
                <tr>
                  <td className="mono strong">customerId</td>
                  <td>Always <code>{customerId}</code>. Other IDs are refused.</td>
                </tr>
                <tr>
                  <td className="mono strong">metric</td>
                  <td>
                    What you are counting. Your plan bills <code>{metric}</code>; other metrics are stored but not billed.
                  </td>
                </tr>
                <tr>
                  <td className="mono strong">quantity</td>
                  <td>How much was used by this event (1 call, 2.5 GB, 1,200 tokens…).</td>
                </tr>
                <tr>
                  <td className="mono strong">timestamp</td>
                  <td>When it happened (UTC, ISO-8601). Late events are accepted up to 72 hours after the month closes.</td>
                </tr>
                <tr>
                  <td className="mono strong">idempotencyKey</td>
                  <td>Unique per event, e.g. your request ID. Retries with the same key are never double-counted.</td>
                </tr>
                <tr>
                  <td className="mono strong">properties</td>
                  <td>Optional details (endpoint, region, model, user_id…). Used for filters and the breakdown below.</td>
                </tr>
              </tbody>
            </Table>
          </Card>
        </div>

        <Card>
          <CardHeader title={<span className="row"><Receipt size={18} /> How your usage is billed</span>} subtitle={plan ? `${plan.name} v${plan.version}` : 'No plan yet'} />
          {account.loading && !plan ? (
            <Spinner />
          ) : !plan ? (
            <p className="muted" style={{ margin: 0 }}>
              Choose a plan to see its billing rule.
            </p>
          ) : (
            <div className="grid-2">
              <KeyValues
                items={[
                  ['Billing', billingModeLabel(plan.billingMode)],
                  ['Billable metric', <span className="mono">{plan.metric}</span>],
                  ['Aggregation', `${plan.aggregation || 'sum'}${plan.aggregationProperty ? ` of ${plan.aggregationProperty}` : ''}`],
                  ['Counted events', filters ? <span className="mono small">only {filters}</span> : 'all events of the metric'],
                  ['Included free', `${toNumber(plan.freeAllowance).toLocaleString()} per month`],
                  ['VAT', `${toNumber(plan.taxRate)}%`],
                ]}
              />
              <div className="stack-sm">
                <b>In plain words</b>
                <ul className="plain-list">
                  <li>
                    Every event with metric <code>{plan.metric}</code> in a calendar month (UTC) is combined with <b>{plan.aggregation || 'sum'}</b>
                    {plan.aggregation === 'unique_count' && plan.aggregationProperty ? ` (distinct ${plan.aggregationProperty} values)` : ' of its quantity'}.
                  </li>
                  {priceLines(plan).map((l) => (
                    <li key={l}>{l}</li>
                  ))}
                  <li>
                    {plan.billingMode === 'PREPAID'
                      ? `You pay ${money(plan.flatFee, plan.currency)} + VAT at the start of each month; usage is accepted once it is paid.`
                      : 'Your invoice is created after the month ends. Each invoice line links back to the exact events behind it.'}
                  </li>
                </ul>
              </div>
            </div>
          )}
        </Card>

        <div className="row-between">
          <h3 className="section-title" style={{ fontSize: 17 }}>
            What your account is using
          </h3>
          <Tabs
            value={range}
            onChange={setRange}
            tabs={[
              { value: 'this', label: 'This month' },
              { value: 'last', label: 'Last month' },
            ]}
          />
        </div>
        {breakdown.error ? (
          <ErrorNote error={breakdown.error} onRetry={breakdown.reload} />
        ) : !breakdown.data ? (
          <Spinner label="Reading your usage…" />
        ) : (
          <UsageBreakdownView data={breakdown.data} app="client" billedMetric={plan?.metric} aggregation={plan?.aggregation || 'sum'} />
        )}
      </div>
    </>
  );
}
