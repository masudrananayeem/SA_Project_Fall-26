import { useState } from 'react';
import { KeyRound, Plus, RotateCw, Webhook as WebhookIcon } from 'lucide-react';
import {
  Badge, Button, Card, CardHeader, Empty, ErrorNote, Field, Modal, PageHeader, Spinner, StatusBadge, Table,
  dateTime, get, post, useAction, useAsync,
  type Webhook, type WebhookDelivery,
} from '@meterflow/shared';
import { CustomerInput, customerIds, useSubscriptions } from '../common';

function randomSecret() {
  const bytes = new Uint8Array(24);
  crypto.getRandomValues(bytes);
  return 'whsec_' + Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}

export function WebhooksPage() {
  const hooks = useAsync(() => get<Webhook[]>('/webhooks'), []);
  const subs = useSubscriptions();
  const [selected, setSelected] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  return (
    <>
      <PageHeader
        title="Webhooks"
        subtitle="Billing events (invoice finalised, payment succeeded/failed, dunning steps, budget alerts) are delivered to customer endpoints, signed with HMAC over “timestamp.body”."
        actions={
          <Button variant="primary" icon={<Plus size={16} />} onClick={() => setCreating(true)}>
            Add endpoint
          </Button>
        }
      />
      <ErrorNote error={hooks.error} onRetry={hooks.reload} />
      <div className="grid-3">
        <Card className="flush">
          <CardHeader title="Endpoints" />
          <div className="flush-body">
            {hooks.loading && !hooks.data ? (
              <Spinner />
            ) : hooks.data?.length ? (
              <Table>
                <thead>
                  <tr>
                    <th>Customer</th>
                    <th>Endpoint</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {hooks.data.map((h) => (
                    <tr key={h.id} className={`clickable${selected === h.id ? ' selected' : ''}`} onClick={() => setSelected(h.id)}>
                      <td className="strong">{h.customerId}</td>
                      <td className="mono small">{h.endpoint}</td>
                      <td>
                        <Badge tone={h.active ? 'good' : 'neutral'}>{h.active ? 'Active' : 'Disabled'}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            ) : (
              <Empty icon={<WebhookIcon size={22} />} title="No endpoints" action={<Button onClick={() => setCreating(true)}>Add endpoint</Button>}>
                Tip: a free URL from webhook.site lets you watch deliveries arrive.
              </Empty>
            )}
          </div>
        </Card>
        <Card className="flush">
          <CardHeader title="Deliveries" subtitle={selected ? 'Latest attempts for the selected endpoint' : 'Select an endpoint'} />
          <div className="flush-body">{selected ? <Deliveries webhookId={selected} /> : <Empty title="No endpoint selected" />}</div>
        </Card>
      </div>
      {creating && (
        <CreateWebhook
          customers={customerIds(subs.data)}
          onClose={() => setCreating(false)}
          onCreated={async (h) => {
            setCreating(false);
            await hooks.reload();
            setSelected(h.id);
          }}
        />
      )}
    </>
  );
}

function Deliveries({ webhookId }: { webhookId: string }) {
  const list = useAsync(() => get<WebhookDelivery[]>(`/webhooks/${webhookId}/deliveries`), [webhookId]);
  const { busy, run } = useAction();
  if (list.loading && !list.data) return <Spinner />;
  if (list.error) return <ErrorNote error={list.error} onRetry={list.reload} />;
  if (!list.data?.length) return <Empty title="No deliveries yet">Deliveries are created when billing events happen for this customer.</Empty>;
  return (
    <div className="stack-sm">
      <div>
        <Button size="sm" variant="ghost" onClick={() => list.reload()}>
          Refresh
        </Button>
      </div>
      {list.data.map((d) => (
        <div key={d.id} className="delivery">
          <div className="row-between">
            <div className="row">
              <Badge tone="accent">{d.eventType}</Badge>
              <StatusBadge status={d.status} />
              <span className="muted small">{d.attempts} attempt(s)</span>
            </div>
            <Button size="sm" icon={<RotateCw size={14} />} loading={busy === d.id} onClick={() => run(d.id, () => post<WebhookDelivery>(`/webhooks/deliveries/${d.id}/replay`), 'Delivery queued for replay.').then(() => list.reload())}>
              Replay
            </Button>
          </div>
          {d.lastResponse && <div className="muted small">Last response: {d.lastResponse}</div>}
          {d.nextAttemptAt && d.status !== 'DELIVERED' && <div className="muted small">Next attempt {dateTime(d.nextAttemptAt)}</div>}
          <details>
            <summary className="muted small">Payload</summary>
            <pre className="code">{(() => {
              try {
                return JSON.stringify(JSON.parse(d.payload), null, 2);
              } catch {
                return d.payload;
              }
            })()}</pre>
          </details>
        </div>
      ))}
    </div>
  );
}

function CreateWebhook({ customers, onClose, onCreated }: { customers: string[]; onClose: () => void; onCreated: (h: Webhook) => void }) {
  const [customerId, setCustomerId] = useState('customer');
  const [endpoint, setEndpoint] = useState('https://webhook.site/');
  const [secret, setSecret] = useState(randomSecret);
  const { busy, run } = useAction();
  const valid = /^https?:\/\/[^/\s]+/.test(endpoint) && secret.length >= 16 && customerId.trim();
  return (
    <Modal
      open
      title="Add webhook endpoint"
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="primary"
            disabled={!valid}
            loading={busy === 'create'}
            onClick={async () => {
              const h = await run('create', () => post<Webhook>('/webhooks', { customerId: customerId.trim(), endpoint: endpoint.trim(), secret }), 'Endpoint added.');
              if (h) onCreated(h);
            }}
          >
            Add endpoint
          </Button>
        </>
      }
    >
      <div className="stack-sm">
        <Field label="Customer ID">
          <CustomerInput value={customerId} onChange={setCustomerId} customers={customers} />
        </Field>
        <Field label="Endpoint URL" hint="Public http(s) URL. Private network hosts are blocked in the cloud profile.">
          <input value={endpoint} onChange={(e) => setEndpoint(e.target.value)} />
        </Field>
        <Field label="Signing secret" hint="At least 16 characters. Store it on the receiving side to verify signatures.">
          <div className="row" style={{ flexWrap: 'nowrap' }}>
            <input className="mono" value={secret} onChange={(e) => setSecret(e.target.value)} />
            <Button icon={<KeyRound size={16} />} onClick={() => setSecret(randomSecret())}>
              New
            </Button>
          </div>
        </Field>
      </div>
    </Modal>
  );
}
