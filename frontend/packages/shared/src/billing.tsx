import { useState } from 'react';
import { CreditCard, Download, FileText, GitBranch, Search, XCircle, Zap } from 'lucide-react';
import { apiText, get, post } from './api';
import { date, dateTime, downloadText, invoiceNo, money, periodLabel, qty, shortId, toNumber } from './format';
import type { Estimate, Invoice, InvoiceLine, LineTrace, PaymentResult } from './types';
import { Alert, Badge, Button, Card, CardHeader, Code, Empty, ErrorNote, KeyValues, Spinner, StatusBadge, Table, useAction, useAsync } from './ui';

export function InvoiceTable({ invoices, onOpen, showCustomer = true, empty }: { invoices: Invoice[]; onOpen?: (i: Invoice) => void; showCustomer?: boolean; empty?: string }) {
  if (!invoices.length) return <Empty icon={<FileText size={22} />} title="No invoices yet">{empty || 'Invoices appear here after a billing run.'}</Empty>;
  return (
    <Table>
      <thead>
        <tr>
          <th>Invoice</th>
          {showCustomer && <th>Customer</th>}
          <th>Period</th>
          <th>Status</th>
          <th className="num">Total</th>
          <th>Issued</th>
        </tr>
      </thead>
      <tbody>
        {invoices.map((i) => (
          <tr key={i.id} className={onOpen ? 'clickable' : undefined} onClick={() => onOpen?.(i)}>
            <td>
              <b className="mono">{invoiceNo(i.id)}</b>
            </td>
            {showCustomer && <td>{i.customerId}</td>}
            <td>{periodLabel(i.period)}</td>
            <td>
              <StatusBadge status={i.status} />
            </td>
            <td className="num strong">{money(i.total, i.currency)}</td>
            <td className="muted">{date(i.finalisedAt || i.createdAt)}</td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}

function LineTracePanel({ lineId }: { lineId: string }) {
  const trace = useAsync(() => get<LineTrace>(`/invoices/lines/${lineId}/trace`), [lineId]);
  if (trace.loading) return <Spinner label="Tracing line to its usage events…" />;
  if (trace.error) return <ErrorNote error={trace.error} />;
  const t = trace.data!;
  return (
    <div className="trace">
      <KeyValues
        items={[
          ['Price rule', <span className="mono small">{t.priceRule || '—'}</span>],
          ['Tax rule', t.taxRule || '—'],
          ['Aggregation', t.aggregation],
          ['Source events', `${t.eventsFound} of ${t.eventCount} found`],
          ['Billed quantity', t.billedUsageQuantity === null ? '—' : qty(t.billedUsageQuantity)],
          ['Recomputed from events', t.recomputedQuantity === null ? '—' : qty(t.recomputedQuantity)],
          [
            'Check',
            t.quantityMatches === null ? <Badge>Not applicable</Badge> : t.quantityMatches ? <Badge tone="good">Quantity matches events</Badge> : <Badge tone="bad">Mismatch</Badge>,
          ],
        ]}
      />
      {t.eventIds.length > 0 && (
        <details className="trace-ids">
          <summary>
            Event IDs ({t.eventIds.length}
            {t.eventIdsTruncated ? `, first ${t.eventIds.length} shown` : ''})
          </summary>
          <Code>{t.eventIds.join('\n')}</Code>
        </details>
      )}
    </div>
  );
}

export interface InvoiceViewProps {
  invoiceId: string;
  admin?: boolean;
  onChanged?: () => void;
}

/** Full invoice: summary, lines with per-line trace (QA5), downloads, and payment actions. */
export function InvoiceView({ invoiceId, admin, onChanged }: InvoiceViewProps) {
  const invoice = useAsync(() => get<Invoice>(`/invoices/${invoiceId}`), [invoiceId]);
  const lines = useAsync(() => get<InvoiceLine[]>(`/invoices/${invoiceId}/lines`), [invoiceId]);
  const [openTrace, setOpenTrace] = useState<string | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const { busy, run } = useAction();

  if (invoice.loading && !invoice.data) return <Spinner label="Loading invoice…" />;
  if (invoice.error) return <ErrorNote error={invoice.error} onRetry={invoice.reload} />;
  const inv = invoice.data!;
  const due = inv.status === 'FINALISED' && toNumber(inv.total) > 0;

  const refresh = async () => {
    await Promise.all([invoice.reload(), lines.reload()]);
    onChanged?.();
  };

  const download = (format: 'text' | 'csv') =>
    run(`dl-${format}`, async () => {
      const text = await apiText(`/invoices/${inv.id}/render?format=${format}`);
      downloadText(`${invoiceNo(inv.id)}.${format === 'csv' ? 'csv' : 'txt'}`, text, format === 'csv' ? 'text/csv' : 'text/plain');
    });

  const payResult = (r: PaymentResult) =>
    r.paymentStatus === 'SUCCEEDED' ? 'Payment received. Invoice is now paid.'
    : r.paymentStatus === 'ALREADY_PAID' ? 'This invoice was already paid.'
    : r.paymentStatus === 'NOTHING_DUE' ? 'Nothing is due on this invoice.'
    : `Payment ${r.paymentStatus.toLowerCase()}${r.reason ? `: ${r.reason}` : ''}`;

  return (
    <div className="stack">
      <div className="invoice-head">
        <div>
          <div className="mono muted small">{invoiceNo(inv.id)}</div>
          <div className="invoice-total">{money(inv.total, inv.currency)}</div>
          <div className="muted">
            {periodLabel(inv.period)} · {inv.customerId}
          </div>
        </div>
        <StatusBadge status={inv.status} />
      </div>

      <div className="btn-row">
        {due && (
          <Button variant="primary" icon={<CreditCard size={16} />} loading={busy === 'pay'} onClick={() => run('pay', () => post<PaymentResult>(`/payments/mock/${inv.id}/pay`), payResult).then(refresh)}>
            Pay {money(inv.total, inv.currency)}
          </Button>
        )}
        {admin && inv.status === 'DRAFT' && (
          <Button variant="primary" icon={<FileText size={16} />} loading={busy === 'fin'} onClick={() => run('fin', () => post<Invoice>(`/invoices/${inv.id}/finalise`), 'Invoice finalised').then(refresh)}>
            Finalise
          </Button>
        )}
        {admin && due && (
          <>
            <Button icon={<Zap size={16} />} loading={busy === 'collect'} onClick={() => run('collect', () => post<PaymentResult>(`/payments/mock/${inv.id}/collect`), payResult).then(refresh)}>
              Collect via provider
            </Button>
            <Button variant="danger" icon={<XCircle size={16} />} loading={busy === 'fail'} onClick={() => run('fail', () => post<PaymentResult>(`/payments/mock/${inv.id}/fail`), 'Payment failure recorded. Dunning started.').then(refresh)}>
              Simulate failed payment
            </Button>
          </>
        )}
        <Button icon={<FileText size={16} />} loading={busy === 'preview'} onClick={() => run('preview', async () => setPreview(await apiText(`/invoices/${inv.id}/render?format=text`)))}>
          Preview
        </Button>
        <Button icon={<Download size={16} />} loading={busy === 'dl-text'} onClick={() => download('text')}>
          Text
        </Button>
        <Button icon={<Download size={16} />} loading={busy === 'dl-csv'} onClick={() => download('csv')}>
          CSV
        </Button>
      </div>

      <KeyValues
        items={[
          ['Subtotal', money(inv.subtotal, inv.currency)],
          ['Tax', `${money(inv.tax, inv.currency)}${inv.taxRule ? ` · ${inv.taxRule}` : ''}`],
          ['Credit applied', toNumber(inv.creditApplied) > 0 ? `− ${money(inv.creditApplied, inv.currency)}` : '—'],
          ['Total', <b>{money(inv.total, inv.currency)}</b>],
          ['Billed quantity', inv.billedQuantity === null ? '—' : qty(inv.billedQuantity)],
          ['Seats', inv.seatCount ?? '—'],
          ['Finalised', dateTime(inv.finalisedAt)],
          ['Paid', dateTime(inv.paidAt)],
        ]}
      />

      {preview && (
        <Card>
          <CardHeader title="Rendered invoice" actions={<Button size="sm" variant="ghost" onClick={() => setPreview(null)}>Close</Button>} />
          <Code>{preview}</Code>
        </Card>
      )}

      <div>
        <h4 className="section-title">Line items</h4>
        <p className="muted small">Select “Trace” to see the usage events and price rule behind a line.</p>
        {lines.loading && !lines.data ? (
          <Spinner />
        ) : lines.error ? (
          <ErrorNote error={lines.error} onRetry={lines.reload} />
        ) : !lines.data?.length ? (
          <Alert>This invoice has no lines.</Alert>
        ) : (
          <div className="lines">
            {lines.data.map((l) => (
              <div key={l.id} className="line">
                <div className="line-main">
                  <div>
                    <div className="line-name">{l.lineName || l.metric || l.kind}</div>
                    <div className="muted small">
                      <Badge tone={l.kind === 'PRORATION' ? 'warn' : l.kind === 'LATE_USAGE' ? 'info' : 'neutral'}>{l.kind.replace(/_/g, ' ').toLowerCase()}</Badge>
                      {toNumber(l.quantity) !== 0 && <> · qty {qty(l.quantity)}</>}
                      {l.sourceEventCount > 0 && <> · {l.sourceEventCount} events</>}
                    </div>
                  </div>
                  <div className="line-amount">{money(l.amount, inv.currency)}</div>
                  <Button size="sm" variant="ghost" icon={<Search size={14} />} onClick={() => setOpenTrace(openTrace === l.id ? null : l.id)}>
                    {openTrace === l.id ? 'Hide' : 'Trace'}
                  </Button>
                </div>
                {openTrace === l.id && <LineTracePanel lineId={l.id} />}
              </div>
            ))}
          </div>
        )}
      </div>
      <div className="muted small">
        <GitBranch size={12} /> Plan version {shortId(inv.planId)} · created {dateTime(inv.createdAt)}
      </div>
    </div>
  );
}

/** Live spend breakdown: charge lines, subtotal, tax and estimated total. */
export function EstimateBreakdown({ estimate }: { estimate: Estimate }) {
  if (!estimate.lines) return <Alert>{estimate.note || 'No active subscription for this period.'}</Alert>;
  return (
    <div className="stack-sm">
      <Table className="compact">
        <thead>
          <tr>
            <th>Charge</th>
            <th className="num">Quantity</th>
            <th className="num">Amount</th>
          </tr>
        </thead>
        <tbody>
          {estimate.lines.map((l, i) => (
            <tr key={i}>
              <td>
                <div className="strong">{l.name}</div>
                <div className="muted small">{l.rule}</div>
              </td>
              <td className="num">{l.quantity === null || l.quantity === undefined ? '—' : qty(l.quantity)}</td>
              <td className="num">{money(l.amount, estimate.currency)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={2}>Subtotal</td>
            <td className="num">{money(estimate.estimatedSubtotal, estimate.currency)}</td>
          </tr>
          <tr>
            <td colSpan={2}>Tax ({toNumber(estimate.taxRate)}%)</td>
            <td className="num">{money(estimate.estimatedTax, estimate.currency)}</td>
          </tr>
          <tr className="total-row">
            <td colSpan={2}>Estimated total</td>
            <td className="num">{money(estimate.estimatedAmount, estimate.currency)}</td>
          </tr>
        </tfoot>
      </Table>
      {estimate.note && <Alert>{estimate.note}</Alert>}
    </div>
  );
}
