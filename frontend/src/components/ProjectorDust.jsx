import { useEffect, useRef } from 'react';

/**
 * Ambient background: dust drifting through a projector beam.
 *
 * Motes are given a z depth and projected with a simple perspective divide, so
 * near ones are larger, brighter and drift faster — real parallax rather than
 * a flat particle field. Costs one canvas and no dependencies.
 *
 * Skipped entirely when the user prefers reduced motion.
 */
export default function ProjectorDust() {
  const ref = useRef(null);

  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const canvas = ref.current;
    const ctx = canvas.getContext('2d');
    let raf;
    let w = 0;
    let h = 0;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);

    const COUNT = window.innerWidth < 700 ? 42 : 90;
    const FOCAL = 420;
    const motes = Array.from({ length: COUNT }, () => spawn(true));

    function spawn(anywhere) {
      return {
        x: (Math.random() - 0.5) * 1400,
        y: (Math.random() - 0.5) * 900,
        z: anywhere ? Math.random() * 900 + 60 : 960,
        drift: Math.random() * 0.5 + 0.18,
        wobble: Math.random() * Math.PI * 2,
        hue: Math.random() < 0.35 ? 40 : 265,   // amber motes among violet
      };
    }

    function resize() {
      w = canvas.clientWidth;
      h = canvas.clientHeight;
      canvas.width = w * dpr;
      canvas.height = h * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    function frame(t) {
      ctx.clearRect(0, 0, w, h);
      for (const m of motes) {
        m.z -= m.drift * 1.6;
        m.wobble += 0.006;
        if (m.z < 40) Object.assign(m, spawn(false));

        const scale = FOCAL / m.z;
        const px = w / 2 + (m.x + Math.sin(m.wobble) * 40) * scale;
        const py = h / 2 + (m.y + Math.cos(m.wobble * 0.7) * 30) * scale;
        if (px < -30 || px > w + 30 || py < -30 || py > h + 30) continue;

        const r = Math.max(0.4, scale * 1.5);
        const alpha = Math.min(0.5, scale * 0.34);
        ctx.beginPath();
        ctx.arc(px, py, r, 0, Math.PI * 2);
        ctx.fillStyle = `hsla(${m.hue}, 90%, 78%, ${alpha})`;
        ctx.fill();
      }
      raf = requestAnimationFrame(frame);
    }

    resize();
    window.addEventListener('resize', resize);
    raf = requestAnimationFrame(frame);

    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener('resize', resize);
    };
  }, []);

  return <canvas ref={ref} className="dust" aria-hidden="true" />;
}
