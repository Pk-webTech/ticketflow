import { useEffect, useState } from 'react';
import { Venues } from '../lib/api.js';

const unwrap = (r) => (Array.isArray(r) ? r : r?.content ?? []);

const blank = { name: '', city: '', address: '' };
const blankCategory = { name: '', displayColor: '#4de3d0', defaultPrice: '', displayOrder: 1 };
const blankBlock = { categoryId: '', rowLabel: 'A', seatCount: 10, section: 'Main' };

/** Admin-only: venues, seat categories, and bulk seat-layout generation. */
export default function AdminVenues() {
  const [venues, setVenues] = useState([]);
  const [selected, setSelected] = useState(null);
  const [categories, setCategories] = useState([]);
  const [seatCount, setSeatCount] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);

  const [venueForm, setVenueForm] = useState(blank);
  const [catForm, setCatForm] = useState(blankCategory);
  const [blocks, setBlocks] = useState([{ ...blankBlock }]);

  const load = async () => {
    setLoading(true);
    try {
      setVenues(unwrap(await Venues.list()));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const selectVenue = async (venue) => {
    setSelected(venue);
    setError(''); setNotice('');
    try {
      const [cats, seats] = await Promise.all([
        Venues.categories(venue.id).catch(() => []),
        Venues.seats(venue.id).catch(() => []),
      ]);
      setCategories(unwrap(cats));
      setSeatCount(unwrap(seats).length);
      setBlocks([{ ...blankBlock, categoryId: unwrap(cats)[0]?.id ?? '' }]);
    } catch (err) {
      setError(err.message);
    }
  };

  const createVenue = async (e) => {
    e.preventDefault();
    setError(''); setNotice('');
    try {
      const v = await Venues.create(venueForm);
      setVenueForm(blank);
      setNotice('Venue created. Add seat categories, then generate its layout.');
      await load();
      selectVenue(v);
    } catch (err) { setError(err.message); }
  };

  const createCategory = async (e) => {
    e.preventDefault();
    if (!selected) return;
    setError(''); setNotice('');
    try {
      await Venues.createCategory(selected.id, {
        ...catForm,
        defaultPrice: Number(catForm.defaultPrice),
        displayOrder: Number(catForm.displayOrder),
      });
      setCatForm(blankCategory);
      setNotice('Category added.');
      selectVenue(selected);
    } catch (err) { setError(err.message); }
  };

  const updateBlock = (i, patch) =>
    setBlocks((bs) => bs.map((b, idx) => (idx === i ? { ...b, ...patch } : b)));

  const addBlock = () => setBlocks((bs) => [...bs, { ...blankBlock, categoryId: categories[0]?.id ?? '' }]);
  const removeBlock = (i) => setBlocks((bs) => bs.filter((_, idx) => idx !== i));

  const generateSeats = async (e) => {
    e.preventDefault();
    if (!selected) return;
    setError(''); setNotice('');
    try {
      await Venues.createSeats(selected.id, {
        blocks: blocks.map((b) => ({
          categoryId: b.categoryId,
          rowLabel: b.rowLabel,
          seatCount: Number(b.seatCount),
          section: b.section,
        })),
      });
      setNotice('Seat layout generated.');
      selectVenue(selected);
    } catch (err) { setError(err.message); }
  };

  return (
    <div>
      <div className="eyebrow">Admin</div>
      <h2>Venues & seat layouts</h2>

      {error && <div className="error">{error}</div>}
      {notice && <div className="notice">{notice}</div>}

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(220px, 300px) 1fr', gap: 22, marginTop: 20, alignItems: 'start' }}>
        <div>
          <div className="card">
            <h3>New venue</h3>
            <form onSubmit={createVenue} style={{ marginTop: 14 }}>
              <label className="field">
                <span>Name</span>
                <input required value={venueForm.name}
                  onChange={(e) => setVenueForm((f) => ({ ...f, name: e.target.value }))} placeholder="Rex Cinemas" />
              </label>
              <label className="field">
                <span>City</span>
                <input required value={venueForm.city}
                  onChange={(e) => setVenueForm((f) => ({ ...f, city: e.target.value }))} placeholder="Chennai" />
              </label>
              <label className="field">
                <span>Address</span>
                <input value={venueForm.address}
                  onChange={(e) => setVenueForm((f) => ({ ...f, address: e.target.value }))} placeholder="Optional" />
              </label>
              <button type="submit" style={{ width: '100%' }}>Create venue</button>
            </form>
          </div>

          <div className="divider" />

          <h3 style={{ marginBottom: 10 }}>Venues</h3>
          {loading && <div className="skeleton" style={{ height: 80 }} />}
          <div style={{ display: 'grid', gap: 8 }}>
            {venues.map((v) => (
              <button
                key={v.id}
                className={`ghost tiny`}
                style={{
                  textAlign: 'left', width: '100%',
                  borderColor: selected?.id === v.id ? 'var(--amber)' : undefined,
                  color: selected?.id === v.id ? 'var(--amber)' : undefined,
                }}
                onClick={() => selectVenue(v)}
              >
                {v.name}<br /><span className="faint">{v.city}</span>
              </button>
            ))}
          </div>
        </div>

        <div>
          {!selected && (
            <div className="empty">
              <h3>Pick a venue</h3>
              <p>Select one from the list, or create a new one to get started.</p>
            </div>
          )}

          {selected && (
            <>
              <div className="card">
                <div className="panel-head" style={{ marginBottom: 0 }}>
                  <div>
                    <h3>{selected.name}</h3>
                    <p className="muted" style={{ marginTop: 4 }}>{selected.city}</p>
                  </div>
                  <span className="badge amber">{seatCount ?? 0} seats laid out</span>
                </div>
              </div>

              <div className="card" style={{ marginTop: 16 }}>
                <h3>Seat categories</h3>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', margin: '14px 0' }}>
                  {categories.length === 0 && <span className="faint">No categories yet.</span>}
                  {categories.map((c) => (
                    <span className="badge" key={c.id} style={{ borderColor: c.displayColor, color: c.displayColor }}>
                      {c.name} · {c.defaultPrice}
                    </span>
                  ))}
                </div>
                <form onSubmit={createCategory}>
                  <div className="form-row" style={{ gridTemplateColumns: '1.5fr 1fr 1fr 1fr' }}>
                    <label className="field">
                      <span>Name</span>
                      <input required value={catForm.name}
                        onChange={(e) => setCatForm((f) => ({ ...f, name: e.target.value }))} placeholder="Premium" />
                    </label>
                    <label className="field">
                      <span>Colour</span>
                      <input type="color" value={catForm.displayColor}
                        onChange={(e) => setCatForm((f) => ({ ...f, displayColor: e.target.value }))} />
                    </label>
                    <label className="field">
                      <span>Default price</span>
                      <input type="number" min="0" step="0.01" required value={catForm.defaultPrice}
                        onChange={(e) => setCatForm((f) => ({ ...f, defaultPrice: e.target.value }))} />
                    </label>
                    <label className="field">
                      <span>Order</span>
                      <input type="number" min="1" value={catForm.displayOrder}
                        onChange={(e) => setCatForm((f) => ({ ...f, displayOrder: e.target.value }))} />
                    </label>
                  </div>
                  <button type="submit">Add category</button>
                </form>
              </div>

              <div className="card" style={{ marginTop: 16 }}>
                <h3>Generate seat layout</h3>
                <p className="muted" style={{ marginTop: 4, marginBottom: 16 }}>
                  Add one block per row of seating. Each block is one row, all in one category.
                </p>
                <form onSubmit={generateSeats}>
                  {blocks.map((b, i) => (
                    <div className="form-row" key={i} style={{ gridTemplateColumns: '1fr 0.7fr 0.7fr 1fr auto', alignItems: 'end', marginBottom: 4 }}>
                      <label className="field">
                        <span>Category</span>
                        <select required value={b.categoryId} onChange={(e) => updateBlock(i, { categoryId: e.target.value })}>
                          <option value="">Select…</option>
                          {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                        </select>
                      </label>
                      <label className="field">
                        <span>Row label</span>
                        <input required value={b.rowLabel} onChange={(e) => updateBlock(i, { rowLabel: e.target.value })} />
                      </label>
                      <label className="field">
                        <span>Seats</span>
                        <input type="number" min="1" required value={b.seatCount}
                          onChange={(e) => updateBlock(i, { seatCount: e.target.value })} />
                      </label>
                      <label className="field">
                        <span>Section</span>
                        <input value={b.section} onChange={(e) => updateBlock(i, { section: e.target.value })} />
                      </label>
                      <button type="button" className="ghost tiny" style={{ marginBottom: 14 }}
                        onClick={() => removeBlock(i)} disabled={blocks.length === 1}>Remove</button>
                    </div>
                  ))}
                  <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
                    <button type="button" className="ghost" onClick={addBlock}>Add row</button>
                    <button type="submit" disabled={!categories.length}>Generate layout</button>
                  </div>
                </form>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
