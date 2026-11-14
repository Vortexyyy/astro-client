import { useEffect, useRef } from 'react';

interface Star {
  x: number;
  y: number;
  z: number;
  twinkle: number;
}

/** Frame cap for the background: smooth enough for a slow drift, half the work of 60 Hz. */
const FPS = 30;
/** Screen area (CSS px²) per star: Orbit keeps the field sparse. */
const AREA_PER_STAR = 9000;

/**
 * Slow, sparse starfield behind the whole launcher. It only animates while the launcher window is
 * visible and focused, so it never competes with a running game for CPU or GPU, and it stops
 * entirely when reduced motion is requested.
 */
export default function Starfield() {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current!;
    const ctx = canvas.getContext('2d')!;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let stars: Star[] = [];
    let frame = 0;
    let last = 0;

    const paint = (t: number, dt: number) => {
      const w = window.innerWidth;
      const h = window.innerHeight;
      ctx.clearRect(0, 0, w, h);
      ctx.fillStyle = '#dde3f0';
      for (const s of stars) {
        s.x -= s.z * 0.0036 * dt; // ~0.06 px per 60 Hz frame, independent of the frame rate
        if (s.x < 0) s.x += w;
        ctx.globalAlpha = 0.25 + 0.55 * s.z * (0.75 + 0.25 * Math.sin(t / 900 + s.twinkle));
        const size = s.z > 0.85 ? 2 : 1;
        ctx.fillRect(Math.round(s.x), Math.round(s.y), size, size);
      }
      ctx.globalAlpha = 1;
    };

    const resize = () => {
      const dpr = window.devicePixelRatio || 1;
      canvas.width = window.innerWidth * dpr;
      canvas.height = window.innerHeight * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const count = Math.round((window.innerWidth * window.innerHeight) / AREA_PER_STAR);
      stars = Array.from({ length: count }, () => ({
        x: Math.random() * window.innerWidth,
        y: Math.random() * window.innerHeight,
        z: Math.random() * 0.8 + 0.2,
        twinkle: Math.random() * Math.PI * 2
      }));
      paint(performance.now(), 0);
    };

    const active = () => !reduced && document.visibilityState === 'visible' && document.hasFocus();

    const loop = (t: number) => {
      frame = 0;
      if (!active()) return; // resumed by the focus/visibility listeners below
      if (t - last >= 1000 / FPS - 1) {
        paint(t, last === 0 ? 0 : Math.min(100, t - last));
        last = t;
      }
      frame = requestAnimationFrame(loop);
    };

    const wake = () => {
      if (frame === 0 && active()) {
        last = 0;
        frame = requestAnimationFrame(loop);
      }
    };

    resize();
    window.addEventListener('resize', resize);
    window.addEventListener('focus', wake);
    document.addEventListener('visibilitychange', wake);
    wake();
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('resize', resize);
      window.removeEventListener('focus', wake);
      document.removeEventListener('visibilitychange', wake);
    };
  }, []);

  return <canvas ref={ref} className="starfield" aria-hidden="true" />;
}
