import { useEffect, useState } from 'react';
import { Events, Venues, Bookings } from '../lib/api.js';
import { money, dateTime } from '../lib/format.js';

const unwrap = (r) => (Array.isArray(r) ? r : r?.content ?? []);

/** Create listings and shows; see bookings + revenue per event. */
export default function OrganiserDashboard() {
  const [events, setEvents] = useState([]);
  const [venues, setVenues] = useState([]);
  const [summary, setSummary] = useState(null);
  const [summaryFor, setSummaryFor] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState('events');

  const [eventForm, setEventForm] = useState({
    title: '', type: 'MOVIE', description: '', language: '', durationMinutes: 120,
  });
  const [showForm, setShowForm] = useState({ eventId: '', venueId: '', startsAt: '', pricing: [] });

  const load = async () => {
    setLoading(true);
    try {
      const [ev, vn] = await Promise.all([
        Events.mine().catch(() => Events.list().catch(() => [])),
        Venues.list().catch(() => []),
      ]);
      setEvents(unwrap(ev));
      setVenues(unwrap(vn));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const createEvent = async (e) => {
    e.preventDefault();
    setError(''); setNotice('');
    try {
      await Events.create(eventForm);
      setNotice('Event created. You can schedule its first show below.');
      setEventForm({ title: '', type: 'MOVIE', description: '', language: '', durationMinutes: 120 });
      await load();
    } catch (err) { setError(err.message); }
  };

  // Pricing is per seat category, so the form reloads categories when the venue changes.
  const onVenueChange = async (venueId) => {
    setShowForm((f) => ({ ...f, venueId, pricing: [] }));
    if (!venueId) return;
    try {
      const cats = await Venues.categories(venueId);
      setShowForm((f) => ({
        ...f,
        venueId,
        pricing: cats.map((c) => ({ categoryId: c.id, categoryName: c.name, price: '' })),
      }));
    } catch (err) { setError(err.message); }
  };

  const createShow = async (e) => {
    e.preventDefault();
    setError(''); setNotice('');
    try {
      await Events.createShow(showForm.eventId, {
        venueId: showForm.venueId,
        showDateTime: new Date(showForm.startsAt).toISOString(),
        pricing: showForm.pricing.map((p) => ({ categoryId: p.categoryId, price: Number(p.price) })),
      });
      setNotice('Show scheduled and now on sale.');
      setShowForm({ eventId: showForm.eventId, venueId: '', startsAt: '', pricing: [] });
      await load();
    } catch (err) { setError(err.message); }
  };

  const loadSummary = async (eventId) => {
    setSummaryFor(eventId);
    setSummary(null);
    if (!eventId) return;
    try {
      setSummary(await Bookings.eventSummary(eventId));
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div>
      <div className="eyebrow">Your listings</div>
      <h2>Organiser dashboard</h2>

      {error && <div className="error">{error}</div>}
      {notice && <div className="notice">{notice}</div>}

      <div className="tabs" style={{ marginTop: 20 }}>
        <button className={`tab ${tab === 'events' ? 'on' : ''}`} onClick={() => setTab('events')}>New event</button>
        <button className={`tab ${tab === 'shows' ? 'on' : ''}`} onClick={() => setTab('shows')}>Schedule a show</button>
        <button className={`tab ${tab === 'revenue' ? 'on' : ''}`} onClick={() => setTab('revenue')}>Revenue</button>
      </div>

      {tab === 'events' && (
        <div className="card">
          <h3>Create an event</h3>
          <p className="muted" style={{ marginTop: 4, marginBottom: 18 }}>
            An event is the listing — the film, concert or match. Shows are its individual dates.
          </p>
          <form onSubmit={createEvent}>
            <div className="form-row">
              <label className="field">
                <span>Title</span>
                <input required value={eventForm.title}
                  onChange={(e) => setEventForm((f) => ({ ...f, title: e.target.value }))}
                  placeholder="Interstellar — 70mm re-release" />
              </label>
              <label className="field">
                <span>Type</span>
                <select value={eventForm.type} onChange={(e) => setEventForm((f) => ({ ...f, type: e.target.value }))}>
                  <option value="MOVIE">Movie</option>
                  <option value="CONCERT">Concert</option>
                  <option value="SPORTS">Sports</option>
                  <option value="THEATRE">Theatre</option>
                </select>
              </label>
            </div>
            <div className="form-row">
              <label className="field">
                <span>Language</span>
                <input value={eventForm.language}
                  onChange={(e) => setEventForm((f) => ({ ...f, language: e.target.value }))}
                  placeholder="English" />
              </label>
              <label className="field">
                <span>Duration (minutes)</span>
                <input type="number" min="1" value={eventForm.durationMinutes}
                  onChange={(e) => setEventForm((f) => ({ ...f, durationMinutes: Number(e.target.value) }))} />
              </label>
            </div>
            <label className="field">
              <span>Description</span>
              <textarea rows={3} value={eventForm.description}
                onChange={(e) => setEventForm((f) => ({ ...f, description: e.target.value }))}
                placeholder="A line or two customers will see on the listing." />
            </label>
            <button type="submit">Create event</button>
          </form>
        </div>
      )}

      {tab === 'shows' && (
        <div className="card">
          <h3>Schedule a show</h3>
          <p className="muted" style={{ marginTop: 4, marginBottom: 18 }}>
            Pick a venue and set a price per seat category — prices can differ from show to show.
          </p>
          <form onSubmit={createShow}>
            <div className="form-row">
              <label className="field">
                <span>Event</span>
                <select required value={showForm.eventId}
                  onChange={(e) => setShowForm((f) => ({ ...f, eventId: e.target.value }))}>
                  <option value="">Select an event…</option>
                  {events.map((e) => <option key={e.id} value={e.id}>{e.title}</option>)}
                </select>
              </label>
              <label className="field">
                <span>Venue</span>
                <select required value={showForm.venueId} onChange={(e) => onVenueChange(e.target.value)}>
                  <option value="">Select a venue…</option>
                  {venues.map((v) => <option key={v.id} value={v.id}>{v.name}{v.city ? ` — ${v.city}` : ''}</option>)}
                </select>
              </label>
            </div>
            <label className="field">
              <span>Date & time</span>
              <input type="datetime-local" required value={showForm.startsAt}
                onChange={(e) => setShowForm((f) => ({ ...f, startsAt: e.target.value }))} />
            </label>

            {showForm.pricing.length > 0 && (
              <div className="field">
                <span style={{ display: 'block', fontSize: 12, fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--ink-faint)', marginBottom: 8 }}>
                  Price per category
                </span>
                <div className="form-row">
                  {showForm.pricing.map((p, i) => (
                    <label className="field" key={p.categoryId}>
                      <span>{p.categoryName}</span>
                      <input type="number" min="0" step="0.01" required value={p.price}
                        onChange={(e) => setShowForm((f) => {
                          const pricing = [...f.pricing];
                          pricing[i] = { ...pricing[i], price: e.target.value };
                          return { ...f, pricing };
                        })}
                        placeholder="0.00" />
                    </label>
                  ))}
                </div>
              </div>
            )}

            <button type="submit" disabled={!showForm.pricing.length}>Schedule show</button>
          </form>
        </div>
      )}

      {tab === 'revenue' && (
        <div className="card">
          <h3>Revenue by event</h3>
          <label className="field" style={{ marginTop: 14, maxWidth: 360 }}>
            <span>Event</span>
            <select value={summaryFor} onChange={(e) => loadSummary(e.target.value)}>
              <option value="">Select an event…</option>
              {events.map((e) => <option key={e.id} value={e.id}>{e.title}</option>)}
            </select>
          </label>

          {summary && (
            <div className="stats" style={{ marginTop: 20 }}>
              <div className="stat"><b>{money(summary.totalRevenue ?? summary.revenue ?? 0)}</b><span>Revenue</span></div>
              <div className="stat"><b>{summary.confirmedBookings ?? summary.totalBookings ?? '—'}</b><span>Bookings</span></div>
              <div className="stat"><b>{summary.seatsSold ?? summary.ticketsSold ?? '—'}</b><span>Seats sold</span></div>
              <div className="stat"><b>{summary.cancelledBookings ?? '—'}</b><span>Cancelled</span></div>
            </div>
          )}
        </div>
      )}

      <div className="divider" />

      <h3 style={{ marginBottom: 14 }}>Your events</h3>
      {loading && <div className="skeleton" style={{ height: 100 }} />}
      {!loading && events.length === 0 && <p className="muted">You haven't created an event yet.</p>}
      <div className="grid">
        {events.map((e) => (
          <div className="card" key={e.id}>
            <span className="kind">{e.type || e.eventType}</span>
            <h3 style={{ marginTop: 6 }}>{e.title}</h3>
            <p className="muted" style={{ marginTop: 6, fontSize: 13.5 }}>
              {[e.language, e.durationMinutes && `${e.durationMinutes} min`].filter(Boolean).join(' · ')}
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}
