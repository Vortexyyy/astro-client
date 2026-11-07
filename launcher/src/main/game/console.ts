import type { ConsoleLine, LogLevel } from '@shared/types';

/**
 * Game + launcher log, kept in the main process so it survives page changes in the UI.
 * New lines are batched and pushed to listeners every 100 ms to keep IPC traffic low.
 */
const MAX_LINES = 5000;
const FLUSH_MS = 100;

let lines: ConsoleLine[] = [];
let pending: ConsoleLine[] = [];
let nextId = 1;
let timer: NodeJS.Timeout | null = null;
const listeners = new Set<(batch: ConsoleLine[]) => void>();

// Matches log4j's "[12:00:00] [Render thread/WARN]" prefix.
const LEVEL = /\/(INFO|WARN|ERROR|FATAL|DEBUG)\]/;

function detectLevel(text: string): LogLevel {
  const m = LEVEL.exec(text);
  if (!m) return /exception|error/i.test(text) ? 'error' : 'info';
  if (m[1] === 'WARN') return 'warn';
  if (m[1] === 'ERROR' || m[1] === 'FATAL') return 'error';
  if (m[1] === 'DEBUG') return 'debug';
  return 'info';
}

function flush() {
  timer = null;
  if (!pending.length) return;
  const batch = pending;
  pending = [];
  for (const l of listeners) l(batch);
}

/** Appends text (possibly several lines). Without an explicit level it is parsed from the log4j prefix. */
export function log(text: string, level?: LogLevel): void {
  for (const raw of text.split(/\r?\n/)) {
    if (!raw.trim()) continue;
    const line: ConsoleLine = { id: nextId++, time: Date.now(), level: level ?? detectLevel(raw), text: raw };
    lines.push(line);
    pending.push(line);
  }
  if (lines.length > MAX_LINES) lines = lines.slice(-MAX_LINES);
  timer ??= setTimeout(flush, FLUSH_MS);
}

export function getLines(): ConsoleLine[] {
  return lines;
}

export function clear(): void {
  lines = [];
  pending = [];
}

export function onLines(listener: (batch: ConsoleLine[]) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
