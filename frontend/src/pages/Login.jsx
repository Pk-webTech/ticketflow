import { useState } from 'react';
import { useNavigate, Link, useLocation } from 'react-router-dom';
import { Auth, auth } from '../lib/api.js';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      const res = await Auth.login(email, password);
      auth.save(res.accessToken, {
        id: res.userId,
        email: res.email,
        role: res.role,
        fullName: res.fullName,
      });
      // Return the user to wherever they were headed (e.g. a claim link).
      navigate(location.state?.from ?? '/', { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card" style={{ maxWidth: 400, margin: '48px auto' }}>
      <h2 style={{ marginTop: 0 }}>Sign in</h2>
      {error && <div className="error">{error}</div>}
      <form onSubmit={submit}>
        <label>Email</label>
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <label>Password</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <button style={{ width: '100%', marginTop: 20 }} disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="muted" style={{ marginTop: 18 }}>
        No account? <Link to="/register" style={{ color: 'var(--accent)' }}>Register</Link>
      </p>
    </div>
  );
}
