import { useEffect, useState } from 'react';
import { Events, Venues, Bookings } from '../lib/api.js';
import { money, dateTime } from '../lib/format.js';

/** Create listings and shows; see bookings + revenue per event. */
export default function OrganiserDashboard() {
  const [events, setEvents] = useState([]);
  const [venues, setVenues] = useState([]);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const [eventForm, setEventForm] = useState({ title: '', type: 'MOVIE', description: '', language: '', durationMinutes: 120 });
  const [showForm, setShowForm] = useState({ eventId: '', venueId: '', startsAt: '', pricing: [] });

  const load = async () => {
    try {
      const [ev, vn] = await Promise.all([
        Events.mine().catch(() => Events.list().catch(() => [])),
        Venues.list().catch(() => []),
      ]);
      setEvents(Array.isArray(ev) ? ev : ev?.content ?? []);
      setVenues(Array.isArray(vn) ? vn : vn?.content ?? []);
    } catch (err) {
      setError(err.message);
    }
  };

  useEffect(() => { load(); }, []);

  const createEvent = async (e) => {
    e.preventDefault();
    setError(''); setNotice('');
    try {
      await Events.create(eventForm);
      setNotice('Event created.');
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
      setNotice('Show scheduled.');
    } catch (err) { setError(err.message); }
  };

  const loadSummary = async (eventId) => {
    setError('');
    try {
      setSummary(await Bookings.eventSummary(eventId));
    } catch (err) { setError(err.message); }
  };

  return (
    <div>
      <h2>Organiser dashboard</h2>
      {error && <div className="error">{error}</div>}
      {notice && <div className="notice">{notice}</div>}

      <div className="grid cols-2" style={{ marginTop: 16 }}>
        <form className="card" onSubmit={createEvent}>
          <h3 style={{ marginTop: 0 }}>New listing</h3>
          <label>Title</label>
          <input value={eventForm.title} onChange={(e) => setEventForm({ ...eventForm, title: e.target.value })} required />
          <label>Type</label>
          <select value={eventForm.type} onChange={(e) => setEventForm({ ...eventForm, type: e.target.value })}>
            <option value="MOVIE">Movie</option>
            <option value="CONCERT">Concert</option>
          </select>
          <label>Language</label>
          <input value={eventForm.language} onChange={(e) => setEventForm({ ...eventForm, language: e.target.value })} />
          <label>Duration (minutes)</label>
          <input type="number" min="1" value={eventForm.durationMinutes}
                 onChange={(e) => setEventForm({ ...eventForm, durationMinutes: Number(e.target.value) })} />
          <label>Description</label>
          <input value={eventForm.description} onChange={(e) => setEventForm({ ...eventForm, description: e.target.value })} />
          <button style={{ marginTop: 18 }}>Create listing</button>
        </form>

        <form className="card" onSubmit={createShow}>
          <h3 style={{ marginTop: 0 }}>Schedule a show</h3>
          <label>Event</label>
          <select value={showForm.eventId} onChange={(e) => setShowForm({ ...showForm, eventId: e.target.value })} required>
            <option value="">Select…</option>
            {events.map((ev) => <option key={ev.id} value={ev.id}>{ev.title}</option>)}
          </select>

          <label>Venue</label>
          <select value={showForm.venueId} onChange={(e) => onVenueChange(e.target.value)} required>
            <option value="">Select…</option>
            {venues.map((v) => <option key={v.id} value={v.id}>{v.name}</option>)}
          </select>

          <label>Starts at</label>
          <input type="datetime-local" value={showForm.startsAt}
                 onChange={(e) => setShowForm({ ...showForm, startsAt: e.target.value })} required />

          {showForm.pricing.map((p, i) => (
            <div key={p.categoryId}>
              <label>{p.categoryName} price</label>
              <input type="number" min="0" value={p.price} required
                     onChange={(e) => {
                       const next = [...showForm.pricing];
                       next[i] = { ...p, price: e.target.value };
                       setShowForm({ ...showForm, pricing: next });
                     }} />
            </div>
          ))}

          <button style={{ marginTop: 18 }}>Schedule show</button>
        </form>
      </div>

      <h3 style={{ marginTop: 34 }}>Revenue</h3>
      <div className="grid cols-3">
        {events.map((ev) => (
          <div className="card" key={ev.id}>
            <strong>{ev.title}</strong>
            <div className="muted" style={{ fontSize: 13, marginTop: 4 }}>{ev.type}</div>
            <button className="ghost" style={{ marginTop: 12 }} onClick={() => loadSummary(ev.id)}>
              View summary
            </button>
          </div>
        ))}
      </div>

      {summary && (
        <div className="card" style={{ marginTop: 20 }}>
          <h3 style={{ marginTop: 0 }}>Summary</h3>
          <div className="grid cols-3">
            <Stat label="Confirmed bookings" value={summary.confirmedBookings} />
            <Stat label="Cancelled" value={summary.cancelledBookings} />
            <Stat label="Seats sold" value={summary.seatsSold} />
            <Stat label="Gross revenue" value={money(summary.grossRevenue)} />
            <Stat label="Refunded" value={money(summary.refundedAmount)} />
            <Stat label="Net revenue" value={money(summary.netRevenue)} />
          </div>

          {summary.perCategory?.length > 0 && (
            <table style={{ width: '100%', marginTop: 20, borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ color: 'var(--muted)', fontSize: 12, textAlign: 'left' }}>
                  <th style={{ paddingBottom: 8 }}>Category</th>
                  <th style={{ paddingBottom: 8 }}>Seats sold</th>
                  <th style={{ paddingBottom: 8, textAlign: 'right' }}>Revenue</th>
                </tr>
              </thead>
              <tbody>
                {summary.perCategory.map((c) => (
                  <tr key={c.categoryId} style={{ borderTop: '1px solid var(--line)' }}>
                    <td style={{ padding: '8px 0' }}>{c.categoryName}</td>
                    <td style={{ padding: '8px 0' }}>{c.seatsSold}</td>
                    <td style={{ padding: '8px 0', textAlign: 'right' }}>{money(c.revenue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}

function Stat({ label, value }) {
  return (
    <div>
      <div className="muted" style={{ fontSize: 12, textTransform: 'uppercase', letterSpacing: '.6px' }}>{label}</div>
      <div style={{ fontSize: 24, fontWeight: 700, marginTop: 4 }}>{value}</div>
    </div>
  );
}
