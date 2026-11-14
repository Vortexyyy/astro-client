import { useEffect, useRef } from 'react';

interface Star {
  x: number;
  y: number;
  z: number;
  twinkle: number;
  /** precomputed so drawing allocates nothing per frame */
  color: string;
  size: number;
}

/** Frame cap for the background: smooth enough for a slow drift, half the work of 60 Hz. */
const FPS = 30;
/** Screen area (CSS px²) per star. */
const AREA_PER_STAR = 4500;
/** Seconds between shooting stars, and how long each one lasts. */
const SHOOTING_STAR_EVERY = 9;
const SHOOTING_STAR_LIFE = 0.9;

/**
 * Slow parallax starfield behind the whole launcher, with the occasional shooting star (the same
 * timing as the in-game title screen). It only animates while the launcher window is visible and
 * focused, so it never competes with a running game for CPU or GPU, and it stays still when reduced
 * motion is requested.
 */
export default function Starfield({ still = false, theme = 'nebula' }: { still?: boolean; theme?: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  /** "r, g, b" of the current accent, read from the CSS tokens when the theme changes */
  const accent = useRef('139, 92, 246');

  useEffect(() => {
    // the parent applies data-theme in its own effect, which runs after this one
    const id = requestAnimationFrame(() => {
      const rgb = getComputedStyle(document.documentElement).getPropertyValue('--accent-rgb').trim();
      if (rgb) accent.current = rgb.split(/\s+/).join(', ');
    });
    return () => cancelAnimationFrame(id);
  }, [theme]);

  useEffect(() => {
    const canvas = ref.current!;
    const ctx = canvas.getContext('2d')!;
    const reduced = still || window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let stars: Star[] = [];
    let frame = 0;
    let last = 0;

    const shootingStar = (t: number, w: number, h: number) => {
      const seconds = t / 1000;
      const index = Math.floor(seconds / SHOOTING_STAR_EVERY);
      const life = (seconds - index * SHOOTING_STAR_EVERY) / SHOOTING_STAR_LIFE;
      if (life >= 1) return;
      // deterministic per streak, like the mod's version
      const r1 = (Math.sin(index * 12.9898) * 43758.5453) % 1;
      const r2 = (Math.sin(index * 78.233) * 12345.6789) % 1;
      const startX = (0.35 + Math.abs(r1) * 0.6) * w;
      const startY = Math.abs(r2) * 0.35 * h;
      const travel = 0.28 * w;
      const headX = startX - life * travel;
      const headY = startY + life * travel * 0.45;
      const tail = 90;
      const grad = ctx.createLinearGradient(headX, headY, headX + tail, headY - tail * 0.45);
      grad.addColorStop(0, `rgba(236, 235, 255, ${0.9 * (1 - life)})`);
      grad.addColorStop(0.35, `rgba(${accent.current}, ${0.45 * (1 - life)})`);
      grad.addColorStop(1, `rgba(${accent.current}, 0)`);
      ctx.strokeStyle = grad;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(headX, headY);
      ctx.lineTo(headX + tail, headY - tail * 0.45);
      ctx.stroke();
    };

    const paint = (t: number, dt: number) => {
      const w = window.innerWidth;
      const h = window.innerHeight;
      ctx.clearRect(0, 0, w, h);
      for (const s of stars) {
        s.x -= s.z * 0.0036 * dt; // ~0.06 px per 60 Hz frame, independent of the frame rate
        if (s.x < 0) s.x += w;
        ctx.globalAlpha = 0.35 + 0.65 * s.z * (0.75 + 0.25 * Math.sin(t / 900 + s.twinkle));
        ctx.fillStyle = s.color;
        ctx.fillRect(s.x, s.y, s.size, s.size);
      }
      ctx.globalAlpha = 1;
      if (!reduced) shootingStar(t, w, h);
    };

    const resize = () => {
      const dpr = window.devicePixelRatio || 1;
      canvas.width = window.innerWidth * dpr;
      canvas.height = window.innerHeight * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const count = Math.round((window.innerWidth * window.innerHeight) / AREA_PER_STAR);
      stars = Array.from({ length: count }, () => {
        const z = Math.random() * 0.8 + 0.2;
        return {
          x: Math.random() * window.innerWidth,
          y: Math.random() * window.innerHeight,
          z,
          twinkle: Math.random() * Math.PI * 2,
          color: `rgb(${Math.round(200 + 55 * z)}, ${Math.round(210 + 45 * z)}, 255)`,
          size: z * 1.8
        };
      });
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
  }, [still]);

  return <canvas ref={ref} className="starfield" aria-hidden="true" />;
}
