import { useEffect, useState } from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { auth } from '../lib/api.js';

export default function NavBar() {
  const navigate = useNavigate();
  const location = useLocation();
  const [user, setUser] = useState(auth.user);
  const [open, setOpen] = useState(false);

  // auth.save/clear dispatch 'tf-auth', so the bar updates without a reload.
  useEffect(() => {
    const sync = () => setUser(auth.user);
    window.addEventListener('tf-auth', sync);
    window.addEventListener('storage', sync);
    return () => {
      window.removeEventListener('tf-auth', sync);
      window.removeEventListener('storage', sync);
    };
  }, []);

  useEffect(() => { setOpen(false); }, [location.pathname]);

  const signOut = () => {
    auth.clear();
    navigate('/login');
  };

  const initials = (user?.fullName || user?.email || '?')
    .split(/[\s@.]+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('');

  return (
    <nav className="nav">
      <div className="nav-inner">
        <NavLink to="/" className="brand">
          <span className="brand-mark"><span className="brand-stub" /></span>
          TicketFlow
        </NavLink>

        <button className="nav-toggle" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
          {open ? '✕' : '☰'}
        </button>

        <div className={`nav-links ${open ? 'open' : ''}`}>
          <NavLink to="/" end className="nav-link">Browse</NavLink>
          {user && <NavLink to="/bookings" className="nav-link">My tickets</NavLink>}
          {user && <NavLink to="/waitlist" className="nav-link">Waitlist</NavLink>}
          {(user?.role === 'ORGANISER' || user?.role === 'ADMIN') &&
            <NavLink to="/organiser" className="nav-link">Organiser</NavLink>}
          {user?.role === 'ADMIN' &&
            <NavLink to="/admin/venues" className="nav-link">Venues</NavLink>}
        </div>

        <div className="nav-user">
          {user ? (
            <>
              <span className="avatar" title={user.email}>{initials}</span>
              <span className="who">{user.fullName || user.email}</span>
              <button className="ghost tiny" onClick={signOut}>Sign out</button>
            </>
          ) : (
            <>
              <NavLink to="/login" className="nav-link">Sign in</NavLink>
              <NavLink to="/register" className="btn">Create account</NavLink>
            </>
          )}
        </div>
      </div>
    </nav>
  );
}
