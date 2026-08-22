import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Waitlist, auth } from '../lib/api.js';
import { mmss } from '../lib/format.js';

/**
 * The page the emailed claim link opens.
 *
 * Viewing is public — the token is the credential — but accepting requires a
 * signed-in session so the backend can verify the claimer is actually the
 * person the offer was made to. If they aren't signed in we bounce to login
 * and come straight back here.
 *
 * Accepting publishes waitlist.offer.accepted, which seat-hold-service turns
 * into a normal TTL hold, so we then drop the customer into the ordinary
 * seat-map checkout rather than a separate purchase flow.
 */
export default function WaitlistOffer() {
  const { token } = useParams();
  const navigate = useNavigate();

  const [offer, setOffer] = useState(null);
  const [remaining, setRemaining] = useState(0);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    Waitlist.viewOffer(token).then(setOffer).catch((err) => setError(err.message));
  }, [token]);

  useEffect(() => {
    if (!offer) return;
    const deadline = new Date(offer.expiresAt).getTime();
    const tick = () => setRemaining(Math.max(0, Math.round((deadline - Date.now()) / 1000)));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [offer]);

  const accept = async () => {
    if (!auth.token) {
      return navigate('/login', { state: { from: `/waitlist/offer/${token}` } });
    }
    setBusy(true); setError('');
    try {
      await Waitlist.acceptOffer(token);
      navigate(`/shows/${offer.showId}`, { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  if (error) {
    return (
      <div className="card" style={{ maxWidth: 480, margin: '48px auto' }}>
        <h2 style={{ marginTop: 0 }}>This offer isn't available</h2>
        <div className="error">{error}</div>
        <p className="muted">
          Offers are time-limited, and once they lapse the seats pass to the next customer in the
          queue. You're still welcome to rejoin the waitlist.
        </p>
        <button className="ghost" onClick={() => navigate('/')}>Browse events</button>
      </div>
    );
  }

  if (!offer) return <p className="muted" style={{ marginTop: 40 }}>Loading your offer…</p>;

  const lapsed = remaining === 0 || offer.status !== 'PENDING';

  return (
    <div className="card" style={{ maxWidth: 480, margin: '48px auto', textAlign: 'center' }}>
      <h2 style={{ marginTop: 0 }}>A seat opened up for you</h2>

      <div style={{ fontSize: 15, marginTop: 14 }}>
        {offer.seatLabels?.length > 0
          ? <>Seats <strong>{offer.seatLabels.join(', ')}</strong> are held for you.</>
          : <>{offer.seatIds.length} seat(s) are held for you.</>}
      </div>

      <div style={{ margin: '26px 0' }}>
        <div className="muted" style={{ fontSize: 12, letterSpacing: 1 }}>TIME REMAINING</div>
        <div className="timer" style={{ fontSize: 40 }}>{mmss(remaining)}</div>
      </div>

      {lapsed ? (
        <p className="muted">
          This offer has closed. The seats have been passed to the next customer in the queue.
        </p>
      ) : (
        <>
          <button className="ok" style={{ width: '100%' }} onClick={accept} disabled={busy}>
            {busy ? 'Claiming…' : 'Claim these seats'}
          </button>
          <p className="muted" style={{ fontSize: 12, marginTop: 14 }}>
            Claiming places a normal seat hold — you'll finish checkout on the seat map as usual.
          </p>
        </>
      )}
    </div>
  );
}
