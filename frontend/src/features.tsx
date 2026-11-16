import {useCallback, useEffect, useState} from 'react';
import {api, authorizationHeader} from './api';

const money = (v: any, c = 'BDT') =>
  new Intl.NumberFormat('en-BD', {style: 'currency', currency: c || 'BDT', maximumFractionDigits: 2}).format(Number(v || 0));
const thisMonth = () => new Date().toISOString().slice(0, 7);
const lastMonth = () => {
  const d = new Date();
  d.setUTCDate(1);
  d.setUTCMonth(d.getUTCMonth() - 1);
  return d.toISOString().slice(0, 7);
};
const API_BASE = import.meta.env.VITE_API_URL || '';

/** FR7: customer live-spend page. Refreshes every 30 seconds (the API cache TTL), well within 1 minute. */
export function LiveSpendPage({customer, notify}: {customer: string; notify: (m: string) => void}) {
  const [estimate, setEstimate] = useState<any>(null);
  const [budget, setBudget] = useState<any>(null);
  const [alerts, setAlerts] = useState<any[]>([]);
  const [amount, setAmount] = useState('1000');
  const [updated, setUpdated] = useState<Date | null>(null);
  const period = thisMonth();

  const refresh = useCallback(async () => {
    try {
      const [e, b, n] = await Promise.all([
        api<any>(`/usage/estimate?customerId=${encodeURIComponent(customer)}&period=${period}`),
        api<any>(`/budgets?customerId=${encodeURIComponent(customer)}&period=${period}`),
        api<any[]>(`/notifications?customerId=${encodeURIComponent(customer)}`),
      ]);
      setEstimate(e);
      setBudget(b);
      setAlerts(n);
      setUpdated(new Date());
    } catch (e: any) {
      notify(e.message);
    }
  }, [customer, period, notify]);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, 30_000);
    return () => clearInterval(timer);
  }, [refresh]);

  const saveBudget = async () => {
    try {
      await api('/budgets', {method: 'POST', body: JSON.stringify({customerId: customer, period, amount: Number(amount), currency: estimate?.currency || 'BDT'})});
      notify('Budget saved. Alerts fire at 50%, 80% and 100%.');
      refresh();
    } catch (e: any) {
      notify(e.message);
    }
  };

  const spent = Number(estimate?.estimatedAmount || 0);
  const limit = Number(budget?.amount || 0);
  const percent = limit > 0 ? Math.min(100, Math.round((spent * 100) / limit)) : 0;

  return (
    <div className="grid2">
      <section className="panel">
        <div className="panelhead">
          <div><h3>Live spend · {period}</h3><p>{customer} · plan {estimate?.plan || '—'} · updated {updated ? updated.toLocaleTimeString() : '—'} (auto every 30 s)</p></div>
          <button className="btn ghost" onClick={refresh}>Refresh</button>
        </div>
        <div className="settingrow"><span>Billable usage</span><b>{estimate?.billableQuantity ?? 0} {estimate?.billableMetric || ''} ({estimate?.eventCount ?? 0} events)</b></div>
        {(estimate?.lines || []).map((l: any, i: number) => (
          <div className="settingrow" key={i}><span title={l.rule}>{l.name}<br/><small className="hint">{l.rule}</small></span><b>{money(l.amount, estimate?.currency)}</b></div>
        ))}
        <div className="settingrow"><span>Tax ({estimate?.taxRate ?? 0}%)</span><b>{money(estimate?.estimatedTax, estimate?.currency)}</b></div>
        <div className="settingrow"><span><b>Estimated total</b></span><b>{money(spent, estimate?.currency)}</b></div>
        {limit > 0 && (
          <>
            <div className="settingrow"><span>Budget used</span><b>{percent}% of {money(limit, budget?.currency)}</b></div>
            <div style={{height: 8, background: '#e5e7eb', borderRadius: 4}}>
              <div style={{width: `${percent}%`, height: 8, borderRadius: 4, background: percent >= 100 ? '#dc2626' : percent >= 80 ? '#f59e0b' : '#16a34a'}}/>
            </div>
          </>
        )}
      </section>
      <section className="panel formpanel">
        <h3>Monthly budget</h3>
        <p>Get an alert at 50%, 80% and 100% of your budget for {period}.</p>
        <label>Budget amount ({estimate?.currency || 'BDT'})<input type="number" min="1" value={amount} onChange={e => setAmount(e.target.value)}/></label>
        <button className="btn primary" onClick={saveBudget}>Save budget</button>
        <hr/>
        <h3>Notifications</h3>
        {alerts.length === 0 && <p className="hint">No notifications yet.</p>}
        {alerts.slice(0, 10).map(a => (
          <div className="settingrow" key={a.id}><span><code>{a.eventType}</code><br/>{a.message}</span><small>{new Date(a.createdAt).toLocaleString()}</small></div>
        ))}
      </section>
    </div>
  );
}

/** QA5: invoice lines with one-call trace and rendered invoice; FR8 mock payment actions. */
export function InvoiceDetail({invoice, notify, onChanged}: {invoice: any; notify: (m: string) => void; onChanged: () => void}) {
  const [lines, setLines] = useState<any[]>([]);
  const [trace, setTrace] = useState<any>(null);
  const [rendered, setRendered] = useState('');

  useEffect(() => {
    setTrace(null);
    setRendered('');
    api<any[]>(`/invoices/${invoice.id}/lines`).then(setLines).catch(e => notify(e.message));
  }, [invoice.id, notify]);

  const render = async () => {
    try {
      const auth = authorizationHeader();
      const res = await fetch(`${API_BASE}/api/invoices/${invoice.id}/render`, {headers: auth ? {Authorization: auth} : {}});
      setRendered(await res.text());
    } catch (e: any) {
      notify(e.message);
    }
  };
  const act = async (path: string, message: string) => {
    try {
      await api(path, {method: 'POST'});
      notify(message);
      onChanged();
    } catch (e: any) {
      notify(e.message);
    }
  };

  return (
    <section className="panel tablepanel">
      <div className="panelhead">
        <div><h3>INV-{invoice.id.slice(0, 8).toUpperCase()} · {invoice.period}</h3><p>{invoice.status} · {invoice.taxRule || ''} · credit applied {money(invoice.creditApplied, invoice.currency)}</p></div>
        <div style={{display: 'flex', gap: 8}}>
          <button className="btn ghost" onClick={render}>Render</button>
          {invoice.status === 'FINALISED' && <button className="btn ghost" onClick={() => act(`/payments/mock/${invoice.id}/fail`, 'Payment failure recorded; dunning started.')}>Simulate failed payment</button>}
          {invoice.status === 'FINALISED' && <button className="btn primary" onClick={() => act(`/payments/mock/${invoice.id}/pay`, 'Payment recorded.')}>Pay</button>}
        </div>
      </div>
      <div className="tablewrap"><table>
        <thead><tr><th>KIND</th><th>LINE</th><th>QUANTITY</th><th>PRICE RULE</th><th>AMOUNT</th><th/></tr></thead>
        <tbody>
          {lines.map(l => (
            <tr key={l.id}>
              <td>{l.kind}</td><td>{l.lineName || l.metric}</td><td>{l.quantity}</td><td><small>{l.priceRule}</small></td>
              <td><b>{money(l.amount, invoice.currency)}</b></td>
              <td><button className="textbtn" onClick={() => api<any>(`/invoices/lines/${l.id}/trace`).then(setTrace).catch(e => notify(e.message))}>Trace</button></td>
            </tr>
          ))}
          <tr><td colSpan={4}>Subtotal · Tax · Total</td><td colSpan={2}><b>{money(invoice.subtotal, invoice.currency)} · {money(invoice.tax, invoice.currency)} · {money(invoice.total, invoice.currency)}</b></td></tr>
        </tbody>
      </table></div>
      {trace && (
        <div className="notice" onClick={() => setTrace(null)}>
          Trace: {trace.eventCount} events · billed quantity {String(trace.billedUsageQuantity)} · recomputed from events {String(trace.recomputedQuantity)} ·
          {trace.quantityMatches ? ' matches ✓' : ' MISMATCH'} · rule: {trace.priceRule} · tax: {trace.taxRule}<button>×</button>
        </div>
      )}
      {rendered && <pre style={{whiteSpace: 'pre-wrap', fontSize: 12}}>{rendered}</pre>}
    </section>
  );
}

/** FR10: what would last month's invoice have been under every version of the customer's plan? */
export function SimulatorPanel({customer, notify}: {customer: string; notify: (m: string) => void}) {
  const [period, setPeriod] = useState(lastMonth());
  const [result, setResult] = useState<any>(null);
  const run = async () => {
    try {
      setResult(await api<any>(`/invoices/simulate/versions?customerId=${encodeURIComponent(customer)}&period=${period}`));
    } catch (e: any) {
      notify(e.message);
    }
  };
  return (
    <section className="panel formpanel">
      <h3>Plan version simulator</h3>
      <p>Prices a past month's usage under every version of {customer}'s plan, without creating invoices.</p>
      <label>Period<input value={period} onChange={e => setPeriod(e.target.value)} placeholder="YYYY-MM"/></label>
      <button className="btn ghost" onClick={run}>Simulate</button>
      {(result?.simulations || []).map((s: any) => (
        <div className="settingrow" key={s.planId}>
          <span>{result.planKey} v{s.planVersion}{s.current ? ' (current)' : ''} · {s.status}{s.reason ? ` · ${s.reason}` : ''}</span>
          <b>{s.total !== undefined ? money(s.total, s.currency) : '—'}</b>
        </div>
      ))}
    </section>
  );
}

/** FR8: dunning cases with a time-travel button to demonstrate the day 1/3/7 schedule. */
export function DunningPage({notify}: {notify: (m: string) => void}) {
  const [cases, setCases] = useState<any[]>([]);
  const [asOf, setAsOf] = useState(() => new Date(Date.now() + 86_400_000).toISOString().slice(0, 16));
  const load = useCallback(() => api<any[]>('/dunning').then(setCases).catch(e => notify(e.message)), [notify]);
  useEffect(() => { load(); }, [load]);
  const process = async () => {
    try {
      const r = await api<any>(`/dunning/process?asOf=${encodeURIComponent(new Date(asOf).toISOString())}`, {method: 'POST'});
      notify(`Processed ${r.processed} due case(s): ${r.recovered} recovered, ${r.failedAgain} failed again.`);
      load();
    } catch (e: any) {
      notify(e.message);
    }
  };
  return (
    <section className="panel tablepanel">
      <div className="panelhead">
        <div><h3>Payment recovery</h3><p>Retries on day 1, 3 and 7 → past due → suspended; a successful payment reactivates immediately.</p></div>
        <div style={{display: 'flex', gap: 8, alignItems: 'center'}}>
          <input type="datetime-local" value={asOf} onChange={e => setAsOf(e.target.value)}/>
          <button className="btn primary" onClick={process}>Run retries as of</button>
        </div>
      </div>
      <div className="tablewrap"><table>
        <thead><tr><th>INVOICE</th><th>CUSTOMER</th><th>STATUS</th><th>ATTEMPTS</th><th>NEXT ATTEMPT</th></tr></thead>
        <tbody>
          {cases.map(c => <tr key={c.id}><td>INV-{c.invoiceId.slice(0, 8).toUpperCase()}</td><td>{c.customerId}</td><td><span className={`status ${c.status.toLowerCase()}`}>{c.status}</span></td><td>{c.attempt}</td><td>{c.nextAttemptAt ? new Date(c.nextAttemptAt).toLocaleString() : '—'}</td></tr>)}
          {!cases.length && <tr><td colSpan={5} className="empty">No dunning cases. Use "Simulate failed payment" on a finalised invoice.</td></tr>}
        </tbody>
      </table></div>
    </section>
  );
}
