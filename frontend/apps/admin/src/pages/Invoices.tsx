import { useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { Button, Card, Drawer, ErrorNote, InvoiceTable, InvoiceView, PageHeader, Spinner, Tabs, get, invoiceNo, money, toNumber, useAsync, type Invoice } from '@meterflow/shared';
import type { Navigate } from '../common';

type StatusFilter = 'all' | 'DRAFT' | 'FINALISED' | 'PAID';

export function InvoicesPage({ selected, navigate }: { selected?: string; navigate: Navigate }) {
  const invoices = useAsync(() => get<Invoice[]>('/invoices'), []);
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<StatusFilter>('all');

  const rows = useMemo(
    () =>
      (invoices.data || []).filter(
        (i) => (status === 'all' || i.status === status) && (!q || i.customerId.toLowerCase().includes(q.toLowerCase()) || invoiceNo(i.id).toLowerCase().includes(q.toLowerCase()) || i.period.includes(q)),
      ),
    [invoices.data, q, status],
  );
  const total = rows.reduce((a, i) => a + toNumber(i.total), 0);

  return (
    <>
      <PageHeader title="Invoices" subtitle="Every invoice is explainable: open one to trace each line to its usage events and price rule." actions={<Button onClick={() => invoices.reload()}>Refresh</Button>} />
      <ErrorNote error={invoices.error} onRetry={invoices.reload} />
      <Card className="flush">
        <div className="toolbar">
          <div className="input-icon search">
            <Search size={16} />
            <input placeholder="Customer, invoice number or period" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <Tabs
            value={status}
            onChange={setStatus}
            tabs={[
              { value: 'all', label: 'All' },
              { value: 'DRAFT', label: 'Draft' },
              { value: 'FINALISED', label: 'Payment pending' },
              { value: 'PAID', label: 'Paid' },
            ]}
          />
          <span className="spacer" />
          <span className="muted small">
            {rows.length} invoices · {money(total)}
          </span>
        </div>
        <div className="flush-body">{invoices.loading && !invoices.data ? <Spinner /> : <InvoiceTable invoices={rows} onOpen={(i) => navigate(`invoices/${i.id}`)} />}</div>
      </Card>
      <Drawer open={!!selected} title="Invoice" subtitle={selected ? invoiceNo(selected) : undefined} onClose={() => navigate('invoices')}>
        {selected && <InvoiceView invoiceId={selected} admin onChanged={invoices.reload} />}
      </Drawer>
    </>
  );
}
