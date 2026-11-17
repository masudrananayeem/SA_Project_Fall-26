import type { Num } from './types';

export const toNumber = (v: Num | null | undefined) => (v === null || v === undefined || v === '' ? 0 : Number(v));

export function money(v: Num | null | undefined, currency = 'BDT') {
  const n = toNumber(v);
  const formatted = n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return currency === 'USD' ? `$${formatted}` : `৳${formatted}`;
}

export function moneyCode(v: Num | null | undefined, currency = 'BDT') {
  return `${currency} ${toNumber(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function qty(v: Num | null | undefined) {
  const n = toNumber(v);
  return n.toLocaleString('en-US', { maximumFractionDigits: 4 });
}

export function date(v: string | null | undefined) {
  if (!v) return '—';
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function dateTime(v: string | null | undefined) {
  if (!v) return '—';
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function timeAgo(v: string | null | undefined) {
  if (!v) return '—';
  const diff = (Date.now() - new Date(v).getTime()) / 1000;
  if (diff < 60) return 'just now';
  if (diff < 3600) return `${Math.floor(diff / 60)} min ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} h ago`;
  return `${Math.floor(diff / 86400)} d ago`;
}

/** Billing period of "now" in UTC, as the backend uses (YYYY-MM). */
export function currentPeriod(offsetMonths = 0) {
  const d = new Date();
  const u = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + offsetMonths, 1));
  return `${u.getUTCFullYear()}-${String(u.getUTCMonth() + 1).padStart(2, '0')}`;
}

export function periodLabel(period: string) {
  const [y, m] = period.split('-').map(Number);
  if (!y || !m) return period;
  return new Date(Date.UTC(y, m - 1, 1)).toLocaleDateString('en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' });
}

/** YYYY-MM-DD in UTC, shifted by days. */
export function isoDay(offsetDays = 0) {
  const d = new Date(Date.now() + offsetDays * 86400000);
  return d.toISOString().slice(0, 10);
}

export function shortId(id: string | null | undefined) {
  return id ? id.slice(0, 8).toUpperCase() : '—';
}

export function invoiceNo(id: string) {
  return `INV-${shortId(id)}`;
}

export function percent(v: Num | null | undefined) {
  return `${toNumber(v).toLocaleString('en-US', { maximumFractionDigits: 2 })}%`;
}

export function titleCase(s: string | null | undefined) {
  if (!s) return '—';
  return s.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

export function downloadText(filename: string, text: string, type = 'text/plain') {
  const blob = new Blob([text], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function parseJsonObject(text: string): Record<string, string> | null {
  if (!text.trim()) return {};
  try {
    const v = JSON.parse(text);
    if (v && typeof v === 'object' && !Array.isArray(v)) return v as Record<string, string>;
  } catch {
    /* invalid */
  }
  return null;
}

export function uid(prefix = 'evt') {
  const r = typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `${prefix}-${r}`;
}
