import { Badge, Card, CardHeader, Drawer, Empty, ErrorNote, InvoiceTable, InvoiceView, METHOD_LABEL, PageHeader, Spinner, Stat, StatusBadge, Table, dateTime, get, invoiceNo, money, qs, toNumber, useAsync, type Invoice, type PaymentRecord } from '@meterflow/shared';
import { AccountStatusBanner, useAccountState } from '../status';
import { CheckCircle2, Clock, FileText } from 'lucide-react';

export function InvoicesPage({ customerId, selected, navigate }: { customerId: string; selected?: string; navigate: (to: string) => void }) {
  const invoices = useAsync(() => get<Invoice[]>(`/invoices${qs({ customerId })}`), [customerId]);
  const payments = useAsync(() => get<PaymentRecord[]>(`/payments${qs({ customerId })}`), [customerId]);
  const account = useAccountState(customerId);
  const refreshAll = async () => {
    await Promise.all([invoices.reload(), payments.reload(), account.reload()]);
  };
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
        <AccountStatusBanner state={{ ...account, reload: refreshAll }} compact />
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
        <Card className="flush">
          <CardHeader title="Payment history" subtitle="Every payment and prepaid credit purchase on your account" />
          <div className="flush-body">
            {payments.data?.length ? (
              <Table className="compact">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>For</th>
                    <th>Method</th>
                    <th>Status</th>
                    <th className="num">Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {payments.data.map((p) => (
                    <tr key={p.id}>
                      <td className="muted">{dateTime(p.createdAt)}</td>
                      <td>{p.invoiceId ? <span className="mono">{invoiceNo(p.invoiceId)}</span> : <Badge tone="accent">Prepaid credit</Badge>}</td>
                      <td>{METHOD_LABEL[p.method] || p.method}</td>
                      <td>
                        <StatusBadge status={p.status} />
                      </td>
                      <td className={`num ${p.status === 'SUCCEEDED' ? 'amount-in' : 'amount-fail'}`}>{money(p.amount, p.currency)}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            ) : (
              <Empty title="No payments yet" />
            )}
          </div>
        </Card>
      </div>
      <Drawer open={!!selected} title="Invoice" subtitle={selected ? invoiceNo(selected) : undefined} onClose={() => navigate('invoices')}>
        {selected && <InvoiceView invoiceId={selected} onChanged={() => void refreshAll()} />}
      </Drawer>
    </>
  );
}
