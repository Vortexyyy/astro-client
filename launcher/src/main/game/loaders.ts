import { access, mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import type { GameVersionInfo, LoaderId } from '@shared/types';
import { compareVersions } from '../importers/inspect';
import { getJson } from '../net/http';
import { paths } from '../paths';
import { astroVersions } from './astroMod';

/**
 * Fabric and Quilt both publish launcher profiles (a version JSON that inherits from vanilla),
 * which minecraft-launcher-core can run as a "custom" version. Vanilla needs no profile.
 */
const META: Record<Exclude<LoaderId, 'vanilla'>, { base: string; prefix: string }> = {
  fabric: { base: 'https://meta.fabricmc.net/v2', prefix: 'fabric-loader' },
  quilt: { base: 'https://meta.quiltmc.org/v3', prefix: 'quilt-loader' }
};

interface LoaderEntry {
  loader: { version: string; stable?: boolean };
}

/** Every release and snapshot Mojang publishes, newest first. */
export async function listGameVersions(): Promise<GameVersionInfo[]> {
  const manifest = await getJson<{ versions: Array<{ id: string; type: string }> }>(
    'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
  );
  const astro = await astroVersions();
  return manifest.versions
    .filter((v) => v.type === 'release' || v.type === 'snapshot')
    .map((v) => ({ id: v.id, type: v.type as GameVersionInfo['type'], astro: astro.has(v.id) }));
}

export async function latestLoaderVersion(loader: LoaderId, mcVersion: string): Promise<string> {
  if (loader === 'vanilla') return '';
  const list = await getJson<LoaderEntry[]>(`${META[loader].base}/versions/loader/${encodeURIComponent(mcVersion)}`);
  // Fabric marks stable builds; Quilt marks pre-releases with "-beta"/"-pre" in the version
  // and does not list newest first, so sort numerically.
  const stable = list
    .map((l) => l.loader)
    .filter((l) => l.stable ?? !/-(beta|pre|rc)/.test(l.version))
    .sort((a, b) => compareVersions(b.version, a.version));
  const pick = stable[0] ?? list[0]?.loader;
  if (!pick) throw new Error(`${loader === 'fabric' ? 'Fabric' : 'Quilt'} does not support Minecraft ${mcVersion}.`);
  return pick.version;
}

/** Returns the custom version name for launcher-core, or undefined for vanilla. */
export async function ensureLoaderProfile(loader: LoaderId, mcVersion: string, loaderVersion: string): Promise<string | undefined> {
  if (loader === 'vanilla') return undefined;
  const { base, prefix } = META[loader];
  const name = `${prefix}-${loaderVersion}-${mcVersion}`;
  const dir = join(paths.gameRoot, 'versions', name);
  const file = join(dir, `${name}.json`);
  try {
    await access(file);
  } catch {
    const json = await getJson<unknown>(
      `${base}/versions/loader/${encodeURIComponent(mcVersion)}/${encodeURIComponent(loaderVersion)}/profile/json`
    );
    await mkdir(dir, { recursive: true });
    await writeFile(file, JSON.stringify(json, null, 2));
  }
  return name;
}

/** Mod loaders whose mods a profile can use. Quilt runs Fabric mods too. */
export function compatibleLoaders(loader: LoaderId): string[] {
  if (loader === 'quilt') return ['quilt', 'fabric'];
  if (loader === 'fabric') return ['fabric'];
  return [];
}

export function loaderLabel(loader: LoaderId): string {
  return loader === 'fabric' ? 'Fabric' : loader === 'quilt' ? 'Quilt' : 'Vanilla';
}
