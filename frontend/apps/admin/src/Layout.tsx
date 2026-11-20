import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  Activity, AlertOctagon, Bell, Boxes, CalendarClock, CircleDollarSign, CreditCard, ExternalLink, FileText, LayoutDashboard, LogOut, Menu, PlayCircle, Users, Wallet, Webhook, XCircle,
} from 'lucide-react';
import { ThemeToggle, get, money, timeAgo, useToast, type PaymentRecord, type Session } from '@meterflow/shared';

export const NAV = [
  { key: 'overview', label: 'Overview', icon: LayoutDashboard, group: 'Monitor' },
  { key: 'customers', label: 'Customers', icon: Users, group: 'Monitor' },
  { key: 'invoices', label: 'Invoices', icon: FileText, group: 'Monitor' },
  { key: 'payments', label: 'Payments', icon: CircleDollarSign, group: 'Monitor' },
  { key: 'plans', label: 'Pricing plans', icon: Boxes, group: 'Configure' },
  { key: 'subscriptions', label: 'Subscriptions', icon: CalendarClock, group: 'Configure' },
  { key: 'usage', label: 'Usage metering', icon: Activity, group: 'Operate' },
  { key: 'billing', label: 'Billing runs', icon: PlayCircle, group: 'Operate' },
  { key: 'credits', label: 'Credits', icon: Wallet, group: 'Operate' },
  { key: 'dunning', label: 'Dunning', icon: AlertOctagon, group: 'Operate' },
  { key: 'webhooks', label: 'Webhooks', icon: Webhook, group: 'Integrate' },
] as const;

const GROUPS = ['Monitor', 'Configure', 'Operate', 'Integrate'];

export function Layout({
  session, page, navigate, onLogout, clientUrl, children,
}: { session: Session; page: string; navigate: (to: string) => void; onLogout: () => void; clientUrl: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const current = NAV.find((n) => n.key === page);
  return (
    <div className="admin-shell">
      <aside className={`sidebar${open ? ' open' : ''}`}>
        <div className="sidebar-brand">
          <span className="logo-mark">M</span>
          <div>
            <b>meterflow</b>
            <small>Admin console</small>
          </div>
        </div>
        <nav>
          {GROUPS.map((g) => (
            <div key={g} className="nav-group">
              <div className="nav-group-label">{g}</div>
              {NAV.filter((n) => n.group === g).map((n) => (
                <button
                  key={n.key}
                  className={`nav-item${page === n.key ? ' active' : ''}`}
                  onClick={() => {
                    navigate(n.key);
                    setOpen(false);
                  }}
                >
                  <n.icon size={17} />
                  {n.label}
                </button>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-foot">
          <a className="nav-item" href={clientUrl} target="_blank" rel="noreferrer">
            <ExternalLink size={17} /> Customer portal
          </a>
          <div className="user-chip">
            <span className="avatar">{session.username.slice(0, 2).toUpperCase()}</span>
            <div>
              <b>{session.username}</b>
              <small>Billing admin</small>
            </div>
            <button className="icon-btn on-dark" title="Sign out" aria-label="Sign out" onClick={onLogout}>
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>
      {open && <div className="sidebar-backdrop" onClick={() => setOpen(false)} />}
      <div className="admin-main">
        <header className="topbar">
          <button className="icon-btn mobile-only" aria-label="Menu" onClick={() => setOpen(true)}>
            <Menu size={18} />
          </button>
          <div className="crumbs">
            <span>Admin</span>
            <span className="sep">/</span>
            <b>{current?.label || 'Overview'}</b>
          </div>
          <div className="spacer" />
          <span className="env-pill">
            <span className="dot live" /> API connected
          </span>
          <PaymentBell navigate={navigate} />
          <ThemeToggle />
        </header>
        <main className="admin-content">{children}</main>
      </div>
    </div>
  );
}

const SEEN_KEY = 'meterflow.admin.paymentsSeen';

/** Live payment notifications: polls the ledger, toasts new payments, counts unseen ones. */
function PaymentBell({ navigate }: { navigate: (to: string) => void }) {
  const toast = useToast();
  const [items, setItems] = useState<PaymentRecord[]>([]);
  const [open, setOpen] = useState(false);
  const [seen, setSeen] = useState(() => {
    try {
      return localStorage.getItem(SEEN_KEY) || new Date().toISOString();
    } catch {
      return new Date().toISOString();
    }
  });
  const known = useRef<Set<string> | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let alive = true;
    const poll = async () => {
      try {
        const list = await get<PaymentRecord[]>('/payments');
        if (!alive) return;
        if (known.current) {
          list
            .filter((p) => !known.current!.has(p.id))
            .slice(0, 3)
            .forEach((p) =>
              p.status === 'SUCCEEDED'
                ? toast.success(`${p.customerId} paid ${money(p.amount, p.currency)}${p.kind === 'TOP_UP' ? ' (prepaid credit)' : p.kind === 'UPGRADE' ? ' (plan upgrade balance)' : ''}`)
                : toast.error(`Payment failed: ${p.customerId} ${money(p.amount, p.currency)}`),
            );
        }
        known.current = new Set(list.map((p) => p.id));
        setItems(list.slice(0, 20));
      } catch {
        /* the page shows API errors; the bell stays quiet */
      }
    };
    void poll();
    const t = setInterval(poll, 15000);
    return () => {
      alive = false;
      clearInterval(t);
    };
  }, [toast]);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false);
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);

  const unseen = items.filter((p) => p.createdAt > seen).length;
  const markSeen = () => {
    const now = new Date().toISOString();
    setSeen(now);
    try {
      localStorage.setItem(SEEN_KEY, now);
    } catch {
      /* ignore */
    }
  };

  return (
    <div className="bell-wrap" ref={ref}>
      <button className="icon-btn" aria-label="Payment notifications" title="Payment notifications" onClick={() => setOpen((o) => !o)}>
        <Bell size={18} />
        {unseen > 0 && <span className="bell-count">{unseen > 9 ? '9+' : unseen}</span>}
      </button>
      {open && (
        <div className="bell-menu">
          <div className="bell-head">
            <b>Payments</b>
            {unseen > 0 && (
              <button className="link-btn" onClick={markSeen}>
                Mark all read
              </button>
            )}
          </div>
          <div className="bell-list">
            {items.slice(0, 8).map((p) => (
              <div key={p.id} className={`bell-item${p.createdAt > seen ? ' unread' : ''}`}>
                <span className="list-icon">{p.status === 'SUCCEEDED' ? <CreditCard size={14} /> : <XCircle size={14} />}</span>
                <div style={{ flex: 1 }}>
                  <div>
                    <b>{p.customerId}</b> {p.status === 'SUCCEEDED' ? 'paid' : 'payment failed'} {money(p.amount, p.currency)}
                    {p.kind === 'TOP_UP' && ' (credit)'}
                    {p.kind === 'UPGRADE' && ' (upgrade)'}
                  </div>
                  <div className="muted small">{timeAgo(p.createdAt)}</div>
                </div>
              </div>
            ))}
            {!items.length && <div className="muted small bell-empty">No payments yet.</div>}
          </div>
          <button
            className="bell-all"
            onClick={() => {
              setOpen(false);
              markSeen();
              navigate('payments');
            }}
          >
            Open payments
          </button>
        </div>
      )}
    </div>
  );
}
