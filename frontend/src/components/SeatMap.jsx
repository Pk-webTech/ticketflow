import { useMemo } from 'react';

/**
 * Renders the venue layout as a grid of rows.
 *
 * seat-hold-service distinguishes HELD_BY_ME from HELD_BY_OTHERS, which is
 * exactly what the UI needs: a seat this customer is holding stays interactive
 * (they can release it), while someone else's hold is inert.
 *
 * Status precedence: BOOKED beats any hold. A stale hold key on a seat that has
 * since been booked must render as booked — showing it as merely "held" would
 * imply it's about to free up, which it never will.
 */
export default function SeatMap({ seats, selected, onToggle, disabled }) {
  const rows = useMemo(() => {
    const grouped = new Map();
    for (const seat of seats) {
      const key = seat.rowLabel ?? '?';
      if (!grouped.has(key)) grouped.set(key, []);
      grouped.get(key).push(seat);
    }
    return [...grouped.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([label, list]) => [label, list.sort((a, b) => (a.seatNumber ?? 0) - (b.seatNumber ?? 0))]);
  }, [seats]);

  const isSelected = (seat) => selected.includes(seat.seatId);

  const classFor = (seat) => {
    if (seat.status === 'BOOKED') return 'seat booked unavailable';
    if (isSelected(seat) || seat.status === 'HELD_BY_ME') return 'seat selected';
    if (seat.status === 'HELD_BY_OTHERS') return 'seat held unavailable';
    return 'seat';
  };

  const isPickable = (seat) =>
    seat.status === 'AVAILABLE' || seat.status === 'HELD_BY_ME' || isSelected(seat);

  return (
    <div className="seatmap">
      <div className="screen" />
      <div className="screen-label">SCREEN / STAGE</div>

      {rows.map(([rowLabel, rowSeats]) => (
        <div className="seat-row" key={rowLabel}>
          <span className="row-label">{rowLabel}</span>
          {rowSeats.map((seat) => (
            <button
              key={seat.seatId}
              className={classFor(seat)}
              title={`${rowLabel}${seat.seatNumber} · ${seat.categoryName ?? ''} · ${seat.status}`}
              disabled={disabled || !isPickable(seat)}
              onClick={() => onToggle(seat)}
              style={{ background: undefined }}
            >
              {seat.seatNumber}
            </button>
          ))}
        </div>
      ))}

      <div className="legend">
        <span><i className="swatch" style={{ background: 'var(--surface-2)' }} /> Available</span>
        <span><i className="swatch" style={{ background: 'var(--accent)' }} /> Your selection</span>
        <span><i className="swatch" style={{ background: '#4a3a12' }} /> Held by someone else</span>
        <span><i className="swatch" style={{ background: '#2a2f39' }} /> Booked</span>
      </div>
    </div>
  );
}
