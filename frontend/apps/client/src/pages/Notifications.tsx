import { AlertTriangle, Bell, CheckCircle2, FileText, PiggyBank } from 'lucide-react';
import { Button, Card, Empty, ErrorNote, PageHeader, Spinner, dateTime, get, qs, useAsync, type CustomerNotification } from '@meterflow/shared';

function icon(type: string) {
  if (type.startsWith('invoice')) return <FileText size={15} />;
  if (type === 'payment.succeeded' || type === 'dunning.reactivated') return <CheckCircle2 size={15} />;
  if (type.startsWith('payment') || type.startsWith('dunning')) return <AlertTriangle size={15} />;
  if (type.startsWith('usage')) return <PiggyBank size={15} />;
  return <Bell size={15} />;
}

export function NotificationsPage({ customerId }: { customerId: string }) {
  const notes = useAsync(() => get<CustomerNotification[]>(`/notifications${qs({ customerId })}`), [customerId]);
  return (
    <>
      <PageHeader title="Notifications" subtitle="Invoices, payments, payment retries and budget alerts for your account (latest 50)." actions={<Button onClick={() => notes.reload()}>Refresh</Button>} />
      <ErrorNote error={notes.error} onRetry={notes.reload} />
      <Card>
        {notes.loading && !notes.data ? (
          <Spinner />
        ) : notes.data?.length ? (
          <div className="list">
            {notes.data.map((n) => (
              <div key={n.id} className="list-item">
                <span className="list-icon">{icon(n.eventType)}</span>
                <div style={{ flex: 1 }}>
                  <div>{n.message}</div>
                  <div className="muted small">
                    {n.eventType} · {dateTime(n.createdAt)}
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <Empty icon={<Bell size={22} />} title="No notifications yet" />
        )}
      </Card>
    </>
  );
}
