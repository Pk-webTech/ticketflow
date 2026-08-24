import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Bookings } from '../lib/api.js';
import { money, dateTime } from '../lib/format.js';

/** Bookings render as physical ticket stubs — perforation, tear-off and all. */
export default function MyBookings() {
  const [bookings, setBookings] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [loading, setLoading] = useState(true);
  const location = useLocation();

  const load = async () => {
    try {
      const page = await Bookings.mine();
      setBookings(page?.content ?? page ?? []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
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
      setNotice('Booking cancelled. Anyone waitlisted for these seats has been emailed an offer.');
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <div className="eyebrow">Your tickets</div>
      <h2>My bookings</h2>

      {notice && <div className="notice">{notice}</div>}
      {error && <div className="error">{error}</div>}

      {loading && <div className="skeleton" style={{ height: 140, marginTop: 20 }} />}

      {!loading && bookings.length === 0 && (
        <div className="empty" style={{ marginTop: 20 }}>
          <h3>No tickets yet</h3>
          <p>Find a show and hold a seat — it takes about thirty seconds.</p>
          <Link className="btn" to="/" style={{ display: 'inline-block', marginTop: 16 }}>Browse shows</Link>
        </div>
      )}

      <div style={{ display: 'grid', gap: 16, marginTop: 20 }}>
        {bookings.map((b, i) => (
          <article
            className={`stub rise ${b.status === 'CANCELLED' ? 'is-cancelled' : ''}`}
            key={b.id}
            style={{ animationDelay: `${i * 60}ms` }}
          >
            <div className="stub-main">
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                <h3>{b.eventTitle || 'Show'}</h3>
                <span className={`badge ${b.status === 'CONFIRMED' ? 'confirmed' : 'cancelled'}`}>
                  {b.status}
                </span>
              </div>

              <div className="muted" style={{ marginTop: 6, fontSize: 13.5 }}>
                {[b.venueName, dateTime(b.showStartsAt || b.showDateTime)].filter(Boolean).join(' · ')}
              </div>

              <div className="stub-seats">
                {(b.seats ?? []).map((s) => (
                  <span className="seat-tag" key={s.seatId || s.seatLabel}>
                    {s.seatLabel || `${s.rowLabel ?? ''}${s.seatNumber ?? ''}`}
                  </span>
                ))}
              </div>

              {b.status === 'CONFIRMED' && (
                <button className="danger tiny" style={{ marginTop: 16 }}
                  disabled={busyId === b.id} onClick={() => cancel(b.id)}>
                  {busyId === b.id ? 'Cancelling…' : 'Cancel booking'}
                </button>
              )}
            </div>

            <div className="stub-tear">
              <span className="faint" style={{ letterSpacing: '0.2em', fontSize: 10 }}>REF</span>
              <span className="stub-ref">{b.bookingReference}</span>
              <strong style={{ fontFamily: 'var(--font-display)', fontSize: 18 }}>
                {money(b.totalAmount)}
              </strong>
              <span className="faint">QR sent by email</span>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}
