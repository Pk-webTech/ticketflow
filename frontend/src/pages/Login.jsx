import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Auth, auth } from '../lib/api.js';

export default function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      const res = await Auth.login(email, password);
      auth.save(res.accessToken, {
        userId: res.userId, email: res.email, role: res.role, fullName: res.fullName,
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
        <div className="eyebrow">Welcome back</div>
        <h1>The seat you want is<br />still on the board.</h1>
        <p className="lead" style={{ margin: '18px 0 0' }}>
          Sign in to hold seats, see your QR tickets and manage your place in any waitlist queue.
        </p>
      </aside>

      <div className="card auth-card rise">
        <h2>Sign in</h2>
        <p className="muted" style={{ marginTop: 6 }}>Use the email you booked with.</p>
        {error && <div className="error">{error}</div>}

        <div style={{ marginTop: 18 }}>
          <label className="field">
            <span>Email</span>
            <input type="email" value={email} required autoComplete="email"
              onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" />
          </label>
          <label className="field">
            <span>Password</span>
            <input type="password" value={password} required autoComplete="current-password"
              onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
          </label>
          <button style={{ width: '100%', marginTop: 6 }} disabled={busy} onClick={submit}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </div>

        <p className="muted" style={{ marginTop: 18, fontSize: 13.5 }}>
          New here? <Link to="/register">Create an account</Link>
        </p>
      </div>
    </div>
  );
}
