import AdmZip from 'adm-zip';
import { access, readdir, readFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { join } from 'node:path';
import type { ImportCandidate } from '@shared/types';

export type Loader = ImportCandidate['loader'];

export async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

export async function readJson<T>(path: string): Promise<T | null> {
  try {
    return JSON.parse((await readFile(path, 'utf8')).replace(/^﻿/, '')) as T;
  } catch {
    return null;
  }
}

export async function subdirs(dir: string): Promise<string[]> {
  try {
    return (await readdir(dir, { withFileTypes: true })).filter((e) => e.isDirectory()).map((e) => e.name);
  } catch {
    return [];
  }
}

/** Mod files in a folder (enabled `.jar` and disabled `.jar.disabled`), not recursive. */
export async function modFiles(dir: string): Promise<string[]> {
  try {
    return (await readdir(dir, { withFileTypes: true }))
      .filter((e) => e.isFile() && /\.jar(\.disabled)?$/i.test(e.name))
      .map((e) => e.name);
  } catch {
    return [];
  }
}

export async function countWorlds(gameDir: string): Promise<number> {
  let n = 0;
  for (const name of await subdirs(join(gameDir, 'saves'))) if (await exists(join(gameDir, 'saves', name, 'level.dat'))) n++;
  return n;
}

/** Per-OS base folders where launchers keep their data. */
export function dataRoots(): string[] {
  const home = homedir();
  if (process.platform === 'win32') {
    return [process.env['APPDATA'], process.env['LOCALAPPDATA'], home].filter((p): p is string => Boolean(p));
  }
  if (process.platform === 'darwin') return [join(home, 'Library', 'Application Support'), home];
  return [process.env['XDG_DATA_HOME'] ?? join(home, '.local', 'share'), join(home, '.config'), home];
}

/** Every existing `<root>/<name>` for the given folder names. */
export async function findDirs(names: string[]): Promise<string[]> {
  const found: string[] = [];
  const seen = new Set<string>();
  // Windows and macOS file systems are case-insensitive: "ATLauncher" and "atlauncher" are one folder.
  const norm = (p: string) => (process.platform === 'linux' ? p : p.toLowerCase());
  for (const root of dataRoots()) {
    for (const name of names) {
      const dir = join(root, name);
      if (seen.has(norm(dir)) || !(await exists(dir))) continue;
      seen.add(norm(dir));
      found.push(dir);
    }
  }
  return found;
}

/** Parses Minecraft-style INI (`key=value`, sections ignored) used by Prism/PolyMC/MultiMC. */
export function parseIni(text: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const line of text.split(/\r?\n/)) {
    const m = /^\s*([^#;=[\s][^=]*?)\s*=\s*(.*)$/.exec(line);
    if (m) out[m[1]] = m[2];
  }
  return out;
}

/** Splits launcher version ids such as "fabric-loader-0.16.5-1.21.1" or "1.20.1-forge-47.2.0". */
export function parseVersionId(id: string): { mcVersion: string | null; loader: Loader; loaderVersion: string | null } {
  let m = /^fabric-loader-([^-]+)-(.+)$/.exec(id);
  if (m) return { mcVersion: m[2], loader: 'fabric', loaderVersion: m[1] };
  m = /^quilt-loader-([^-]+)-(.+)$/.exec(id);
  if (m) return { mcVersion: m[2], loader: 'quilt', loaderVersion: m[1] };
  m = /^neoforge-(.+)$/.exec(id);
  if (m) return { mcVersion: null, loader: 'neoforge', loaderVersion: m[1] };
  m = /^([\d.]+)-forge-?(.+)$/i.exec(id);
  if (m) return { mcVersion: m[1], loader: 'forge', loaderVersion: m[2] };
  if (/^\d+\.\d+(\.\d+)?$/.test(id) || /^\d+w\d+[a-z]$/.test(id)) return { mcVersion: id, loader: 'vanilla', loaderVersion: null };
  return { mcVersion: null, loader: 'unknown', loaderVersion: null };
}

export function normalizeLoader(name: string | undefined | null): Loader {
  const n = (name ?? '').toLowerCase();
  if (n.includes('neoforge')) return 'neoforge';
  if (n.includes('forge')) return 'forge';
  if (n.includes('quilt')) return 'quilt';
  if (n.includes('fabric')) return 'fabric';
  if (n === 'vanilla' || n === 'none' || n === '') return 'vanilla';
  return 'unknown';
}

/**
 * Best-effort guess for folders without launcher metadata: the loader most mod jars target,
 * and the Minecraft version from a Fabric API / Quilted Fabric API file name.
 */
export async function inspectMods(dirs: string[]): Promise<{ loader: Loader; mcVersion: string | null }> {
  const votes: Record<string, number> = {};
  let mcVersion: string | null = null;
  for (const dir of dirs) {
    for (const file of await modFiles(dir)) {
      const v = /^(?:fabric-api|qfapi)-[\w.]+\+(\d+\.\d+(?:\.\d+)?)\.jar/i.exec(file);
      if (v) mcVersion ??= v[1];
      if (!file.endsWith('.jar')) continue;
      try {
        const zip = new AdmZip(join(dir, file));
        const kind = zip.getEntry('quilt.mod.json')
          ? 'quilt'
          : zip.getEntry('fabric.mod.json')
            ? 'fabric'
            : zip.getEntry('META-INF/neoforge.mods.toml')
              ? 'neoforge'
              : zip.getEntry('META-INF/mods.toml')
                ? 'forge'
                : null;
        if (kind) votes[kind] = (votes[kind] ?? 0) + 1;
      } catch {
        // not a readable jar
      }
    }
  }
  const best = Object.entries(votes).sort((a, b) => b[1] - a[1])[0];
  return { loader: (best?.[0] as Loader) ?? 'vanilla', mcVersion };
}

export function compareVersions(a: string, b: string): number {
  const pa = a.split('.').map(Number);
  const pb = b.split('.').map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d) return d;
  }
  return 0;
}
