import { useRef } from 'react';

/**
 * Pointer-tracked 3D tilt. Writes the rotation straight onto the node's style
 * (no state, no re-render per mousemove) and feeds --mx/--my to the CSS light
 * sweep so the highlight follows the cursor. Touch devices simply never fire
 * these handlers and get the flat card, which is the right outcome.
 */
export default function TiltCard({ children, className = '', max = 8, style, ...rest }) {
  const ref = useRef(null);

  const onMove = (e) => {
    const el = ref.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const px = (e.clientX - r.left) / r.width;
    const py = (e.clientY - r.top) / r.height;
    el.style.transform =
      `rotateY(${(px - 0.5) * max * 2}deg) rotateX(${(0.5 - py) * max * 2}deg) translateZ(6px)`;
    el.style.setProperty('--mx', `${px * 100}%`);
    el.style.setProperty('--my', `${py * 100}%`);
  };

  const reset = () => {
    const el = ref.current;
    if (el) el.style.transform = '';
  };

  return (
    <div className="tilt-wrap" onMouseMove={onMove} onMouseLeave={reset}>
      <div ref={ref} className={className} style={style} {...rest}>{children}</div>
    </div>
  );
}
