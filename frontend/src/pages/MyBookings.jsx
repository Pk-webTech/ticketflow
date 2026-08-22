import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Bookings } from '../lib/api.js';
import { money, dateTime } from '../lib/format.js';

export default function MyBookings() {
  const [bookings, setBookings] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busyId, setBusyId] = useState(null);
  const location = useLocation();

  const load = async () => {
    try {
      const page = await Bookings.mine();
      setBookings(page?.content ?? page ?? []);
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => {
    load();
    if (location.state?.justBooked) {
      setNotice(`Booking ${location.state.justBooked} confirmed — your QR ticket is on its way by email.`);
    }
  }, []);   // eslint-disable-line react-hooks/exhaustive-deps

  const cancel = async (id) => {
    if (!confirm('Cancel this booking? The seats will be offered to customers on the waitlist.')) return;
    setBusyId(id); setError('');
    try {
      await Bookings.cancel(id);
      setNotice('Booking cancelled. If anyone is waitlisted for these seats, they have been emailed an offer.');
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <h2>My bookings</h2>
      {notice && <div className="notice">{notice}</div>}
      {error && <div className="error">{error}</div>}
      {bookings.length === 0 && <p className="muted">You haven't booked anything yet.</p>}

      <div className="grid" style={{ marginTop: 16 }}>
        {bookings.map((b) => (
          <div className="card" key={b.id}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <div style={{ fontSize: 17, fontWeight: 600 }}>{b.eventTitle}</div>
              <span className={`badge ${b.status === 'CONFIRMED' ? 'confirmed' : 'cancelled'}`}>
                {b.status}
              </span>
              <span style={{ marginLeft: 'auto', fontFamily: 'monospace', color: 'var(--muted)' }}>
                {b.bookingReference}
              </span>
            </div>

            <div className="muted" style={{ marginTop: 6 }}>
              {b.venueName} · {dateTime(b.showStartsAt)}
            </div>

            <div style={{ marginTop: 12, fontSize: 14 }}>
              Seats: <strong>{b.seats?.map((s) => s.seatLabel).join(', ')}</strong>
              <span className="muted"> · {money(b.totalAmount)}</span>
            </div>

            {b.status === 'CONFIRMED' && (
              <button
                className="ghost"
                style={{ marginTop: 14 }}
                disabled={busyId === b.id}
                onClick={() => cancel(b.id)}
              >
                {busyId === b.id ? 'Cancelling…' : 'Cancel booking'}
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
