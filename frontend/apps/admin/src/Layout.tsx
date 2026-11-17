import { useState, type ReactNode } from 'react';
import {
  Activity, AlertOctagon, Boxes, CalendarClock, ExternalLink, FileText, LayoutDashboard, LogOut, Menu, PlayCircle, Users, Wallet, Webhook,
} from 'lucide-react';
import type { Session } from '@meterflow/shared';

export const NAV = [
  { key: 'overview', label: 'Overview', icon: LayoutDashboard, group: 'Monitor' },
  { key: 'customers', label: 'Customers', icon: Users, group: 'Monitor' },
  { key: 'invoices', label: 'Invoices', icon: FileText, group: 'Monitor' },
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
        </header>
        <main className="admin-content">{children}</main>
      </div>
    </div>
  );
}
