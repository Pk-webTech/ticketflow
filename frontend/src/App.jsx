import { Routes, Route, Navigate } from 'react-router-dom';
import NavBar from './components/NavBar.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import EventList from './pages/EventList.jsx';
import ShowSeatMap from './pages/ShowSeatMap.jsx';
import MyBookings from './pages/MyBookings.jsx';
import MyWaitlist from './pages/MyWaitlist.jsx';
import WaitlistOffer from './pages/WaitlistOffer.jsx';
import OrganiserDashboard from './pages/OrganiserDashboard.jsx';
import AdminVenues from './pages/AdminVenues.jsx';
import { auth } from './lib/api.js';

/** Route guard. `roles` empty = any signed-in user. */
function Protected({ children, roles = [] }) {
  const user = auth.user;
  if (!auth.token || !user) return <Navigate to="/login" replace />;
  if (roles.length && !roles.includes(user.role)) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <>
      <NavBar />
      <div className="container">
        <Routes>
          <Route path="/" element={<EventList />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/shows/:showId" element={<ShowSeatMap />} />

          {/* Public on purpose: the emailed token is the credential. The page
              itself prompts for sign-in only at the moment of accepting. */}
          <Route path="/waitlist/offer/:token" element={<WaitlistOffer />} />

          <Route path="/bookings" element={<Protected><MyBookings /></Protected>} />
          <Route path="/waitlist" element={<Protected><MyWaitlist /></Protected>} />
          <Route
            path="/organiser"
            element={<Protected roles={['ORGANISER', 'ADMIN']}><OrganiserDashboard /></Protected>}
          />
          <Route
            path="/admin/venues"
            element={<Protected roles={['ADMIN']}><AdminVenues /></Protected>}
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
    </>
  );
}
