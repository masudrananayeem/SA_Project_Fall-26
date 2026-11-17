import { Card, Drawer, ErrorNote, InvoiceTable, InvoiceView, PageHeader, Spinner, Stat, get, invoiceNo, money, qs, toNumber, useAsync, type Invoice } from '@meterflow/shared';
import { CheckCircle2, Clock, FileText } from 'lucide-react';

export function InvoicesPage({ customerId, selected, navigate }: { customerId: string; selected?: string; navigate: (to: string) => void }) {
  const invoices = useAsync(() => get<Invoice[]>(`/invoices${qs({ customerId })}`), [customerId]);
  const list = invoices.data || [];
  const due = list.filter((i) => i.status === 'FINALISED');
  const paid = list.filter((i) => i.status === 'PAID');
  const sum = (xs: Invoice[]) => xs.reduce((a, i) => a + toNumber(i.total), 0);
  const currency = list[0]?.currency || 'BDT';

  return (
    <>
      <PageHeader title="Invoices" subtitle="Open an invoice to see every line, trace it to your usage events, download it or pay it." />
      <ErrorNote error={invoices.error} onRetry={invoices.reload} />
      <div className="stack">
        <div className="stats">
          <Stat label="Amount due" value={money(sum(due), currency)} hint={`${due.length} open invoice(s)`} icon={<Clock size={17} />} tone={due.length ? 'warn' : undefined} />
          <Stat label="Paid to date" value={money(sum(paid), currency)} hint={`${paid.length} paid invoice(s)`} icon={<CheckCircle2 size={17} />} tone="good" />
          <Stat label="Invoices" value={list.length} icon={<FileText size={17} />} />
        </div>
        <Card className="flush">
          <div className="flush-body" style={{ paddingTop: 20 }}>
            {invoices.loading && !invoices.data ? <Spinner /> : <InvoiceTable invoices={list} showCustomer={false} onOpen={(i) => navigate(`invoices/${i.id}`)} empty="Your first invoice is created at the end of the billing period." />}
          </div>
        </Card>
      </div>
      <Drawer open={!!selected} title="Invoice" subtitle={selected ? invoiceNo(selected) : undefined} onClose={() => navigate('invoices')}>
        {selected && <InvoiceView invoiceId={selected} onChanged={invoices.reload} />}
      </Drawer>
    </>
  );
}
