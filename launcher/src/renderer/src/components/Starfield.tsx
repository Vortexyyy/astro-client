import { useEffect, useRef } from 'react';

interface Star {
  x: number;
  y: number;
  z: number;
  twinkle: number;
}

/** Slow parallax starfield behind the whole launcher. Pauses when reduced motion is requested. */
export default function Starfield() {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current!;
    const ctx = canvas.getContext('2d')!;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let stars: Star[] = [];
    let frame = 0;

    const resize = () => {
      const dpr = window.devicePixelRatio || 1;
      canvas.width = window.innerWidth * dpr;
      canvas.height = window.innerHeight * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const count = Math.round((window.innerWidth * window.innerHeight) / 4500);
      stars = Array.from({ length: count }, () => ({
        x: Math.random() * window.innerWidth,
        y: Math.random() * window.innerHeight,
        z: Math.random() * 0.8 + 0.2,
        twinkle: Math.random() * Math.PI * 2
      }));
    };

    const draw = (t: number) => {
      const w = window.innerWidth;
      const h = window.innerHeight;
      ctx.clearRect(0, 0, w, h);
      for (const s of stars) {
        if (!reduced) {
          s.x -= s.z * 0.06;
          if (s.x < 0) s.x += w;
        }
        const a = 0.35 + 0.65 * s.z * (0.75 + 0.25 * Math.sin(t / 900 + s.twinkle));
        ctx.fillStyle = `rgba(${200 + 55 * s.z}, ${210 + 45 * s.z}, 255, ${a})`;
        ctx.fillRect(s.x, s.y, s.z * 1.8, s.z * 1.8);
      }
      if (!reduced) frame = requestAnimationFrame(draw);
    };

    resize();
    window.addEventListener('resize', resize);
    frame = requestAnimationFrame(draw);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('resize', resize);
    };
  }, []);

  return <canvas ref={ref} className="starfield" aria-hidden="true" />;
}
