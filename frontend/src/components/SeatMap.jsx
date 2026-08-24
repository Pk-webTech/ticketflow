import { useMemo, useState } from 'react';
import { money } from '../lib/format.js';

/**
 * The auditorium — the one place this app spends its boldness.
 *
 * Rows are laid out on a raked floor using CSS `perspective` + `rotateX`, so
 * the map reads the way the room actually looks from the stalls: the screen is
 * upstage, near rows are large, far rows recede. Each seat has a `::before`
 * seat-back, so tilting the floor gives it genuine thickness rather than
 * looking like a tilted spreadsheet.
 *
 * Everything about status comes straight from the backend's SeatStatus:
 *   AVAILABLE | HELD_BY_ME | HELD_BY_OTHERS | BOOKED
 * HELD_BY_ME stays interactive — those are your own seats and you may deselect
 * them. HELD_BY_OTHERS and BOOKED are inert.
 *
 * `changed` is a Set of seatIds that just moved because of a WebSocket frame;
 * they get a one-shot jolt so a customer sees a seat being taken out from
 * under them instead of it silently changing colour.
 */
export default function SeatMap({ seats = [], selected = [], onToggle, disabled, changed }) {
  const [flat, setFlat] = useState(false);
  const selectedSet = useMemo(() => new Set(selected), [selected]);

  const rows = useMemo(() => {
    const byRow = new Map();
    for (const s of seats) {
      const key = s.rowLabel ?? '—';
      if (!byRow.has(key)) byRow.set(key, []);
      byRow.get(key).push(s);
    }
    return [...byRow.entries()]
      .sort((a, b) => String(a[0]).localeCompare(String(b[0]), undefined, { numeric: true }))
      .map(([label, list]) => [
        label,
        list.slice().sort((a, b) => (a.seatNumber ?? 0) - (b.seatNumber ?? 0)),
      ]);
  }, [seats]);

  if (seats.length === 0) {
    return (
      <div className="empty" style={{ marginTop: 26 }}>
        <h3>No seat layout for this show</h3>
        <p className="muted">An admin needs to generate the venue's seat rows before it can sell.</p>
      </div>
    );
  }

  const classFor = (s) => {
    if (selectedSet.has(s.seatId)) return 'selected';
    switch (s.status) {
      case 'BOOKED': return 'booked';
      case 'HELD_BY_OTHERS': return 'held-other';
      case 'HELD_BY_ME': return 'held-mine';
      default: return 'available';
    }
  };

  const inert = (s) => s.status === 'BOOKED' || s.status === 'HELD_BY_OTHERS';

  const labelFor = (s) => s.seatLabel || `${s.rowLabel ?? ''}${s.seatNumber ?? ''}`;

  return (
    <section className="auditorium" aria-label="Seat map">
      <div className="screen" />
      <div className="screen-label">Screen</div>

      <div className={`rake ${flat ? 'flat' : ''}`}>
        <div className="rake-inner">
          {rows.map(([rowLabel, rowSeats]) => {
            const mid = Math.ceil(rowSeats.length / 2);
            return (
              <div className="seat-row" key={rowLabel}>
                <span className="row-label" aria-hidden="true">{rowLabel}</span>
                {rowSeats.map((s, i) => (
                  <Seat
                    key={s.seatId}
                    seat={s}
                    aisle={i === mid && rowSeats.length > 9}
                    cls={classFor(s)}
                    jolt={changed?.has(s.seatId)}
                    label={labelFor(s)}
                    disabled={disabled || inert(s)}
                    onToggle={onToggle}
                  />
                ))}
                <span className="row-label" aria-hidden="true">{rowLabel}</span>
              </div>
            );
          })}
        </div>
      </div>

      <div className="legend">
        <span><i style={{ background: 'rgba(77,227,208,0.5)' }} />Free</span>
        <span><i style={{ background: 'var(--amber)' }} />Yours</span>
        <span><i style={{ background: 'rgba(255,92,122,0.6)' }} />Held by someone else</span>
        <span><i style={{ background: 'rgba(120,108,160,0.35)' }} />Sold</span>
        <button className="link tiny" onClick={() => setFlat((f) => !f)}>
          {flat ? 'Tilt the room' : 'Flatten the view'}
        </button>
      </div>
    </section>
  );
}

function Seat({ seat, cls, jolt, label, disabled, onToggle, aisle }) {
  const priceLine = seat.price !== undefined && seat.price !== null ? ` · ${money(seat.price)}` : '';
  const title = `${label}${seat.categoryName ? ` · ${seat.categoryName}` : ''}${priceLine}`;

  return (
    <>
      {aisle && <span className="aisle" aria-hidden="true" />}
      <button
        type="button"
        className={`seat ${cls} ${jolt ? 'just-changed' : ''}`}
        disabled={disabled}
        aria-label={`Seat ${title}. ${seat.status?.toLowerCase().replace(/_/g, ' ')}`}
        aria-pressed={cls === 'selected'}
        onClick={() => onToggle?.(seat)}
      >
        <span className="seat-tip">{title}</span>
      </button>
    </>
  );
}
