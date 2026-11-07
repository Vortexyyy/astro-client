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
