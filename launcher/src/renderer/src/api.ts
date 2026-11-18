import type { AstroApi } from '@shared/types';

declare global {
  interface Window {
    astro: AstroApi;
  }
}

export const astro = window.astro;

/** Electron prefixes IPC errors with "Error invoking remote method '…': Error: " — strip that for display. */
export function errorText(err: unknown): string {
  const msg = err instanceof Error ? err.message : String(err);
  return msg.replace(/^Error invoking remote method '[^']+': (Error: )?/, '');
}

export function formatCount(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}k`;
  return String(n);
}

export function timeAgo(when: number): string {
  const minutes = Math.round((Date.now() - when) / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  return days === 1 ? 'yesterday' : `${days} days ago`;
}

/** 0 min · 42 min · 3.4 h · 120 h */
export function formatPlaytime(ms: number): string {
  const minutes = Math.floor(ms / 60000);
  if (minutes < 60) return `${minutes} min`;
  const hours = ms / 3_600_000;
  return `${hours < 100 ? Math.round(hours * 10) / 10 : Math.round(hours)} h`;
}
