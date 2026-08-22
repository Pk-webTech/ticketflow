import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Auth, auth } from '../lib/api.js';

export default function Register() {
  const [form, setForm] = useState({ fullName: '', email: '', password: '', phone: '', role: 'CUSTOMER' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError('');
    try {
      const res = await Auth.register(form);
      // register returns a full AuthResponse, so sign the user straight in.
      if (res?.accessToken) {
        auth.save(res.accessToken,
          { id: res.userId, email: res.email, role: res.role, fullName: res.fullName });
        navigate('/');
      } else {
        navigate('/login');
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card" style={{ maxWidth: 400, margin: '48px auto' }}>
      <h2 style={{ marginTop: 0 }}>Create an account</h2>
      {error && <div className="error">{error}</div>}
      <form onSubmit={submit}>
        <label>Full name</label>
        <input value={form.fullName} onChange={set('fullName')} required />
        <label>Email</label>
        <input type="email" value={form.email} onChange={set('email')} required />
        <label>Phone</label>
        <input value={form.phone} onChange={set('phone')} />
        <label>Password</label>
        <input type="password" value={form.password} onChange={set('password')} minLength={8} required />
        <p className="muted" style={{ fontSize: 12, marginTop: 6 }}>
          At least 8 characters, with an uppercase letter, a lowercase letter and a digit.
        </p>
        <label>I am a</label>
        <select value={form.role} onChange={set('role')}>
          <option value="CUSTOMER">Customer — I want to book tickets</option>
          <option value="ORGANISER">Organiser — I run events</option>
        </select>
        <button style={{ width: '100%', marginTop: 20 }} disabled={busy}>
          {busy ? 'Creating…' : 'Create account'}
        </button>
      </form>
      <p className="muted" style={{ marginTop: 18 }}>
        Already registered? <Link to="/login" style={{ color: 'var(--accent)' }}>Sign in</Link>
      </p>
    </div>
  );
}
