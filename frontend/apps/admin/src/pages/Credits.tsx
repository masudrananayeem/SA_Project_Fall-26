import { useState } from 'react';
import { FileMinus, PlusCircle, Wallet } from 'lucide-react';
import {
  Button, Card, CardHeader, Empty, ErrorNote, Field, PageHeader, Spinner, Stat, Table,
  dateTime, get, money, post, qs, useAction, useAsync,
  type CreditAccount, type CreditNote,
} from '@meterflow/shared';
import { CustomerInput, customerIds, useSubscriptions } from '../common';

export function CreditsPage() {
  const subs = useSubscriptions();
  const [input, setInput] = useState('customer');
  const [customerId, setCustomerId] = useState('customer');
  const account = useAsync(() => get<CreditAccount>(`/credits${qs({ customerId })}`), [customerId]);
  const notes = useAsync(() => get<CreditNote[]>(`/credits/notes${qs({ customerId })}`), [customerId]);
  const [topUp, setTopUp] = useState('500');
  const [noteAmount, setNoteAmount] = useState('50');
  const [reason, setReason] = useState('Service disruption on 12th');
  const { busy, run } = useAction();
  const currency = account.data?.currency || 'BDT';
  const refresh = () => Promise.all([account.reload(), notes.reload()]);

  return (
    <>
      <PageHeader title="Credits" subtitle="Prepaid balances and credit notes. Credit is applied automatically to the customer's next invoice before payment." />
      <Card style={{ marginBottom: 20 }}>
        <div className="inline-form">
          <Field label="Customer ID">
            <CustomerInput value={input} onChange={setInput} customers={customerIds(subs.data)} />
          </Field>
          <Button variant="primary" onClick={() => setCustomerId(input.trim())} disabled={!input.trim()}>
            Load account
          </Button>
        </div>
      </Card>
      <ErrorNote error={account.error || notes.error} onRetry={refresh} />
      <div className="grid-3">
        <div className="stack">
          <div className="stats">
            <Stat label={`Balance · ${customerId}`} value={account.data ? money(account.data.balance, currency) : '…'} hint="Available for the next invoice" icon={<Wallet size={17} />} />
          </div>
          <Card className="flush">
            <CardHeader title="Credit notes" subtitle="Issued for disputes or service credits" />
            <div className="flush-body">
              {notes.loading && !notes.data ? (
                <Spinner />
              ) : notes.data?.length ? (
                <Table>
                  <thead>
                    <tr>
                      <th>Issued</th>
                      <th>Reason</th>
                      <th className="num">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {notes.data.map((n) => (
                      <tr key={n.id}>
                        <td className="muted">{dateTime(n.createdAt)}</td>
                        <td>{n.reason}</td>
                        <td className="num strong">{money(n.amount, n.currency)}</td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              ) : (
                <Empty icon={<FileMinus size={22} />} title="No credit notes" />
              )}
            </div>
          </Card>
        </div>
        <div className="stack">
          <Card>
            <CardHeader title="Top up prepaid credit" />
            <div className="inline-form">
              <Field label={`Amount (${currency})`}>
                <input type="number" min="0.01" step="0.01" value={topUp} onChange={(e) => setTopUp(e.target.value)} />
              </Field>
              <Button
                variant="primary"
                icon={<PlusCircle size={16} />}
                loading={busy === 'top'}
                disabled={!(Number(topUp) > 0)}
                onClick={() => run('top', () => post<CreditAccount>('/credits/top-up', { customerId, currency, amount: Number(topUp) }), (a) => `Balance is now ${money(a.balance, a.currency)}.`).then(refresh)}
              >
                Top up
              </Button>
            </div>
          </Card>
          <Card>
            <CardHeader title="Issue credit note" subtitle="Recorded for audit and added to the balance" />
            <div className="stack-sm">
              <Field label={`Amount (${currency})`}>
                <input type="number" min="0.01" step="0.01" value={noteAmount} onChange={(e) => setNoteAmount(e.target.value)} />
              </Field>
              <Field label="Reason">
                <input value={reason} onChange={(e) => setReason(e.target.value)} />
              </Field>
              <div>
                <Button
                  icon={<FileMinus size={16} />}
                  loading={busy === 'note'}
                  disabled={!(Number(noteAmount) > 0) || !reason.trim()}
                  onClick={() => run('note', () => post<CreditNote>('/credits/notes', { customerId, currency, amount: Number(noteAmount), reason: reason.trim() }), 'Credit note issued.').then(refresh)}
                >
                  Issue credit note
                </Button>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </>
  );
}
