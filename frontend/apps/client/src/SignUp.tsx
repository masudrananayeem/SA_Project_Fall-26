import { useState, type FormEvent } from 'react';
import { ArrowLeft, UserPlus } from 'lucide-react';
import { Button, signIn, signUp, type Session } from '@meterflow/shared';

function slug(s: string) {
  return s
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40);
}

/** Self-service sign-up: creates the account, signs in and hands the session to the portal. */
export function SignUpForm({ onBack, onSignedIn }: { onBack: () => void; onSignedIn: (s: Session) => void }) {
  const [company, setCompany] = useState('');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [touchedUser, setTouchedUser] = useState(false);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [agree, setAgree] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const effectiveUser = touchedUser ? username : slug(company || name);
  const userValid = /^[a-z0-9][a-z0-9_-]{2,39}$/.test(effectiveUser);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!name.trim() || !email.trim()) return setError('Enter your name and email.');
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim())) return setError('Enter a valid email address.');
    if (!userValid) return setError('Customer ID: 3–40 lowercase letters, digits, “-” or “_”.');
    if (password.length < 8) return setError('Password must be at least 8 characters.');
    if (password !== confirm) return setError('Passwords do not match.');
    if (!agree) return setError('Please accept the billing terms.');
    setBusy(true);
    try {
      await signUp({ username: effectiveUser, password, displayName: name.trim(), email: email.trim(), company: company.trim() || undefined });
      onSignedIn(await signIn(effectiveUser, password));
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="login-form" onSubmit={submit} noValidate>
      <button type="button" className="link-btn back-link" onClick={onBack}>
        <ArrowLeft size={14} /> Back to sign in
      </button>
      <h2>Create your account</h2>
      <p className="muted">Start metering usage in minutes. You choose a plan in the next step.</p>
      <div className="form-grid two">
        <label className="field">
          <span className="field-label">Company</span>
          <input value={company} onChange={(e) => setCompany(e.target.value)} placeholder="Acme Ltd" autoComplete="organization" />
        </label>
        <label className="field">
          <span className="field-label">Your name</span>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Rahim Uddin" autoComplete="name" />
        </label>
      </div>
      <label className="field">
        <span className="field-label">Work email</span>
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="billing@acme.com" autoComplete="email" />
      </label>
      <label className="field">
        <span className="field-label">Customer ID (your username)</span>
        <input
          value={effectiveUser}
          onChange={(e) => {
            setTouchedUser(true);
            setUsername(e.target.value.toLowerCase());
          }}
          placeholder="acme-ltd"
          autoComplete="username"
        />
        <span className="field-hint">Used to sign in and to tag your usage events. It cannot be changed later.</span>
      </label>
      <div className="form-grid two">
        <label className="field">
          <span className="field-label">Password</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" placeholder="8+ characters" />
        </label>
        <label className="field">
          <span className="field-label">Confirm password</span>
          <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" />
        </label>
      </div>
      <label className="check small">
        <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />I agree to be billed monthly for the usage my account records.
      </label>
      {error && <div className="alert alert-bad compact">{error}</div>}
      <Button type="submit" variant="primary" className="btn-block" loading={busy} icon={!busy && <UserPlus size={16} />}>
        Create account
      </Button>
    </form>
  );
}
