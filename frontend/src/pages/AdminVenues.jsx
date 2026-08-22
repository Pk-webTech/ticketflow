import { useEffect, useState } from 'react';
import { Venues } from '../lib/api.js';

/**
 * Admin: venues → seat categories → seat layout.
 *
 * Seats are created in row-blocks rather than one at a time; a 500-seat
 * cinema would otherwise be 500 requests.
 */
export default function AdminVenues() {
  const [venues, setVenues] = useState([]);
  const [selected, setSelected] = useState(null);
  const [categories, setCategories] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const [venueForm, setVenueForm] = useState({ name: '', city: '', address: '', state: '', postalCode: '' });
  const [catForm, setCatForm] = useState({ name: '', displayColor: '#e0483c', defaultPrice: 250, displayOrder: 1 });
  const [layoutForm, setLayoutForm] = useState({ categoryId: '', rowStart: 'A', rowEnd: 'E', seatsPerRow: 12, section: 'MAIN' });

  const load = async () => {
    try {
      const list = await Venues.list();
      setVenues(Array.isArray(list) ? list : list?.content ?? []);
    } catch (err) { setError(err.message); }
  };

  useEffect(() => { load(); }, []);

  const pick = async (venue) => {
    setSelected(venue); setError('');
    try {
      setCategories(await Venues.categories(venue.id));
    } catch (err) { setError(err.message); }
  };

  const createVenue = async (e) => {
    e.preventDefault(); setError(''); setNotice('');
    try {
      await Venues.create(venueForm);
      setNotice('Venue created.');
      setVenueForm({ name: '', city: '', address: '', state: '', postalCode: '' });
      await load();
    } catch (err) { setError(err.message); }
  };

  const createCategory = async (e) => {
    e.preventDefault(); setError(''); setNotice('');
    try {
      await Venues.createCategory(selected.id, catForm);
      setNotice('Category added.');
      setCatForm({ name: '', displayColor: '#e0483c', defaultPrice: 250, displayOrder: 1 });
      setCategories(await Venues.categories(selected.id));
    } catch (err) { setError(err.message); }
  };

  const createSeats = async (e) => {
    e.preventDefault(); setError(''); setNotice('');
    try {
      // The API takes one block per ROW, so expand the A–E range here.
      const from = layoutForm.rowStart.toUpperCase().charCodeAt(0);
      const to = layoutForm.rowEnd.toUpperCase().charCodeAt(0);
      if (to < from) throw new Error('Row "to" must come after row "from".');

      const blocks = [];
      for (let code = from; code <= to; code++) {
        blocks.push({
          categoryId: layoutForm.categoryId,
          rowLabel: String.fromCharCode(code),
          seatCount: Number(layoutForm.seatsPerRow),
          section: layoutForm.section || 'MAIN',
        });
      }

      await Venues.createSeats(selected.id, { blocks });
      setNotice('Seat rows generated.');
    } catch (err) { setError(err.message); }
  };

  return (
    <div>
      <h2>Venues</h2>
      {error && <div className="error">{error}</div>}
      {notice && <div className="notice">{notice}</div>}

      <div className="grid cols-2" style={{ marginTop: 16 }}>
        <form className="card" onSubmit={createVenue}>
          <h3 style={{ marginTop: 0 }}>New venue</h3>
          <label>Name</label>
          <input value={venueForm.name} onChange={(e) => setVenueForm({ ...venueForm, name: e.target.value })} required />
          <label>City</label>
          <input value={venueForm.city} onChange={(e) => setVenueForm({ ...venueForm, city: e.target.value })} required />
          <label>Address</label>
          <input value={venueForm.address} onChange={(e) => setVenueForm({ ...venueForm, address: e.target.value })} />
          <label>State</label>
          <input value={venueForm.state} onChange={(e) => setVenueForm({ ...venueForm, state: e.target.value })} />
          <label>Postal code</label>
          <input value={venueForm.postalCode} onChange={(e) => setVenueForm({ ...venueForm, postalCode: e.target.value })} />
          <button style={{ marginTop: 18 }}>Create venue</button>
        </form>

        <div className="card">
          <h3 style={{ marginTop: 0 }}>Existing venues</h3>
          {venues.length === 0 && <p className="muted">No venues yet.</p>}
          <div style={{ display: 'grid', gap: 8 }}>
            {venues.map((v) => (
              <button key={v.id} className="ghost" style={{ textAlign: 'left' }} onClick={() => pick(v)}>
                {v.name} — <span style={{ color: 'var(--muted)' }}>{v.city}</span>
              </button>
            ))}
          </div>
        </div>
      </div>

      {selected && (
        <div className="grid cols-2" style={{ marginTop: 20 }}>
          <form className="card" onSubmit={createCategory}>
            <h3 style={{ marginTop: 0 }}>Seat categories · {selected.name}</h3>
            <div className="muted" style={{ fontSize: 13, marginBottom: 10 }}>
              {categories.map((c) => c.name).join(', ') || 'None yet'}
            </div>
            <label>Category name</label>
            <input value={catForm.name} onChange={(e) => setCatForm({ ...catForm, name: e.target.value })}
                   placeholder="Premium" required />
            <label>Display colour</label>
            <input value={catForm.displayColor} onChange={(e) => setCatForm({ ...catForm, displayColor: e.target.value })} />
            <label>Default price</label>
            <input type="number" min="0" value={catForm.defaultPrice}
                   onChange={(e) => setCatForm({ ...catForm, defaultPrice: Number(e.target.value) })} required />
            <label>Display order</label>
            <input type="number" min="1" value={catForm.displayOrder}
                   onChange={(e) => setCatForm({ ...catForm, displayOrder: Number(e.target.value) })} />
            <button style={{ marginTop: 18 }}>Add category</button>
          </form>

          <form className="card" onSubmit={createSeats}>
            <h3 style={{ marginTop: 0 }}>Generate seat rows</h3>
            <label>Category</label>
            <select value={layoutForm.categoryId} required
                    onChange={(e) => setLayoutForm({ ...layoutForm, categoryId: e.target.value })}>
              <option value="">Select…</option>
              {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
              <div>
                <label>Row from</label>
                <input value={layoutForm.rowStart} maxLength={2}
                       onChange={(e) => setLayoutForm({ ...layoutForm, rowStart: e.target.value.toUpperCase() })} />
              </div>
              <div>
                <label>Row to</label>
                <input value={layoutForm.rowEnd} maxLength={2}
                       onChange={(e) => setLayoutForm({ ...layoutForm, rowEnd: e.target.value.toUpperCase() })} />
              </div>
              <div>
                <label>Per row</label>
                <input type="number" min="1" max="40" value={layoutForm.seatsPerRow}
                       onChange={(e) => setLayoutForm({ ...layoutForm, seatsPerRow: e.target.value })} />
              </div>
            </div>
            <button style={{ marginTop: 18 }}>Generate seats</button>
          </form>
        </div>
      )}
    </div>
  );
}
