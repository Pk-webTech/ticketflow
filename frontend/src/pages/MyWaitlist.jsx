import { useEffect, useState } from 'react';
import { Waitlist } from '../lib/api.js';

const EXPLAIN = {
  ACTIVE: 'Waiting — we\u2019ll email you if a seat frees up.',
  OFFERED: 'A seat has been offered to you. Check your email for the claim link.',
  CONVERTED: 'You claimed an offer.',
  EXPIRED: 'Your offer window lapsed and the seats moved to the next customer.',
  CANCELLED: 'You left this waitlist.',
};

export default function MyWaitlist() {
  const [entries, setEntries] = useState([]);
  const [error, setError] = useState('');

  const load = async () => {
    try {
      setEntries(await Waitlist.mine());
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => { load(); }, []);

  const leave = async (id) => {
    try {
      await Waitlist.leave(id);
      await load();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div>
      <h2>My waitlist</h2>
      {error && <div className="error">{error}</div>}
      {entries.length === 0 && <p className="muted">You're not on any waitlists.</p>}

      <div className="grid" style={{ marginTop: 16 }}>
        {entries.map((e) => (
          <div className="card" key={e.id}>
            <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
              <strong>{e.categoryName ?? 'Seat category'}</strong>
              <span className="badge confirmed">{e.status}</span>
              {e.position != null && (
                <span style={{ marginLeft: 'auto', color: 'var(--warn)', fontWeight: 700 }}>
                  #{e.position} in queue
                </span>
              )}
            </div>
            <p className="muted" style={{ marginTop: 8, fontSize: 13 }}>{EXPLAIN[e.status]}</p>
            <div className="muted" style={{ fontSize: 13 }}>Requested {e.quantity} seat(s)</div>

            {e.status === 'ACTIVE' && (
              <button className="ghost" style={{ marginTop: 12 }} onClick={() => leave(e.id)}>
                Leave waitlist
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
