import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Auth, auth } from '../lib/api.js';

const ROLES = [
  ['CUSTOMER', 'Book tickets'],
  ['ORGANISER', 'Sell tickets'],
  ['ADMIN', 'Run venues'],
];

export default function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', role: 'CUSTOMER' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      await Auth.register(form);
      // Register doesn't return a session, so sign in straight away.
      const res = await Auth.login(form.email, form.password);
      auth.save(res.accessToken, {
        userId: res.userId, email: res.email, role: res.role, fullName: form.fullName,
      });
      navigate('/');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-shell">
      <aside className="auth-aside">
        <div className="eyebrow">Create an account</div>
        <h1>Two minutes now,<br />front row later.</h1>
        <p className="lead" style={{ margin: '18px 0 0' }}>
          Customers book and join waitlists. Organisers publish shows and watch revenue.
          Admins build the venues everything else runs on.
        </p>
      </aside>

      <div className="card auth-card rise">
        <h2>Create your account</h2>
        {error && <div className="error">{error}</div>}

        <div style={{ marginTop: 18 }}>
          <label className="field">
            <span>Full name</span>
            <input value={form.fullName} required onChange={set('fullName')} placeholder="Asha Menon" />
          </label>
          <label className="field">
            <span>Email</span>
            <input type="email" value={form.email} required autoComplete="email"
              onChange={set('email')} placeholder="you@example.com" />
          </label>
          <label className="field">
            <span>Password</span>
            <input type="password" value={form.password} required minLength={8}
              autoComplete="new-password" onChange={set('password')} placeholder="At least 8 characters" />
          </label>

          <label className="field">
            <span>What will you use TicketFlow for?</span>
            <div className="role-picker">
              {ROLES.map(([value, label]) => (
                <button
                  type="button"
                  key={value}
                  className={`role-opt ${form.role === value ? 'on' : ''}`}
                  onClick={() => setForm((f) => ({ ...f, role: value }))}
                >
                  {label}
                </button>
              ))}
            </div>
          </label>

          <button style={{ width: '100%', marginTop: 6 }} disabled={busy} onClick={submit}>
            {busy ? 'Creating…' : 'Create account'}
          </button>
        </div>

        <p className="muted" style={{ marginTop: 18, fontSize: 13.5 }}>
          Already have one? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  );
}
