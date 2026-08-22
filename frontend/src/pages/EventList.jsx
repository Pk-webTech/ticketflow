import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Events } from '../lib/api.js';
import { dateTime } from '../lib/format.js';

/** Browse + filter. Search hits the ElasticSearch-backed endpoint. */
export default function EventList() {
  const [events, setEvents] = useState([]);
  const [filters, setFilters] = useState({ q: '', city: '', eventType: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true); setError('');
    try {
      const params = Object.fromEntries(Object.entries(filters).filter(([, v]) => v));
      const result = Object.keys(params).length
        ? await Events.search(params)
        : await Events.list().catch(() => Events.search({}));
      setEvents(Array.isArray(result) ? result : result?.content ?? []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);   // eslint-disable-line react-hooks/exhaustive-deps

  const set = (key) => (e) => setFilters({ ...filters, [key]: e.target.value });

  return (
    <div>
      <h2>What's on</h2>

      <div className="card" style={{ display: 'grid', gap: 12, gridTemplateColumns: '2fr 1fr 1fr auto', alignItems: 'end' }}>
        <div>
          <label>Search</label>
          <input value={filters.q} onChange={set('q')} placeholder="Movie or concert name" />
        </div>
        <div>
          <label>City</label>
          <input value={filters.city} onChange={set('city')} placeholder="Chennai" />
        </div>
        <div>
          <label>Type</label>
          <select value={filters.eventType} onChange={set('eventType')}>
            <option value="">All</option>
            <option value="MOVIE">Movies</option>
            <option value="CONCERT">Concerts</option>
          </select>
        </div>
        <button onClick={load}>Search</button>
      </div>

      {error && <div className="error">{error}</div>}
      {loading && <p className="muted" style={{ marginTop: 24 }}>Loading events…</p>}
      {!loading && events.length === 0 && (
        <p className="muted" style={{ marginTop: 24 }}>No events match that search.</p>
      )}

      <div className="grid cols-3" style={{ marginTop: 20 }}>
        {events.map((event) => (
          <EventCard key={event.id ?? event.eventId} event={event} />
        ))}
      </div>
    </div>
  );
}

function EventCard({ event }) {
  const [shows, setShows] = useState(null);
  const eventId = event.id ?? event.eventId;

  const loadShows = async () => {
    if (shows) return;
    try {
      setShows(await Events.shows(eventId));
    } catch {
      setShows([]);
    }
  };

  return (
    <div className="card">
      <div style={{ fontSize: 17, fontWeight: 600 }}>{event.title ?? event.eventTitle}</div>
      <div className="muted" style={{ marginTop: 4 }}>
        {event.type} {event.language ? '· ' + event.language : ''}
      </div>
      {event.description && (
        <p className="muted" style={{ fontSize: 13, marginTop: 10 }}>
          {event.description.slice(0, 110)}{event.description.length > 110 ? '…' : ''}
        </p>
      )}

      {!shows && (
        <button className="ghost" style={{ marginTop: 14 }} onClick={loadShows}>
          See showtimes
        </button>
      )}

      {shows && shows.length === 0 && (
        <p className="muted" style={{ marginTop: 12, fontSize: 13 }}>No upcoming shows.</p>
      )}

      {shows && shows.length > 0 && (
        <div style={{ marginTop: 14, display: 'grid', gap: 8 }}>
          {shows.map((show) => (
            <Link key={show.id} to={`/shows/${show.id}`} className="btn" style={{ textAlign: 'center' }}>
              {dateTime(show.showDateTime)}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
