import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import SeatMap from '../components/SeatMap.jsx';
import { Seats, Bookings, Events, Waitlist, auth, ApiError } from '../lib/api.js';
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
  const [seats, setSeats] = useState([]);
  const [selected, setSelected] = useState([]);
  const [hold, setHold] = useState(null);
  const [remaining, setRemaining] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  const holdRef = useRef(null);
  holdRef.current = hold;

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
      } catch (err) {
        if (!cancelled) setError(err.message);
      }
    })();
    return () => { cancelled = true; };
  }, [showId]);

  // ---- live updates ------------------------------------------------
  useEffect(() => {
    const disconnect = subscribeSeatMap(showId, (event) => {
      const affected = new Set(event.seatIds ?? []);
      setSeats((prev) => prev.map((seat) => {
        if (!affected.has(seat.seatId)) return seat;
        // Our own hold shouldn't grey itself out under us.
        if (holdRef.current && holdRef.current.holdId === event.holdId) return seat;
        return { ...seat, status: event.status };
      }));
    });
    return disconnect;
  }, [showId]);

  // ---- hold countdown ----------------------------------------------
  useEffect(() => {
    if (!hold) return;
    const deadline = new Date(hold.expiresAt).getTime();
    const tick = () => {
      const left = Math.max(0, Math.round((deadline - Date.now()) / 1000));
      setRemaining(left);
      if (left === 0) {
        setHold(null);
        setSelected([]);
        setNotice('Your seat hold expired and the seats were released automatically.');
      }
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [hold]);

  // ---- release on leaving the page ---------------------------------
  useEffect(() => () => {
    const h = holdRef.current;
    if (h) Seats.release(h.showId, h.holdId, h.seatIds).catch(() => {});
  }, []);

  const toggleSeat = (seat) => {
    if (hold) return;   // selection is frozen once seats are held
    setSelected((prev) =>
      prev.includes(seat.seatId) ? prev.filter((id) => id !== seat.seatId) : [...prev, seat.seatId]);
  };

  const selectedSeats = useMemo(
    () => seats.filter((s) => selected.includes(s.seatId)), [seats, selected]);

  const total = useMemo(
    () => selectedSeats.reduce((sum, s) => sum + Number(s.price ?? 0), 0), [selectedSeats]);

  const soldOut = seats.length > 0 && seats.every((s) => s.status === 'BOOKED');

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

  return (
    <div>
      <h2 style={{ marginBottom: 4 }}>Select your seats</h2>
      <p className="muted">{dateTime(show?.showDateTime)}</p>

      {error && <div className="error">{error}</div>}
      {notice && <div className="notice">{notice}</div>}

      {soldOut && (
        <div className="card" style={{ marginTop: 16 }}>
          <strong>This show is sold out.</strong>
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

      <SeatMap seats={seats} selected={selected} onToggle={toggleSeat} disabled={busy || !!hold} />

      {selected.length > 0 && (
        <div className="checkout-bar">
          <div>
            <div style={{ fontWeight: 600 }}>
              {selectedSeats.map((s) => `${s.rowLabel}${s.seatNumber}`).join(', ')}
            </div>
            <div className="muted">{selected.length} seat(s) · {money(total)}</div>
          </div>

          <span style={{ marginLeft: 'auto' }} />

          {hold ? (
            <>
              <span className="timer">⏳ {mmss(remaining)}</span>
              <button className="ghost" onClick={releaseHold} disabled={busy}>Release</button>
              <button className="ok" onClick={confirmBooking} disabled={busy}>
                Pay {money(total)} & confirm
              </button>
            </>
          ) : (
            <button onClick={placeHold} disabled={busy}>Hold these seats</button>
          )}
        </div>
      )}
    </div>
  );
}
