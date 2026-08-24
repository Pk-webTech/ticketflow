import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import SeatMap from '../components/SeatMap.jsx';
import { Seats, Bookings, Events, Venues, Waitlist, auth, ApiError } from '../lib/api.js';
import { subscribeSeatMap } from '../lib/seatSocket.js';
import { money, mmss, dateTime } from '../lib/format.js';

/**
 * The core customer screen: live seat map → hold → checkout.
 *
 * Three things worth noting:
 *
 * 1. The map is seeded once by REST, then kept current purely by WebSocket
 *    deltas. We never poll — seat-hold-service pushes every hold, release and
 *    TTL expiry, so another customer's selection greys out here within
 *    milliseconds.
 *
 * 2. The countdown is display-only. The authoritative TTL lives in Redis; if
 *    this tab is backgrounded and the timer drifts, checkout still fails
 *    server-side with a clear 409. Never trust the client clock for
 *    correctness — only for telling the user what's happening.
 *
 * 3. Abandoning checkout is handled two ways: an explicit release on unmount
 *    (instant, courteous) and the Redis TTL (guaranteed, even if the browser
 *    is killed). The second is what actually makes the requirement hold.
 */
export default function ShowSeatMap() {
  const { showId } = useParams();
  const navigate = useNavigate();

  const [show, setShow] = useState(null);
  const [event, setEvent] = useState(null);
  const [venue, setVenue] = useState(null);
  const [seats, setSeats] = useState([]);
  const [selected, setSelected] = useState([]);
  const [hold, setHold] = useState(null);
  const [remaining, setRemaining] = useState(0);
  const [ttl, setTtl] = useState(600);
  const [changed, setChanged] = useState(new Set());
  const [socket, setSocket] = useState('connecting');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);

  const holdRef = useRef(null);
  holdRef.current = hold;
  const selectedRef = useRef([]);
  selectedRef.current = selected;

  // ---- initial load ------------------------------------------------
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [mapData, showData, booked] = await Promise.all([
          Seats.map(showId),
          Events.show(showId).catch(() => null),
          Bookings.bookedSeats(showId).catch(() => []),
        ]);
        if (cancelled) return;

        // Overlay permanently-booked seats: seat-hold-service knows about holds,
        // booking-service is the authority on what is sold.
        const bookedSet = new Set(booked ?? []);
        setSeats((mapData.seats ?? []).map((s) =>
          bookedSet.has(s.seatId) ? { ...s, status: 'BOOKED' } : s));
        setShow(showData);

        // ShowResponse carries no title or venue name, so fetch them separately
        // and treat both as best-effort decoration.
        if (showData?.eventId) Events.get(showData.eventId).then(setEvent).catch(() => {});
        if (showData?.venueId) Venues.get(showData.venueId).then(setVenue).catch(() => {});
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [showId]);

  // ---- live updates ------------------------------------------------
  useEffect(() => {
    const disconnect = subscribeSeatMap(showId, (event) => {
      const affected = new Set(event.seatIds ?? []);
      if (affected.size === 0) return;

      setSeats((prev) => prev.map((s) => {
        if (!affected.has(s.seatId)) return s;
        // Our own hold is echoed back to us as HELD; don't let the broadcast
        // downgrade a seat we are actively holding.
        const mine = holdRef.current && event.holdId === holdRef.current.holdId;
        if (event.status === 'HELD') {
          return { ...s, status: mine ? 'HELD_BY_ME' : 'HELD_BY_OTHERS' };
        }
        if (event.status === 'BOOKED') return { ...s, status: 'BOOKED' };
        return { ...s, status: 'AVAILABLE' };
      }));

      // Drop anything we had selected that someone else just won.
      if (event.status !== 'AVAILABLE' && !(holdRef.current && event.holdId === holdRef.current.holdId)) {
        setSelected((prev) => prev.filter((id) => !affected.has(id)));
      }

      setChanged(affected);
      setTimeout(() => setChanged(new Set()), 600);
    }, setSocket);

    return disconnect;
  }, [showId]);

  // ---- hold countdown ----------------------------------------------
  useEffect(() => {
    if (!hold) { setRemaining(0); return undefined; }
    setTtl(hold.ttlSeconds || 600);
    const expiry = new Date(hold.expiresAt).getTime();
    const tick = () => setRemaining(Math.max(0, Math.round((expiry - Date.now()) / 1000)));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [hold]);

  useEffect(() => {
    if (hold && remaining === 0) {
      setHold(null);
      setSelected([]);
      setError('Your hold expired and the seats went back on sale. Pick them again if they are still free.');
    }
  }, [remaining, hold]);

  // ---- courtesy release on unmount ---------------------------------
  useEffect(() => () => {
    const h = holdRef.current;
    if (h) Seats.release(showId, h.holdId, selectedRef.current).catch(() => {});
  }, [showId]);

  // ---- derived ------------------------------------------------------
  const selectedSeats = useMemo(
    () => seats.filter((s) => selected.includes(s.seatId)),
    [seats, selected],
  );
  const total = useMemo(
    () => selectedSeats.reduce((sum, s) => sum + Number(s.price ?? 0), 0),
    [selectedSeats],
  );
  const soldOut = seats.length > 0 && seats.every((s) => s.status === 'BOOKED');

  const toggleSeat = (seat) => {
    if (hold) return;   // the hold is fixed once placed; release to change it
    setSelected((prev) => prev.includes(seat.seatId)
      ? prev.filter((id) => id !== seat.seatId)
      : [...prev, seat.seatId]);
  };

  // ---- actions ------------------------------------------------------
  const placeHold = async () => {
    if (!auth.token) return navigate('/login');
    setBusy(true); setError(''); setNotice('');
    try {
      setHold(await Seats.hold(showId, selected));
    } catch (err) {
      // 409 here means someone else won the race for one of these seats.
      setError(err.message);
      if (err instanceof ApiError && err.status === 409) setSelected([]);
    } finally {
      setBusy(false);
    }
  };

  const releaseHold = async () => {
    if (!hold) return;
    setBusy(true);
    try {
      await Seats.release(showId, hold.holdId, selected);
      setHold(null);
      setSelected([]);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const confirmBooking = async () => {
    setBusy(true); setError('');
    try {
      const booking = await Bookings.confirm({
        showId, holdId: hold.holdId, seatIds: selected, customerName: auth.user?.fullName,
      });
      setHold(null);
      navigate('/bookings', { state: { justBooked: booking.bookingReference } });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const joinWaitlist = async (categoryId, categoryName) => {
    if (!auth.token) return navigate('/login');
    try {
      await Waitlist.join({ showId, eventId: show?.eventId, categoryId, categoryName, quantity: 1 });
      setNotice(`You're on the waitlist for ${categoryName}. We'll email you the moment a seat frees up.`);
    } catch (err) {
      setError(err.message);
    }
  };

  const categories = useMemo(() => {
    const map = new Map();
    seats.forEach((s) => { if (s.categoryId) map.set(s.categoryId, s.categoryName); });
    return [...map.entries()];
  }, [seats]);

  const urgent = hold && remaining <= 60;

  return (
    <div>
      <div className="panel-head" style={{ marginTop: 8 }}>
        <div>
          <div className="eyebrow">Choose your seats</div>
          <h2>{event?.title || 'This show'}</h2>
          <p className="muted" style={{ marginTop: 6 }}>
            {[venue ? `${venue.name}${venue.city ? `, ${venue.city}` : ''}` : null, dateTime(show?.showDateTime)]
              .filter(Boolean).join(' · ')}
          </p>
        </div>
        <span className={`live-pill ${socket === 'live' ? '' : 'off'}`}>
          <span className="live-dot" />
          {socket === 'live' ? 'Live' : socket === 'reconnecting' ? 'Reconnecting' : 'Connecting'}
        </span>
      </div>

      {error && <div className="error">{error}</div>}
      {notice && <div className="notice">{notice}</div>}

      {soldOut && (
        <div className="card" style={{ marginTop: 16 }}>
          <strong>Every seat is sold.</strong>
          <p className="muted" style={{ marginTop: 6 }}>
            Join a waitlist and you'll be emailed a time-limited claim link if someone cancels.
          </p>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 12 }}>
            {categories.map(([id, name]) => (
              <button key={id} className="ghost" onClick={() => joinWaitlist(id, name)}>
                Join {name} waitlist
              </button>
            ))}
          </div>
        </div>
      )}

      {loading
        ? <div className="skeleton" style={{ height: 320, marginTop: 26 }} />
        : (
          <SeatMap
            seats={seats}
            selected={selected}
            onToggle={toggleSeat}
            disabled={busy || !!hold}
            changed={changed}
          />
        )}

      {selected.length > 0 && (
        <div className="dock">
          <div>
            <div className="faint">
              {selected.length} seat{selected.length > 1 ? 's' : ''} ·{' '}
              {selectedSeats.map((s) => s.seatLabel || `${s.rowLabel}${s.seatNumber}`).join(', ')}
            </div>
            <div className="total">{money(total)}</div>
          </div>

          <div className="spacer" />

          {hold && (
            <div className={`timer ${urgent ? 'urgent' : ''}`}>
              <div className="timer-ring" style={{ '--p': Math.max(0, (remaining / ttl) * 100) }}>
                <span>{mmss(remaining)}</span>
              </div>
              <div className="faint" style={{ maxWidth: 150 }}>
                Seats held for you. Check out before the timer runs out.
              </div>
            </div>
          )}

          {hold ? (
            <>
              <button className="ghost" onClick={releaseHold} disabled={busy}>Release</button>
              <button onClick={confirmBooking} disabled={busy}>
                {busy ? 'Confirming…' : `Pay ${money(total)}`}
              </button>
            </>
          ) : (
            <button onClick={placeHold} disabled={busy}>
              {busy ? 'Holding…' : 'Hold these seats'}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
