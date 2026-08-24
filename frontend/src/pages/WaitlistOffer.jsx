import { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Waitlist, auth } from '../lib/api.js';
import { dateTime, mmss } from '../lib/format.js';

/**
 * Landing page for the emailed claim link. The token in the URL is the
 * credential, so the page loads for anyone — we only ask for sign-in at the
 * moment of accepting, because accepting creates a hold owned by a real user.
 */
export default function WaitlistOffer() {
  const { token } = useParams();
  const navigate = useNavigate();
  const [offer, setOffer] = useState(null);
  const [remaining, setRemaining] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    Waitlist.viewOffer(token).then(setOffer).catch((err) => setError(err.message));
  }, [token]);

  useEffect(() => {
    if (!offer?.expiresAt) return undefined;
    const expiry = new Date(offer.expiresAt).getTime();
    const tick = () => setRemaining(Math.max(0, Math.round((expiry - Date.now()) / 1000)));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [offer]);

  const accept = async () => {
    if (!auth.token) return navigate('/login');
    setBusy(true); setError('');
    try {
      const res = await Waitlist.acceptOffer(token);
      // Accepting converts the offer into a real seat hold, so send the
      // customer to checkout with the clock already running.
      const showId = res?.showId || offer?.showId;
      if (showId) navigate(`/shows/${showId}`);
      else navigate('/bookings');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const dead = remaining === 0;

  return (
    <div style={{ maxWidth: 560, margin: '40px auto' }}>
      <div className="eyebrow">A seat opened up</div>
      <h2>Your waitlist offer</h2>

      {error && <div className="error">{error}</div>}

      {!offer && !error && <div className="skeleton" style={{ height: 160, marginTop: 20 }} />}

      {offer && (
        <div className="card rise" style={{ marginTop: 18 }}>
          <h3>{offer.eventTitle || 'Show'}</h3>
          <p className="muted" style={{ marginTop: 6 }}>
            {[offer.venueName, offer.categoryName, dateTime(offer.showDateTime)].filter(Boolean).join(' · ')}
          </p>

          {remaining !== null && (
            <p style={{ marginTop: 14, color: dead ? 'var(--rose)' : 'var(--amber)' }}>
              {dead
                ? 'This offer has expired and has moved to the next person in the queue.'
                : <>Claim within <strong className="mono">{mmss(remaining)}</strong> or it passes to the next person.</>}
            </p>
          )}

          <div style={{ display: 'flex', gap: 10, marginTop: 18, flexWrap: 'wrap' }}>
            <button onClick={accept} disabled={busy || dead}>
              {busy ? 'Claiming…' : 'Claim these seats'}
            </button>
            <Link className="nav-link" to="/">Not now</Link>
          </div>

          {!auth.token && (
            <p className="faint" style={{ marginTop: 14 }}>
              You'll be asked to sign in first — the seats are held against your account.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
