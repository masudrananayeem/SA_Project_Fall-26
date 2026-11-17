import { money, toNumber, type ChargeDefinition, type Plan } from '@meterflow/shared';

export function chargesOf(p: Plan): ChargeDefinition[] {
  try {
    return p.chargeDefinitionsJson ? (JSON.parse(p.chargeDefinitionsJson) as ChargeDefinition[]) : [];
  } catch {
    return [];
  }
}

/** Latest active version of every plan key, cheapest first. */
export function catalogue(plans: Plan[] | undefined) {
  const latest = new Map<string, Plan>();
  (plans || []).filter((p) => p.active).forEach((p) => {
    const cur = latest.get(p.planKey);
    if (!cur || p.version > cur.version) latest.set(p.planKey, p);
  });
  return [...latest.values()].sort((a, b) => headlinePrice(a) - headlinePrice(b));
}

export function headlinePrice(p: Plan) {
  const cs = chargesOf(p);
  if (!cs.length) return toNumber(p.flatFee);
  return cs.filter((c) => c.type === 'flat_fee').reduce((a, c) => a + toNumber(c.amount), 0);
}

/** Human-readable price lines for a plan card. */
export function priceLines(p: Plan): string[] {
  const cs = chargesOf(p);
  const unit = p.metric.replace(/_/g, ' ');
  if (!cs.length) {
    const lines = [`${money(p.flatFee, p.currency)} platform fee per month`];
    if (toNumber(p.freeAllowance) > 0) lines.push(`${toNumber(p.freeAllowance).toLocaleString()} ${unit} included`);
    lines.push(`${money(p.unitPrice, p.currency)} per extra ${unit.replace(/s$/, '')}`);
    if (toNumber(p.minimumCommitment) > 0) lines.push(`Minimum ${money(p.minimumCommitment, p.currency)} per month`);
    return lines;
  }
  return cs.map((c) => {
    switch (c.type) {
      case 'flat_fee':
        return `${c.name}: ${money(c.amount, p.currency)} per month`;
      case 'per_unit':
        return `${c.name}: ${money(c.amount, p.currency)} per ${unit.replace(/s$/, '')}${toNumber(c.includedUnits) > 0 ? ` after ${toNumber(c.includedUnits).toLocaleString()} free` : ''}`;
      case 'per_seat':
        return `${c.name}: ${money(c.amount, p.currency)} per seat`;
      case 'package':
        return `${c.name}: ${money(c.amount, p.currency)} per ${toNumber(c.packageSize).toLocaleString()} ${unit}`;
      case 'volume_tier':
      case 'graduated_tier':
        return `${c.name}: ${(c.tiers || []).map((t) => `${t.upTo === null || t.upTo === undefined ? 'above' : `up to ${toNumber(t.upTo).toLocaleString()}`} @ ${toNumber(t.unitPrice)}`).join(', ')} (${c.type === 'volume_tier' ? 'volume' : 'graduated'})`;
      default:
        return `${c.name}: ${money(c.amount, p.currency)}`;
    }
  });
}
