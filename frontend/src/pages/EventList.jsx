import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import TiltCard from '../components/TiltCard.jsx';
import { Events, Venues } from '../lib/api.js';
import { dayOnly, timeOnly, hueFrom } from '../lib/format.js';

const unwrap = (r) => (Array.isArray(r) ? r : r?.content ?? []);

/**
 * Browse screen.
 *
 * Search hits the ElasticSearch-backed endpoint; when the query is empty we
 * fall back to the plain list so the page still works if ES is down. Shows are
 * fetched per event and cached, because there is no batch endpoint for them.
 */
export default function EventList() {
  const [events, setEvents] = useState([]);
  const [showsByEvent, setShowsByEvent] = useState({});
  const [venues, setVenues] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [q, setQ] = useState('');
  const [city, setCity] = useState('');
  const [type, setType] = useState('');

  const loadEvents = async (params) => {
    setLoading(true);
    setError('');
    try {
      const useSearch = params && (params.q || params.city || params.eventType);
      const data = useSearch
        ? await Events.search(params).catch(() => Events.list())
        : await Events.list();
      const list = unwrap(data);
      setEvents(list);
      loadShows(list);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const loadShows = async (list) => {
    const entries = await Promise.all(
      list.map(async (e) => [e.id, unwrap(await Events.shows(e.id).catch(() => []))]),
    );
    setShowsByEvent(Object.fromEntries(entries));
  };

  useEffect(() => {
    loadEvents();
    Venues.list()
      .then((v) => setVenues(Object.fromEntries(unwrap(v).map((x) => [x.id, x]))))
      .catch(() => {});
  }, []);

  const search = (e) => {
    e.preventDefault();
    loadEvents({ q, city, eventType: type });
  };

  const cities = useMemo(
    () => [...new Set(Object.values(venues).map((v) => v.city).filter(Boolean))].sort(),
    [venues],
  );

  return (
    <div>
      <header className="hero">
        <div className="eyebrow">Live seat inventory · updated by websocket</div>
        <h1 className="hero-title">
          Watch the room fill up <span className="glow">in real time.</span>
        </h1>
        <p className="lead">
          Pick your seats and they lock instantly — for everyone. Hold them for ten minutes
          while you check out, and get a QR ticket in your inbox the moment you're done.
        </p>

        {/* The thesis, stated visually: a raked house with the lights coming up. */}
        <div className="house-stage" aria-hidden="true">
          <div className="house-floor">
            {[9, 11, 13, 13].map((n, r) => (
              <div className="house-row" key={r}>
                {Array.from({ length: n }, (_, i) => (
                  <span
                    className="house-seat"
                    key={i}
                    style={{ animationDelay: `${(r * 0.35 + i * 0.12).toFixed(2)}s` }}
                  />
                ))}
              </div>
            ))}
          </div>
        </div>
      </header>

      <form className="searchbar" onSubmit={search}>
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search films, concerts, artists…"
          aria-label="Search events"
        />
        <select value={city} onChange={(e) => setCity(e.target.value)} aria-label="City">
          <option value="">Any city</option>
          {cities.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <select value={type} onChange={(e) => setType(e.target.value)} aria-label="Type">
          <option value="">Any type</option>
          <option value="MOVIE">Movie</option>
          <option value="CONCERT">Concert</option>
          <option value="SPORTS">Sports</option>
          <option value="THEATRE">Theatre</option>
        </select>
        <button type="submit">Search</button>
      </form>

      {error && <div className="error">{error}</div>}

      {loading && (
        <div className="grid">
          {[0, 1, 2, 3, 4, 5].map((i) => <div className="skeleton" key={i} />)}
        </div>
      )}

      {!loading && events.length === 0 && (
        <div className="empty">
          <h3>Nothing on sale yet</h3>
          <p>Once an organiser publishes a show, it appears here. Try clearing the filters.</p>
        </div>
      )}

      <div className="grid">
        {events.map((ev, i) => {
          const shows = showsByEvent[ev.id] ?? [];
          return (
            <TiltCard
              key={ev.id}
              className="event-card rise"
              style={{ '--hue': hueFrom(ev.id ?? ev.title), animationDelay: `${i * 60}ms` }}
            >
              <span className="kind">{ev.type || ev.eventType || 'Event'}</span>
              <h3>{ev.title}</h3>
              <div className="meta">
                {[ev.language, ev.durationMinutes && `${ev.durationMinutes} min`]
                  .filter(Boolean).join(' · ')}
              </div>
              {ev.description && <p className="desc">{ev.description}</p>}

              <div className="shows">
                {shows.length === 0
                  ? <span className="faint">No dates scheduled</span>
                  : shows.slice(0, 6).map((s) => (
                    <Link className="showchip" key={s.id} to={`/shows/${s.id}`}>
                      {timeOnly(s.showDateTime)}
                      <small>{dayOnly(s.showDateTime)}{venues[s.venueId] ? ` · ${venues[s.venueId].name}` : ''}</small>
                    </Link>
                  ))}
              </div>
            </TiltCard>
          );
        })}
      </div>
    </div>
  );
}
