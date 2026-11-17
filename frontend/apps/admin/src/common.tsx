import { useId } from 'react';
import { get, useAsync, type Plan, type Subscription } from '@meterflow/shared';

export type Navigate = (to: string) => void;

export function usePlans() {
  return useAsync(() => get<Plan[]>('/catalogue/plans'), []);
}

export function useSubscriptions() {
  return useAsync(() => get<Subscription[]>('/subscriptions'), []);
}

/** Distinct customer IDs known from subscriptions, plus the demo customer. */
export function customerIds(subs: Subscription[] | undefined) {
  const set = new Set<string>(['customer']);
  subs?.forEach((s) => set.add(s.customerId));
  return [...set].sort();
}

export function planLabel(plans: Plan[] | undefined, planId: string | null | undefined) {
  if (!planId) return '—';
  const p = plans?.find((x) => x.id === planId);
  return p ? `${p.name} · v${p.version}` : planId.slice(0, 8);
}

/** Latest version of every plan key. */
export function latestPlans(plans: Plan[] | undefined) {
  const map = new Map<string, Plan>();
  plans?.forEach((p) => {
    const cur = map.get(p.planKey);
    if (!cur || p.version > cur.version) map.set(p.planKey, p);
  });
  return [...map.values()].sort((a, b) => a.planKey.localeCompare(b.planKey));
}

export function PlanSelect({ plans, value, onChange, allowEmpty, emptyLabel = 'Subscription’s own plan' }: { plans: Plan[] | undefined; value: string; onChange: (v: string) => void; allowEmpty?: boolean; emptyLabel?: string }) {
  return (
    <select value={value} onChange={(e) => onChange(e.target.value)}>
      {allowEmpty && <option value="">{emptyLabel}</option>}
      {!allowEmpty && !value && <option value="">Select a plan…</option>}
      {(plans || [])
        .slice()
        .sort((a, b) => a.planKey.localeCompare(b.planKey) || b.version - a.version)
        .map((p) => (
          <option key={p.id} value={p.id}>
            {p.name} — {p.planKey} v{p.version} ({p.currency})
          </option>
        ))}
    </select>
  );
}

/** Free-text customer ID with suggestions from known subscriptions. */
export function CustomerInput({ value, onChange, customers, placeholder = 'customer' }: { value: string; onChange: (v: string) => void; customers: string[]; placeholder?: string }) {
  const id = useId();
  return (
    <>
      <input list={id} value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
      <datalist id={id}>
        {customers.map((c) => (
          <option key={c} value={c} />
        ))}
      </datalist>
    </>
  );
}
