import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import type { ProfileStats } from '@shared/types';
import { paths } from './paths';

/**
 * Per-profile playtime, kept in <userData>/stats.json rather than in profile.json so it never
 * travels with backups, exports or share codes.
 */
interface StatsDoc {
  version: 1;
  profiles: Record<string, ProfileStats>;
}

const file = () => join(paths.root, 'stats.json');
const ID = /^[0-9a-f-]{36}$/;
/** sessions longer than this are clamped (sleeping PCs, clock jumps) */
const MAX_SESSION_MS = 24 * 60 * 60 * 1000;

let cache: StatsDoc | null = null;
let writing: Promise<void> = Promise.resolve();
const listeners = new Set<(stats: Record<string, ProfileStats>) => void>();

const empty = (): ProfileStats => ({ playtimeMs: 0, launches: 0, lastPlayed: null, lastSessionMs: 0 });

async function load(): Promise<StatsDoc> {
  if (cache) return cache;
  try {
    const parsed = JSON.parse(await readFile(file(), 'utf8')) as Partial<StatsDoc>;
    const profiles: Record<string, ProfileStats> = {};
    for (const [id, v] of Object.entries(parsed.profiles ?? {})) {
      if (!ID.test(id) || typeof v !== 'object' || !v) continue;
      profiles[id] = {
        playtimeMs: Math.max(0, Number(v.playtimeMs) || 0),
        launches: Math.max(0, Math.floor(Number(v.launches) || 0)),
        lastPlayed: Number(v.lastPlayed) || null,
        lastSessionMs: Math.max(0, Number(v.lastSessionMs) || 0)
      };
    }
    cache = { version: 1, profiles };
  } catch {
    cache = { version: 1, profiles: {} };
  }
  return cache;
}

function persist(doc: StatsDoc): Promise<void> {
  // serialised so two quick updates can't interleave their writes
  writing = writing.then(async () => {
    await mkdir(paths.root, { recursive: true });
    await writeFile(`${file()}.tmp`, JSON.stringify(doc, null, 2));
    await rename(`${file()}.tmp`, file());
  }).catch(() => undefined);
  for (const l of listeners) l(doc.profiles);
  return writing;
}

export async function getStats(): Promise<Record<string, ProfileStats>> {
  return (await load()).profiles;
}

export function onStats(listener: (stats: Record<string, ProfileStats>) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export async function recordLaunch(profileId: string, at: number): Promise<void> {
  if (!ID.test(profileId)) return;
  const doc = await load();
  const s = doc.profiles[profileId] ?? empty();
  doc.profiles[profileId] = { ...s, launches: s.launches + 1, lastPlayed: at };
  await persist(doc);
}

export async function recordSession(profileId: string, startedAt: number, endedAt: number): Promise<void> {
  if (!ID.test(profileId)) return;
  const ms = Math.min(Math.max(0, endedAt - startedAt), MAX_SESSION_MS);
  const doc = await load();
  const s = doc.profiles[profileId] ?? empty();
  doc.profiles[profileId] = { ...s, playtimeMs: s.playtimeMs + ms, lastPlayed: endedAt, lastSessionMs: ms };
  await persist(doc);
}

export async function forgetProfile(profileId: string): Promise<void> {
  const doc = await load();
  if (!doc.profiles[profileId]) return;
  delete doc.profiles[profileId];
  await persist(doc);
}
