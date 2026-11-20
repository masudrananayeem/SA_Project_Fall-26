import { Badge, Card, CardHeader, Drawer, Empty, ErrorNote, InvoiceTable, InvoiceView, METHOD_LABEL, PageHeader, Spinner, Stat, StatusBadge, Table, dateTime, get, invoiceNo, money, periodLabel, qs, toNumber, useAsync, type Invoice, type PaymentRecord, type UpgradeCharge } from '@meterflow/shared';
import { AccountStatusBanner, AmountDueCard, useAccountState } from '../status';
import { CheckCircle2, Clock, FileText } from 'lucide-react';

export function InvoicesPage({ customerId, selected, navigate }: { customerId: string; selected?: string; navigate: (to: string) => void }) {
  const invoices = useAsync(() => get<Invoice[]>(`/invoices${qs({ customerId })}`), [customerId]);
  const payments = useAsync(() => get<PaymentRecord[]>(`/payments${qs({ customerId })}`), [customerId]);
  const account = useAccountState(customerId);
  const upgrades = useAsync(() => get<UpgradeCharge[]>(`/billing/upgrade-charges${qs({ customerId })}`).catch(() => [] as UpgradeCharge[]), [customerId]);
  const refreshAll = async () => {
    await Promise.all([invoices.reload(), payments.reload(), account.reload(), upgrades.reload()]);
  };
  const list = invoices.data || [];
  const due = list.filter((i) => i.status === 'FINALISED');
  const dueNow = account.balance ? toNumber(account.balance.totalDueNow) : due.reduce((a, i) => a + toNumber(i.total), 0);
  const upgradesDue = (account.balance?.upgradeCharges || []).length;
  const paid = list.filter((i) => i.status === 'PAID');
  const sum = (xs: Invoice[]) => xs.reduce((a, i) => a + toNumber(i.total), 0);
  const currency = list[0]?.currency || 'BDT';

  return (
    <>
      <PageHeader title="Invoices" subtitle="Open an invoice to see every line, trace it to your usage events, download it or pay it." />
      <ErrorNote error={invoices.error} onRetry={invoices.reload} />
      <div className="stack">
        <AccountStatusBanner state={{ ...account, reload: refreshAll }} compact />
        <AmountDueCard state={{ ...account, reload: refreshAll }} />
        <div className="stats">
          <Stat label="Amount due" value={money(dueNow, currency)} hint={`${due.length} open invoice(s)${upgradesDue ? ` + ${upgradesDue} upgrade balance` : ''}`} icon={<Clock size={17} />} tone={dueNow > 0 ? 'warn' : undefined} />
          <Stat label="Paid to date" value={money(sum(paid), currency)} hint={`${paid.length} paid invoice(s)`} icon={<CheckCircle2 size={17} />} tone="good" />
          <Stat label="Invoices" value={list.length} icon={<FileText size={17} />} />
        </div>
        <Card className="flush">
          <div className="flush-body" style={{ paddingTop: 20 }}>
            {invoices.loading && !invoices.data ? <Spinner /> : <InvoiceTable invoices={list} showCustomer={false} onOpen={(i) => navigate(`invoices/${i.id}`)} empty="Your first invoice is created at the end of the billing period." />}
          </div>
        </Card>
        {!!upgrades.data?.length && (
          <Card className="flush">
            <CardHeader title="Plan upgrades this year" subtitle="When you upgrade in the middle of a paid month you only pay the difference for the days that are left" />
            <div className="flush-body">
              <Table className="compact cards-sm">
                <thead>
                  <tr>
                    <th>Month</th>
                    <th>Change</th>
                    <th className="num">Already paid</th>
                    <th className="num">Rest of month</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {upgrades.data.map((u) => (
                    <tr key={u.id} title={u.explanation || undefined}>
                      <td>{periodLabel(u.period)}</td>
                      <td>
                        {u.fromPlan} → <b>{u.toPlan}</b>
                        <div className="muted small">
                          {u.remainingDays} of {u.daysInPeriod} days · {money(u.subtotal, u.currency)} + VAT {money(u.tax, u.currency)}
                        </div>
                      </td>
                      <td className="num">{money(u.alreadyPaid, u.currency)}</td>
                      <td className="num">{money(u.total, u.currency)}</td>
                      <td>
                        <StatusBadge status={u.status === 'DUE' ? 'FINALISED' : u.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </div>
          </Card>
        )}
        <Card className="flush">
          <CardHeader title="Payment history" subtitle="Every payment and prepaid credit purchase on your account" />
          <div className="flush-body">
            {payments.data?.length ? (
              <Table className="compact cards-sm">
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
                      <td>
                        {p.kind === 'UPGRADE' ? (
                          <Badge tone="warn">Plan upgrade balance</Badge>
                        ) : p.invoiceId ? (
                          <span className="mono">{invoiceNo(p.invoiceId)}</span>
                        ) : (
                          <Badge tone="accent">Prepaid credit</Badge>
                        )}
                      </td>
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
