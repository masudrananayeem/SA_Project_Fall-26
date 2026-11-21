import { useState, type FormEvent, type ReactNode } from 'react';
import { ArrowRight, Eye, EyeOff, Lock, User } from 'lucide-react';
import { signIn, type Role, type Session } from './api';
import { Button } from './ui';
import { ThemeToggle } from './theme';

export interface LoginProps {
  /** Which interface this login opens. */
  expectedRole: Role;
  appTitle: string;
  appSubtitle: string;
  highlights: { icon: ReactNode; title: string; text: string }[];
  /** Where users with the other role should go. */
  otherApp: { label: string; url: string };
  /** Shown only for local demos; never ship real credentials in a public build. */
  demoAccount?: { username: string; password: string };
  onSignedIn: (s: Session) => void;
  /** Optional alternative form (e.g. sign-up) shown instead of the sign-in form. */
  alternate?: { prompt: string; action: string; render: (back: () => void) => ReactNode };
}

export function Login({ expectedRole, appTitle, appSubtitle, highlights, otherApp, demoAccount, onSignedIn, alternate }: LoginProps) {
  const [showAlt, setShowAlt] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [wrongRole, setWrongRole] = useState(false);
  /** Free hosting (Render) puts the API to sleep when idle; the first request can take up to a minute. */
  const [waking, setWaking] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setWrongRole(false);
    if (!username.trim() || !password) {
      setError('Enter your username and password.');
      return;
    }
    setBusy(true);
    const slow = window.setTimeout(() => setWaking(true), 5000);
    try {
      const session = await signIn(username.trim(), password);
      if (!session.roles.includes(expectedRole)) {
        setWrongRole(true);
        return;
      }
      onSignedIn(session);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      window.clearTimeout(slow);
      setWaking(false);
      setBusy(false);
    }
  }

  return (
    <div className="login">
      <ThemeToggle className="icon-btn floating" />
      <div className="login-brand">
        <div className="login-logo">
          <span className="logo-mark">M</span>
          <span className="logo-word">meterflow</span>
        </div>
        <div className="login-pitch">
          <div className="eyebrow light">{expectedRole === 'ADMIN' ? 'Billing operations' : 'Customer portal'}</div>
          <h1>{appTitle}</h1>
          <p>{appSubtitle}</p>
        </div>
        <ul className="login-highlights">
          {highlights.map((h) => (
            <li key={h.title}>
              <span className="hl-icon">{h.icon}</span>
              <div>
                <b>{h.title}</b>
                <span>{h.text}</span>
              </div>
            </li>
          ))}
        </ul>
        <div className="login-foot">MeterFlow · CSE 444 · usage-based billing engine</div>
      </div>

      <div className="login-panel">
        {showAlt && alternate ? (
          alternate.render(() => setShowAlt(false))
        ) : (
        <form className="login-form" onSubmit={submit} noValidate>
          <h2>Sign in</h2>
          <p className="muted">{expectedRole === 'ADMIN' ? 'Admin console access' : 'Access your billing account'}</p>

          <label className="field">
            <span className="field-label">Username</span>
            <div className="input-icon">
              <User size={16} />
              <input autoFocus autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} placeholder={demoAccount?.username || 'username'} />
            </div>
          </label>
          <label className="field">
            <span className="field-label">Password</span>
            <div className="input-icon">
              <Lock size={16} />
              <input type={show ? 'text' : 'password'} autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
              <button type="button" className="input-action" aria-label={show ? 'Hide password' : 'Show password'} onClick={() => setShow((s) => !s)}>
                {show ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </label>

          {error && <div className="alert alert-bad compact">{error}</div>}
          {waking && !error && (
            <div className="alert compact">The server is waking up after a quiet period. This can take up to a minute on the first sign-in, please wait…</div>
          )}
          {wrongRole && (
            <div className="alert alert-warn compact">
              <div>
                These credentials belong to a {expectedRole === 'ADMIN' ? 'customer' : 'billing admin'} account.{' '}
                <a href={otherApp.url}>Open the {otherApp.label} →</a>
              </div>
            </div>
          )}

          <Button type="submit" variant="primary" className="btn-block" loading={busy} icon={!busy && <ArrowRight size={16} />}>
            Sign in
          </Button>

          {demoAccount && (
            <button
              type="button"
              className="demo-fill"
              onClick={() => {
                setUsername(demoAccount.username);
                setPassword(demoAccount.password);
                setError(null);
                setWrongRole(false);
              }}
            >
              Use local demo account <code>{demoAccount.username}</code>
            </button>
          )}

          {alternate && (
            <div className="login-alt">
              {alternate.prompt}{' '}
              <button type="button" className="link-btn" onClick={() => setShowAlt(true)}>
                {alternate.action}
              </button>
            </div>
          )}

          <div className="login-switch">
            Looking for the {otherApp.label}? <a href={otherApp.url}>Go there</a>
          </div>
        </form>
        )}
      </div>
    </div>
  );
}
