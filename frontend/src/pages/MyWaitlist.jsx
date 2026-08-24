import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Waitlist } from '../lib/api.js';
import { dateTime } from '../lib/format.js';

/**
 * Queue position is derived server-side from created_at, so it can move on its
 * own between visits — the copy says so rather than implying it is fixed.
 */
export default function MyWaitlist() {
  const [entries, setEntries] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    try {
      const data = await Waitlist.mine();
      setEntries(Array.isArray(data) ? data : data?.content ?? []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const leave = async (id) => {
    setBusyId(id); setError('');
    try {
      await Waitlist.leave(id);
      setNotice('You have left that queue.');
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <div className="eyebrow">Sold-out shows you're queued for</div>
      <h2>Waitlist</h2>

      {notice && <div className="notice">{notice}</div>}
      {error && <div className="error">{error}</div>}

      {loading && <div className="skeleton" style={{ height: 90, marginTop: 20 }} />}

      {!loading && entries.length === 0 && (
        <div className="empty" style={{ marginTop: 20 }}>
          <h3>You're not in any queue</h3>
          <p>When a show sells out, its page offers a waitlist for each seat category.</p>
          <Link className="btn" to="/" style={{ display: 'inline-block', marginTop: 16 }}>Browse shows</Link>
        </div>
      )}

      <div style={{ display: 'grid', gap: 12, marginTop: 20 }}>
        {entries.map((e, i) => (
          <div className="queue-item rise" key={e.id} style={{ animationDelay: `${i * 60}ms` }}>
            <div className="queue-pos">{e.position ?? '—'}</div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <strong>{e.eventTitle || e.categoryName || 'Waitlist entry'}</strong>
              <div className="muted" style={{ fontSize: 13.5 }}>
                {[e.categoryName, e.quantity ? `${e.quantity} seat${e.quantity > 1 ? 's' : ''}` : null,
                  e.showDateTime ? dateTime(e.showDateTime) : null].filter(Boolean).join(' · ')}
              </div>
              <div className="faint" style={{ marginTop: 4 }}>
                Joined {dateTime(e.createdAt)} · we email a claim link the moment a seat frees up
              </div>
            </div>
            {e.status && <span className="badge">{e.status}</span>}
            <button className="ghost tiny" disabled={busyId === e.id} onClick={() => leave(e.id)}>
              {busyId === e.id ? 'Leaving…' : 'Leave queue'}
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
