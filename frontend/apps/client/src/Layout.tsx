import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Activity, Bell, FileText, Home, Layers, LogOut, Menu, PiggyBank, UserCircle, X } from 'lucide-react';
import { get, qs, timeAgo, useAsync, type CustomerNotification, type Session } from '@meterflow/shared';

export const NAV = [
  { key: 'home', label: 'Overview', icon: Home },
  { key: 'usage', label: 'Usage', icon: Activity },
  { key: 'invoices', label: 'Invoices', icon: FileText },
  { key: 'budget', label: 'Budget', icon: PiggyBank },
  { key: 'plans', label: 'Plans', icon: Layers },
  { key: 'account', label: 'Account', icon: UserCircle },
] as const;

const SEEN_KEY = 'meterflow.client.seenNotifications';

export function Layout({ session, customerId, page, navigate, onLogout, children }: { session: Session; customerId: string; page: string; navigate: (to: string) => void; onLogout: () => void; children: ReactNode }) {
  const [menu, setMenu] = useState(false);
  const [bell, setBell] = useState(false);
  const notes = useAsync(() => get<CustomerNotification[]>(`/notifications${qs({ customerId })}`), [customerId, page]);
  const [seen, setSeen] = useState(() => {
    try {
      return localStorage.getItem(SEEN_KEY) || '';
    } catch {
      return '';
    }
  });
  const unread = (notes.data || []).filter((n) => !seen || n.createdAt > seen).length;
  const bellRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!bell) return;
    const close = (e: MouseEvent) => bellRef.current && !bellRef.current.contains(e.target as Node) && setBell(false);
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [bell]);

  const markSeen = () => {
    const latest = notes.data?.[0]?.createdAt;
    if (!latest) return;
    setSeen(latest);
    try {
      localStorage.setItem(SEEN_KEY, latest);
    } catch {
      /* ignore */
    }
  };

  return (
    <div className="portal">
      <header className="portal-top">
        <div className="portal-bar">
          <button className="brand-btn" onClick={() => navigate('home')}>
            <span className="logo-mark">M</span>
            <span>
              <b>meterflow</b>
              <small>Billing portal</small>
            </span>
          </button>
          <nav className={`portal-nav${menu ? ' open' : ''}`}>
            {NAV.map((n) => (
              <button
                key={n.key}
                className={`portal-link${page === n.key ? ' active' : ''}`}
                onClick={() => {
                  navigate(n.key);
                  setMenu(false);
                }}
              >
                <n.icon size={16} />
                {n.label}
              </button>
            ))}
          </nav>
          <div className="portal-right">
            <div className="bell" ref={bellRef}>
              <button
                className="icon-btn"
                aria-label="Notifications"
                onClick={() => {
                  setBell((b) => !b);
                  if (!bell) void notes.reload();
                }}
              >
                <Bell size={18} />
                {unread > 0 && <span className="bell-count">{unread > 9 ? '9+' : unread}</span>}
              </button>
              {bell && (
                <div className="bell-menu">
                  <div className="bell-head">
                    <b>Notifications</b>
                    {unread > 0 && (
                      <button className="link-btn" onClick={markSeen}>
                        Mark all read
                      </button>
                    )}
                  </div>
                  <div className="bell-list">
                    {(notes.data || []).slice(0, 6).map((n) => (
                      <div key={n.id} className={`bell-item${!seen || n.createdAt > seen ? ' unread' : ''}`}>
                        <div>{n.message}</div>
                        <div className="muted small">{timeAgo(n.createdAt)}</div>
                      </div>
                    ))}
                    {!notes.data?.length && <div className="muted small bell-empty">You’re all caught up.</div>}
                  </div>
                  <button
                    className="bell-all"
                    onClick={() => {
                      setBell(false);
                      markSeen();
                      navigate('notifications');
                    }}
                  >
                    View all notifications
                  </button>
                </div>
              )}
            </div>
            <div className="account-chip" title={`Signed in as ${session.username}`}>
              <span className="avatar">{customerId.slice(0, 2).toUpperCase()}</span>
              <span className="account-name">{customerId}</span>
            </div>
            <button className="icon-btn" title="Sign out" aria-label="Sign out" onClick={onLogout}>
              <LogOut size={18} />
            </button>
            <button className="icon-btn mobile-only" aria-label="Menu" onClick={() => setMenu((m) => !m)}>
              {menu ? <X size={18} /> : <Menu size={18} />}
            </button>
          </div>
        </div>
      </header>
      <main className="portal-content">{children}</main>
      <footer className="portal-foot">MeterFlow customer portal · prices in your plan currency · amounts include VAT where shown</footer>
    </div>
  );
}
