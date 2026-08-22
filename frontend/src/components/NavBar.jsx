import { NavLink, useNavigate } from 'react-router-dom';
import { auth } from '../lib/api.js';

export default function NavBar() {
  const navigate = useNavigate();
  const user = auth.user;

  const signOut = () => {
    auth.clear();
    navigate('/login');
  };

  return (
    <nav className="nav">
      <NavLink to="/" className="brand">TicketFlow</NavLink>
      <NavLink to="/">Events</NavLink>
      {user && <NavLink to="/bookings">My bookings</NavLink>}
      {user && <NavLink to="/waitlist">My waitlist</NavLink>}
      {user && (user.role === 'ORGANISER' || user.role === 'ADMIN') && (
        <NavLink to="/organiser">Organiser</NavLink>
      )}
      {user?.role === 'ADMIN' && <NavLink to="/admin/venues">Venues</NavLink>}

      <span className="spacer" />
      {user ? (
        <>
          <span className="muted">{user.email}</span>
          <button className="ghost" onClick={signOut}>Sign out</button>
        </>
      ) : (
        <>
          <NavLink to="/login">Sign in</NavLink>
          <NavLink to="/register" className="btn">Register</NavLink>
        </>
      )}
    </nav>
  );
}
