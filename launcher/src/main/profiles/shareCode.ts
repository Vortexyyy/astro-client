/**
 * Profile share codes: "ASTRO1-" + base64url(JSON). The payload carries ONLY the profile name,
 * Minecraft version, loader, memory and Modrinth project/version ids: no paths, usernames,
 * tokens or account data. Pure (no Electron) so encode/decode can be unit-checked.
 */
import type { LoaderId } from '@shared/types';

export const SHARE_PREFIX = 'ASTRO1-';
export const MAX_CODE_LENGTH = 16_000;
export const MAX_SHARED_MODS = 300;

export interface SharePayload {
  name: string;
  mcVersion: string;
  loader: LoaderId;
  memoryMb: number;
  /** [Modrinth project id, Modrinth version id] */
  mods: Array<[string, string]>;
}

interface Wire {
  v: 1;
  n: string;
  mc: string;
  l: LoaderId;
  m: number;
  mods: Array<[string, string]>;
}

const LOADERS: readonly LoaderId[] = ['fabric', 'quilt', 'vanilla'];
const MODRINTH_ID = /^[A-Za-z0-9]{8}$/;
const MC_VERSION = /^[0-9A-Za-z][0-9A-Za-z._-]{0,31}$/;
const BASE64URL = /^[A-Za-z0-9_-]+$/;
const WIRE_KEYS = ['l', 'm', 'mc', 'mods', 'n', 'v'];

function cleanName(raw: string): string {
  return raw.replace(/[\u0000-\u001f\u007f<>:"/\\|?*]/g, '').trim().slice(0, 48);
}

export function isModrinthId(id: string): boolean {
  return MODRINTH_ID.test(id);
}

export function encodeShareCode(p: SharePayload): string {
  const wire: Wire = {
    v: 1,
    n: cleanName(p.name) || 'Shared profile',
    mc: p.mcVersion,
    l: p.loader,
    m: p.memoryMb,
    mods: p.mods.filter(([a, b]) => isModrinthId(a) && isModrinthId(b)).slice(0, MAX_SHARED_MODS)
  };
  const code = SHARE_PREFIX + Buffer.from(JSON.stringify(wire), 'utf8').toString('base64url');
  if (code.length > MAX_CODE_LENGTH) throw new Error('This profile has too many mods to fit in a share code.');
  return code;
}

/** Throws a readable error for anything that isn't a well-formed Astro share code. */
export function decodeShareCode(input: unknown): SharePayload {
  if (typeof input !== 'string') throw new Error('Paste a share code.');
  const code = input.replace(/\s+/g, '');
  if (!code) throw new Error('Paste a share code.');
  if (code.length > MAX_CODE_LENGTH) throw new Error('That share code is too long.');
  if (!code.startsWith(SHARE_PREFIX)) throw new Error('That isn\'t an Astro share code (it should start with "ASTRO1-").');
  const body = code.slice(SHARE_PREFIX.length);
  if (!BASE64URL.test(body)) throw new Error('That share code is damaged (unexpected characters).');

  let wire: Wire;
  try {
    wire = JSON.parse(Buffer.from(body, 'base64url').toString('utf8')) as Wire;
  } catch {
    throw new Error('That share code is damaged and could not be read.');
  }
  const bad = (why: string): never => {
    throw new Error(`That share code is invalid: ${why}.`);
  };
  if (!wire || typeof wire !== 'object' || Array.isArray(wire)) bad('not a profile');
  if (Object.keys(wire).sort().join(',') !== WIRE_KEYS.join(',')) bad('unexpected fields');
  if (wire.v !== 1) bad('made by a newer launcher');
  if (typeof wire.n !== 'string' || !cleanName(wire.n)) bad('missing name');
  if (typeof wire.mc !== 'string' || !MC_VERSION.test(wire.mc)) bad('bad Minecraft version');
  if (!LOADERS.includes(wire.l)) bad('unknown mod loader');
  if (!Number.isInteger(wire.m) || wire.m < 1024 || wire.m > 32768) bad('memory out of range');
  if (!Array.isArray(wire.mods) || wire.mods.length > MAX_SHARED_MODS) bad('bad mod list');
  const seen = new Set<string>();
  for (const entry of wire.mods) {
    if (!Array.isArray(entry) || entry.length !== 2 || !entry.every((x) => typeof x === 'string' && MODRINTH_ID.test(x))) bad('bad mod entry');
    if (seen.has(entry[0])) bad('duplicate mod');
    seen.add(entry[0]);
  }
  if (wire.l === 'vanilla' && wire.mods.length) bad('vanilla profiles cannot have mods');
  return { name: cleanName(wire.n), mcVersion: wire.mc, loader: wire.l, memoryMb: wire.m, mods: wire.mods };
}
