import { useMemo, useState } from 'react';
import { ArrowRightLeft, Boxes, GitBranch, Plus, Trash2 } from 'lucide-react';
import {
  Alert, Badge, Button, Card, CardHeader, Empty, ErrorNote, Field, KeyValues, Modal, PageHeader, Spinner, Table,
  get, money, parseJsonObject, post, put, qs, toNumber, useAction, useAsync,
  type ChargeDefinition, type MigrationResult, type Plan, type Tier,
} from '@meterflow/shared';
import { usePlans } from '../common';

const AGGREGATIONS = ['sum', 'count', 'maximum', 'unique_count', 'latest_value'];
const TIERED = ['volume_tier', 'graduated_tier'];

function charges(p: Plan): ChargeDefinition[] {
  if (!p.chargeDefinitionsJson) return [];
  try {
    return JSON.parse(p.chargeDefinitionsJson) as ChargeDefinition[];
  } catch {
    return [];
  }
}

function filters(p: Plan): string {
  return p.metricFiltersJson ? p.metricFiltersJson : '';
}

export function PlansPage() {
  const plans = usePlans();
  const types = useAsync(() => get<{ types: string[] }>('/catalogue/plans/charge-types'), []);
  const [editing, setEditing] = useState<{ base?: Plan } | null>(null);
  const [migrating, setMigrating] = useState<string | null>(null);

  const groups = useMemo(() => {
    const map = new Map<string, Plan[]>();
    plans.data?.forEach((p) => map.set(p.planKey, [...(map.get(p.planKey) || []), p]));
    return [...map.entries()].map(([key, list]) => ({ key, versions: list.sort((a, b) => b.version - a.version) })).sort((a, b) => a.key.localeCompare(b.key));
  }, [plans.data]);

  return (
    <>
      <PageHeader
        title="Pricing plans"
        subtitle="Plans are immutable. Editing publishes a new version; existing subscriptions stay on their version until you migrate them."
        actions={
          <Button variant="primary" icon={<Plus size={16} />} onClick={() => setEditing({})}>
            New plan
          </Button>
        }
      />
      <ErrorNote error={plans.error} onRetry={plans.reload} />
      {types.data && (
        <div className="row" style={{ marginBottom: 18 }}>
          <span className="muted small">Charge types available (Strategy registry):</span>
          <div className="pill-list">
            {types.data.types.map((t) => (
              <Badge key={t} tone="accent">
                {t}
              </Badge>
            ))}
          </div>
        </div>
      )}
      {plans.loading && !plans.data ? (
        <Spinner />
      ) : !groups.length ? (
        <Card>
          <Empty icon={<Boxes size={22} />} title="No plans yet" action={<Button variant="primary" onClick={() => setEditing({})}>Create the first plan</Button>} />
        </Card>
      ) : (
        <div className="stack">
          {groups.map((g) => {
            const latest = g.versions[0];
            const cs = charges(latest);
            return (
              <Card key={g.key}>
                <CardHeader
                  title={
                    <span className="row">
                      {latest.name} <Badge tone="accent">v{latest.version}</Badge> <span className="mono muted small">{g.key}</span>
                    </span>
                  }
                  subtitle={`${g.versions.length} version${g.versions.length > 1 ? 's' : ''} · ${latest.currency} · metric ${latest.metric} (${latest.aggregation || 'sum'})`}
                  actions={
                    <>
                      <Button size="sm" icon={<GitBranch size={14} />} onClick={() => setEditing({ base: latest })}>
                        Publish new version
                      </Button>
                      {g.versions.length > 1 && (
                        <Button size="sm" icon={<ArrowRightLeft size={14} />} onClick={() => setMigrating(g.key)}>
                          Migrate subscriptions
                        </Button>
                      )}
                    </>
                  }
                />
                {cs.length ? (
                  <div className="stack-sm">
                    <span className="muted small">Charge components (Composite)</span>
                    <Table className="compact">
                      <thead>
                        <tr>
                          <th>Charge</th>
                          <th>Type</th>
                          <th className="num">Amount</th>
                          <th>Details</th>
                        </tr>
                      </thead>
                      <tbody>
                        {cs.map((c, i) => (
                          <tr key={i}>
                            <td className="strong">{c.name}</td>
                            <td>
                              <Badge>{c.type}</Badge>
                            </td>
                            <td className="num">{money(c.amount, latest.currency)}</td>
                            <td className="muted small">
                              {toNumber(c.includedUnits) > 0 && `${c.includedUnits} included · `}
                              {c.type === 'package' && `package of ${c.packageSize} · `}
                              {c.tiers?.length ? c.tiers.map((t) => `≤${t.upTo ?? '∞'} @ ${t.unitPrice}`).join(', ') : ''}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </Table>
                  </div>
                ) : (
                  <KeyValues
                    items={[
                      ['Flat fee', money(latest.flatFee, latest.currency)],
                      ['Unit price', `${money(latest.unitPrice, latest.currency)} / ${latest.metric}`],
                      ['Free allowance', toNumber(latest.freeAllowance).toLocaleString()],
                      ['Minimum commitment', money(latest.minimumCommitment, latest.currency)],
                      ['Tax', `${toNumber(latest.taxRate)}%`],
                    ]}
                  />
                )}
                {g.versions.length > 1 && (
                  <details className="versions">
                    <summary>Version history</summary>
                    <Table className="compact">
                      <thead>
                        <tr>
                          <th>Version</th>
                          <th>Name</th>
                          <th className="num">Flat fee</th>
                          <th className="num">Unit price</th>
                          <th className="num">Tax</th>
                          <th>Charges</th>
                          <th>ID</th>
                        </tr>
                      </thead>
                      <tbody>
                        {g.versions.map((v) => (
                          <tr key={v.id}>
                            <td>
                              <Badge tone={v === latest ? 'accent' : 'neutral'}>v{v.version}</Badge>
                            </td>
                            <td>{v.name}</td>
                            <td className="num">{money(v.flatFee, v.currency)}</td>
                            <td className="num">{toNumber(v.unitPrice)}</td>
                            <td className="num">{toNumber(v.taxRate)}%</td>
                            <td>{charges(v).length || '—'}</td>
                            <td className="mono small muted">{v.id.slice(0, 8)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </Table>
                  </details>
                )}
              </Card>
            );
          })}
        </div>
      )}
      {editing && (
        <PlanForm
          base={editing.base}
          types={types.data?.types || []}
          onClose={() => setEditing(null)}
          onSaved={async () => {
            setEditing(null);
            await plans.reload();
          }}
        />
      )}
      {migrating && <MigrateModal planKey={migrating} versions={groups.find((g) => g.key === migrating)?.versions || []} onClose={() => setMigrating(null)} />}
    </>
  );
}

interface FormState {
  planKey: string;
  name: string;
  currency: string;
  metric: string;
  flatFee: string;
  unitPrice: string;
  freeAllowance: string;
  minimumCommitment: string;
  taxRate: string;
  aggregation: string;
  aggregationProperty: string;
  metricFilters: string;
  useCharges: boolean;
  charges: ChargeDefinition[];
}

function PlanForm({ base, types, onClose, onSaved }: { base?: Plan; types: string[]; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<FormState>(() => ({
    planKey: base?.planKey || '',
    name: base?.name || '',
    currency: base?.currency || 'BDT',
    metric: base?.metric || 'api_calls',
    flatFee: String(base?.flatFee ?? '299'),
    unitPrice: String(base?.unitPrice ?? '0.05'),
    freeAllowance: String(base?.freeAllowance ?? '1000'),
    minimumCommitment: String(base?.minimumCommitment ?? '0'),
    taxRate: String(base?.taxRate ?? '15'),
    aggregation: base?.aggregation || 'sum',
    aggregationProperty: base?.aggregationProperty || '',
    metricFilters: base ? filters(base) : '',
    useCharges: base ? charges(base).length > 0 : false,
    charges: base ? charges(base) : [],
  }));
  const [error, setError] = useState<string | null>(null);
  const { busy, run } = useAction();
  const set = (k: keyof FormState, v: unknown) => setF((x) => ({ ...x, [k]: v }));
  const setCharge = (i: number, patch: Partial<ChargeDefinition>) => setF((x) => ({ ...x, charges: x.charges.map((c, j) => (j === i ? { ...c, ...patch } : c)) }));

  async function save() {
    setError(null);
    if (!f.planKey.trim() || !f.name.trim() || !f.metric.trim()) return setError('Plan key, name and metric are required.');
    const mf = parseJsonObject(f.metricFilters);
    if (mf === null) return setError('Metric filters must be a JSON object, e.g. {"region":"ap-south-1"}.');
    if (f.aggregation === 'unique_count' && !f.aggregationProperty.trim()) return setError('Unique count needs an aggregation property (for example user_id).');
    if (f.useCharges && !f.charges.length) return setError('Add at least one charge component or turn components off.');
    const body = {
      planKey: f.planKey.trim(),
      name: f.name.trim(),
      currency: f.currency,
      flatFee: Number(f.flatFee || 0),
      unitPrice: Number(f.unitPrice || 0),
      metric: f.metric.trim(),
      freeAllowance: Number(f.freeAllowance || 0),
      minimumCommitment: Number(f.minimumCommitment || 0),
      taxRate: Number(f.taxRate || 0),
      aggregation: f.aggregation,
      aggregationProperty: f.aggregationProperty.trim() || null,
      metricFilters: Object.keys(mf).length ? mf : null,
      charges: f.useCharges
        ? f.charges.map((c) => ({
            type: c.type,
            name: c.name,
            amount: Number(c.amount || 0),
            includedUnits: Number(c.includedUnits || 0),
            packageSize: Number(c.packageSize || 1),
            tiers: TIERED.includes(c.type) ? (c.tiers || []).map((t) => ({ upTo: t.upTo === '' || t.upTo === null ? null : Number(t.upTo), unitPrice: Number(t.unitPrice || 0) })) : [],
          }))
        : null,
    };
    const saved = await run(
      'save',
      () => (base ? put<Plan>(`/catalogue/plans/${encodeURIComponent(base.planKey)}`, body) : post<Plan>('/catalogue/plans', body)),
      (p) => `Published ${p.name} v${p.version}.`,
    );
    if (saved) onSaved();
  }

  return (
    <Modal
      open
      width={760}
      title={base ? `New version of ${base.planKey}` : 'New pricing plan'}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" loading={busy === 'save'} onClick={save}>
            {base ? `Publish v${base.version + 1}` : 'Publish plan'}
          </Button>
        </>
      }
    >
      <div className="stack">
        {base && <Alert>Subscriptions on v{base.version} keep their price. Use “Migrate subscriptions” to move them to the new version.</Alert>}
        <div className="form-grid">
          <Field label="Plan key" hint="Stable identifier, e.g. starter">
            <input value={f.planKey} disabled={!!base} onChange={(e) => set('planKey', e.target.value)} />
          </Field>
          <Field label="Display name">
            <input value={f.name} onChange={(e) => set('name', e.target.value)} />
          </Field>
          <Field label="Currency" hint={base ? 'Fixed for a plan key' : undefined}>
            <select value={f.currency} disabled={!!base} onChange={(e) => set('currency', e.target.value)}>
              <option>BDT</option>
              <option>USD</option>
            </select>
          </Field>
          <Field label="Billable metric">
            <input value={f.metric} onChange={(e) => set('metric', e.target.value)} />
          </Field>
          <Field label="Aggregation">
            <select value={f.aggregation} onChange={(e) => set('aggregation', e.target.value)}>
              {AGGREGATIONS.map((a) => (
                <option key={a}>{a}</option>
              ))}
            </select>
          </Field>
          <Field label="Aggregation property" hint="Event property, e.g. user_id or gb">
            <input value={f.aggregationProperty} onChange={(e) => set('aggregationProperty', e.target.value)} placeholder="optional" />
          </Field>
          <Field label="Metric filters (JSON)" hint='Only matching events count, e.g. {"region":"ap-south-1"}' wide>
            <input value={f.metricFilters} onChange={(e) => set('metricFilters', e.target.value)} placeholder="{}" />
          </Field>
          <Field label="Tax rate (%)">
            <input type="number" min="0" step="0.01" value={f.taxRate} onChange={(e) => set('taxRate', e.target.value)} />
          </Field>
        </div>

        <label className="check">
          <input type="checkbox" checked={f.useCharges} onChange={(e) => set('useCharges', e.target.checked)} />
          Use charge components (tiers, packages, seats…) instead of the simple flat fee + unit price
        </label>

        {!f.useCharges ? (
          <div className="form-grid">
            <Field label="Flat fee">
              <input type="number" min="0" step="0.01" value={f.flatFee} onChange={(e) => set('flatFee', e.target.value)} />
            </Field>
            <Field label="Unit price">
              <input type="number" min="0" step="0.0001" value={f.unitPrice} onChange={(e) => set('unitPrice', e.target.value)} />
            </Field>
            <Field label="Free allowance (units)">
              <input type="number" min="0" value={f.freeAllowance} onChange={(e) => set('freeAllowance', e.target.value)} />
            </Field>
            <Field label="Minimum commitment">
              <input type="number" min="0" step="0.01" value={f.minimumCommitment} onChange={(e) => set('minimumCommitment', e.target.value)} />
            </Field>
          </div>
        ) : (
          <div className="stack-sm">
            {f.charges.map((c, i) => (
              <div key={i} className="charge-box">
                <div className="row-between">
                  <b>Charge {i + 1}</b>
                  <Button size="sm" variant="ghost" icon={<Trash2 size={14} />} onClick={() => setF((x) => ({ ...x, charges: x.charges.filter((_, j) => j !== i) }))}>
                    Remove
                  </Button>
                </div>
                <div className="form-grid">
                  <Field label="Type">
                    <select value={c.type} onChange={(e) => setCharge(i, { type: e.target.value, tiers: TIERED.includes(e.target.value) ? c.tiers?.length ? c.tiers : [{ upTo: 1000, unitPrice: 0.05 }, { upTo: null, unitPrice: 0.03 }] : [] })}>
                      {(types.length ? types : ['flat_fee', 'per_unit', 'per_seat', 'package', 'volume_tier', 'graduated_tier']).map((t) => (
                        <option key={t}>{t}</option>
                      ))}
                    </select>
                  </Field>
                  <Field label="Name">
                    <input value={c.name} onChange={(e) => setCharge(i, { name: e.target.value })} />
                  </Field>
                  {!TIERED.includes(c.type) && (
                    <Field label={c.type === 'package' ? 'Price per package' : c.type === 'per_seat' ? 'Price per seat' : c.type === 'flat_fee' ? 'Amount' : 'Unit price'}>
                      <input type="number" min="0" step="0.0001" value={String(c.amount)} onChange={(e) => setCharge(i, { amount: e.target.value })} />
                    </Field>
                  )}
                  {['per_unit', 'package', 'volume_tier', 'graduated_tier'].includes(c.type) && (
                    <Field label="Included units">
                      <input type="number" min="0" value={String(c.includedUnits ?? 0)} onChange={(e) => setCharge(i, { includedUnits: e.target.value })} />
                    </Field>
                  )}
                  {c.type === 'package' && (
                    <Field label="Package size">
                      <input type="number" min="1" value={String(c.packageSize ?? 1)} onChange={(e) => setCharge(i, { packageSize: e.target.value })} />
                    </Field>
                  )}
                </div>
                {TIERED.includes(c.type) && (
                  <TierEditor tiers={c.tiers || []} onChange={(tiers) => setCharge(i, { tiers })} graduated={c.type === 'graduated_tier'} />
                )}
              </div>
            ))}
            <Button
              size="sm"
              icon={<Plus size={14} />}
              onClick={() => setF((x) => ({ ...x, charges: [...x.charges, { type: x.charges.length ? 'per_unit' : 'flat_fee', name: x.charges.length ? 'API calls' : 'Platform fee', amount: x.charges.length ? 0.05 : 299, includedUnits: 0, packageSize: 1, tiers: [] }] }))}
            >
              Add charge
            </Button>
          </div>
        )}
        {error && <div className="alert alert-bad compact">{error}</div>}
      </div>
    </Modal>
  );
}

function TierEditor({ tiers, onChange, graduated }: { tiers: Tier[]; onChange: (t: Tier[]) => void; graduated: boolean }) {
  return (
    <div className="stack-sm">
      <span className="muted small">{graduated ? 'Graduated: each tier prices only the units inside it.' : 'Volume: every unit is priced at the tier the total falls into.'} Leave the last “up to” empty for no limit.</span>
      {tiers.map((t, i) => (
        <div key={i} className="tier-row">
          <input type="number" placeholder="up to (∞)" value={t.upTo === null ? '' : String(t.upTo)} onChange={(e) => onChange(tiers.map((x, j) => (j === i ? { ...x, upTo: e.target.value === '' ? null : e.target.value } : x)))} />
          <input type="number" step="0.0001" placeholder="unit price" value={String(t.unitPrice)} onChange={(e) => onChange(tiers.map((x, j) => (j === i ? { ...x, unitPrice: e.target.value } : x)))} />
          <Button size="sm" variant="ghost" aria-label="Remove tier" icon={<Trash2 size={14} />} onClick={() => onChange(tiers.filter((_, j) => j !== i))} />
        </div>
      ))}
      <div>
        <Button size="sm" variant="ghost" icon={<Plus size={14} />} onClick={() => onChange([...tiers, { upTo: null, unitPrice: 0 }])}>
          Add tier
        </Button>
      </div>
    </div>
  );
}

function MigrateModal({ planKey, versions, onClose }: { planKey: string; versions: Plan[]; onClose: () => void }) {
  const [from, setFrom] = useState(String(versions[versions.length - 1]?.version ?? 1));
  const [to, setTo] = useState(String(versions[0]?.version ?? 2));
  const [result, setResult] = useState<MigrationResult | null>(null);
  const { busy, run } = useAction();
  const go = async (dryRun: boolean) => {
    const r = await run(
      dryRun ? 'dry' : 'apply',
      () => post<MigrationResult>(`/catalogue/plans/${encodeURIComponent(planKey)}/migrate-active${qs({ fromVersion: from, toVersion: to, dryRun })}`),
      dryRun ? undefined : (m) => `Migrated ${m.affectedSubscriptions} subscription(s) to v${m.toVersion}.`,
    );
    if (r) setResult(r);
  };
  return (
    <Modal
      open
      width={720}
      title={`Migrate ${planKey} subscriptions`}
      onClose={onClose}
      footer={
        <>
          <Button onClick={() => go(true)} loading={busy === 'dry'}>
            Preview impact
          </Button>
          <Button variant="primary" onClick={() => go(false)} loading={busy === 'apply'} disabled={!result || !result.dryRun || result.affectedSubscriptions === 0}>
            Migrate now
          </Button>
        </>
      }
    >
      <div className="stack">
        <p className="muted">Active subscriptions move from one version to another. A mid-month move adds a proration line to this month’s invoice.</p>
        <div className="form-grid">
          <Field label="From version">
            <select value={from} onChange={(e) => (setFrom(e.target.value), setResult(null))}>
              {versions.map((v) => (
                <option key={v.id} value={v.version}>
                  v{v.version} · {v.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="To version">
            <select value={to} onChange={(e) => (setTo(e.target.value), setResult(null))}>
              {versions.map((v) => (
                <option key={v.id} value={v.version}>
                  v{v.version} · {v.name}
                </option>
              ))}
            </select>
          </Field>
        </div>
        {result && (
          <>
            <Alert tone={result.dryRun ? 'info' : 'good'}>
              {result.dryRun ? 'Preview only — nothing changed yet.' : 'Migration applied.'} {result.affectedSubscriptions} subscription(s) affected.
            </Alert>
            {result.impact.length > 0 && (
              <Table className="compact">
                <thead>
                  <tr>
                    <th>Customer</th>
                    <th className="num">Old recurring</th>
                    <th className="num">New recurring</th>
                    <th className="num">Proration</th>
                  </tr>
                </thead>
                <tbody>
                  {result.impact.map((i) => (
                    <tr key={i.subscriptionId}>
                      <td>{i.customerId}</td>
                      <td className="num">{toNumber(i.oldRecurring).toFixed(2)}</td>
                      <td className="num">{toNumber(i.newRecurring).toFixed(2)}</td>
                      <td className="num">{toNumber(i.prorationDelta).toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            )}
          </>
        )}
      </div>
    </Modal>
  );
}
