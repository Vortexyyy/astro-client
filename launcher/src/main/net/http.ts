import { createHash } from 'node:crypto';
import { mkdir, rename, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';

// Modrinth asks every client to send an identifying User-Agent.
export const USER_AGENT = 'AstroClient/0.2.0 (astro-launcher)';

/** Hosts mod files may be downloaded from. Anything else (e.g. from an imported pack) is refused. */
export const MOD_HOSTS = ['cdn.modrinth.com', 'edge.forgecdn.net', 'mediafilez.forgecdn.net'];
/** Mojang's own hosts, used for the Java runtime. */
export const MOJANG_HOSTS = ['launchermeta.mojang.com', 'piston-meta.mojang.com', 'piston-data.mojang.com', 'launcher.mojang.com'];

export function isAllowedDownload(url: string, hosts: readonly string[] = MOD_HOSTS): boolean {
  try {
    const u = new URL(url);
    return u.protocol === 'https:' && hosts.includes(u.hostname);
  } catch {
    return false;
  }
}

export async function getJson<T>(url: string, headers: Record<string, string> = {}): Promise<T> {
  const res = await fetch(url, {
    headers: { 'User-Agent': USER_AGENT, Accept: 'application/json', ...headers }
  });
  if (!res.ok) {
    throw new Error(`${new URL(url).hostname} responded ${res.status} ${res.statusText}`);
  }
  return (await res.json()) as T;
}

export async function postJson<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'User-Agent': USER_AGENT, Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (!res.ok) throw new Error(`${new URL(url).hostname} responded ${res.status} ${res.statusText}`);
  return (await res.json()) as T;
}

export interface ExpectedHashes {
  sha1?: string;
  sha512?: string;
}

/** Fetches an allow-listed URL and checks it against the given hash before returning the bytes. */
export async function fetchVerified(url: string, expected: ExpectedHashes, hosts: readonly string[] = MOD_HOSTS): Promise<Buffer> {
  if (!isAllowedDownload(url, hosts)) throw new Error(`Refusing to download from untrusted host: ${url}`);
  if (!expected.sha1 && !expected.sha512) throw new Error(`No hash available to verify ${url}`);

  const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
  if (!res.ok) throw new Error(`Download failed (${res.status}) for ${url}`);
  const data = Buffer.from(await res.arrayBuffer());

  if (expected.sha512) {
    const actual = createHash('sha512').update(data).digest('hex');
    if (actual !== expected.sha512.toLowerCase()) throw new Error(`SHA-512 mismatch for ${url}`);
  } else if (expected.sha1) {
    const actual = createHash('sha1').update(data).digest('hex');
    if (actual !== expected.sha1.toLowerCase()) throw new Error(`SHA-1 mismatch for ${url}`);
  }
  return data;
}

/**
 * Downloads an allow-listed file, verifies its hash and writes it atomically
 * (to `<dest>.part`, then renamed), so a failed download never leaves a broken file behind.
 */
export async function downloadVerified(
  url: string,
  dest: string,
  expected: ExpectedHashes,
  hosts: readonly string[] = MOD_HOSTS
): Promise<void> {
  const data = await fetchVerified(url, expected, hosts);
  await mkdir(dirname(dest), { recursive: true });
  const part = `${dest}.part`;
  await writeFile(part, data);
  await rename(part, dest);
}
