import { Activity, Ban, CheckCircle2, Clock, Layers, Tag } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { dateTime, qty, timeAgo, titleCase, toNumber } from './format';
import { chartPalette, useTheme } from './theme';
import type { UsageBreakdown } from './types';
import { Alert, Badge, Card, CardHeader, Empty, Stat, Table } from './ui';

/** Share bars for one event property (e.g. which endpoints / regions were used). */
function PropertyBars({ name, rows, total }: { name: string; rows: { value: string; events: number }[]; total: number }) {
  return (
    <div className="prop-block">
      <div className="prop-title">
        <Tag size={14} /> {titleCase(name)}
      </div>
      {rows.map((r) => {
        const pct = total ? (r.events * 100) / total : 0;
        return (
          <div key={r.value} className="prop-row">
            <span className="prop-value mono" title={r.value}>
              {r.value}
            </span>
            <span className="prop-bar">
              <span style={{ width: `${Math.max(pct, 1.5)}%` }} />
            </span>
            <span className="prop-num">
              {r.events.toLocaleString()} <span className="muted">({pct.toFixed(pct < 10 ? 1 : 0)}%)</span>
            </span>
          </div>
        );
      })}
    </div>
  );
}

/**
 * "What is this customer using?" — metrics, property breakdowns (endpoint, region, model…), daily
 * volume and the latest raw events. Shared by the customer portal and the admin customer view.
 */
export function UsageBreakdownView({ data, app, billedMetric, aggregation }: { data: UsageBreakdown; app: 'admin' | 'client'; billedMetric?: string; aggregation?: string }) {
  const pal = chartPalette(useTheme().theme, app);
  const props = Object.entries(data.properties);
  return (
    <div className="stack">
      {data.usageAccepted ? (
        <Alert tone="good">Usage is being accepted for this account.</Alert>
      ) : (
        <Alert tone="bad">
          <b>Usage is refused right now.</b> {data.usageBlockedReason}
        </Alert>
      )}
      <div className="stats">
        <Stat label="Events in period" value={data.totalEvents.toLocaleString()} icon={<Activity size={17} />} />
        <Stat label="Metrics used" value={data.metrics.length} icon={<Layers size={17} />} hint={data.metrics.map((m) => m.metric).join(', ') || '—'} />
        <Stat label="Last event" value={data.lastEventAt ? timeAgo(data.lastEventAt) : 'never'} icon={<Clock size={17} />} hint={dateTime(data.lastEventAt)} />
      </div>
      {!data.totalEvents ? (
        <Card>
          <Empty icon={<Activity size={22} />} title="No usage in this period">Events sent to the API appear here within seconds.</Empty>
        </Card>
      ) : (
        <>
          <div className="grid-2">
            <Card>
              <CardHeader title="Metrics" subtitle="What is being counted" />
              <Table className="compact">
                <thead>
                  <tr>
                    <th>Metric</th>
                    <th className="num">Events</th>
                    <th className="num">Quantity</th>
                  </tr>
                </thead>
                <tbody>
                  {data.metrics.map((m) => (
                    <tr key={m.metric}>
                      <td>
                        <span className="mono strong">{m.metric}</span>{' '}
                        {billedMetric && (m.metric === billedMetric ? <Badge tone="accent">billed · {aggregation || 'sum'}</Badge> : <Badge>not billed</Badge>)}
                      </td>
                      <td className="num">{m.events.toLocaleString()}</td>
                      <td className="num">{qty(m.quantity)}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </Card>
            <Card>
              <CardHeader title="Events per day" subtitle="UTC" />
              <div className="chart-box" style={{ height: 200 }}>
                <ResponsiveContainer>
                  <BarChart data={data.days.map((d) => ({ day: d.day.slice(5), events: d.events }))} margin={{ left: 0, right: 8, top: 8 }}>
                    <CartesianGrid vertical={false} stroke={pal.grid} />
                    <XAxis dataKey="day" tickLine={false} axisLine={false} fontSize={11} tick={{ fill: pal.axis }} />
                    <YAxis tickLine={false} axisLine={false} fontSize={11} width={48} allowDecimals={false} tick={{ fill: pal.axis }} />
                    <Tooltip cursor={{ fill: pal.cursor }} contentStyle={pal.tooltip} />
                    <Bar dataKey="events" fill={pal.accent} radius={[5, 5, 0, 0]} maxBarSize={34} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </Card>
          </div>
          {props.length > 0 && (
            <Card>
              <CardHeader title="What is being used" subtitle="Share of events by each property your requests carry (endpoint, region, model…)" />
              <div className="prop-grid">
                {props.map(([name, rows]) => (
                  <PropertyBars key={name} name={name} rows={rows} total={data.totalEvents} />
                ))}
              </div>
            </Card>
          )}
          <Card className="flush">
            <CardHeader title="Latest events" subtitle="The most recent raw usage events, exactly as received" />
            <div className="flush-body">
              <Table className="compact">
                <thead>
                  <tr>
                    <th>Time</th>
                    <th>Metric</th>
                    <th className="num">Qty</th>
                    <th>Properties</th>
                    <th className="hide-sm">Idempotency key</th>
                  </tr>
                </thead>
                <tbody>
                  {data.recent.map((e) => (
                    <tr key={e.id}>
                      <td className="muted">{dateTime(e.timestamp)}</td>
                      <td className="mono">{e.metric}</td>
                      <td className="num">{qty(e.quantity)}</td>
                      <td className="mono small props-cell">{e.properties && e.properties !== '{}' ? e.properties : '—'}</td>
                      <td className="mono small muted hide-sm">{e.idempotencyKey}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </div>
          </Card>
        </>
      )}
    </div>
  );
}

export function UsageStatusPill({ accepted }: { accepted: boolean }) {
  return accepted ? (
    <Badge tone="good">
      <CheckCircle2 size={12} /> Usage accepted
    </Badge>
  ) : (
    <Badge tone="bad">
      <Ban size={12} /> Usage refused
    </Badge>
  );
}

export const usageTotal = (b: UsageBreakdown | undefined) => (b ? b.metrics.reduce((a, m) => a + toNumber(m.quantity), 0) : 0);
