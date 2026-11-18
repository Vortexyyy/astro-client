import { clipboard } from 'electron';
import { copyFile, cp, mkdir, stat } from 'node:fs/promises';
import { join } from 'node:path';
import type { BatchResult, DuplicateOptions, InstalledMod, Profile, ShareCodeResult } from '@shared/types';
import { paths } from '../paths';
import { modrinthProjectTitles, modrinthVersion } from '../providers/modrinth';
import { provider } from '../providers/provider';
import { decodeShareCode, encodeShareCode } from './shareCode';
import { addModFile, createProfile, getProfile, modLoaders, safeFileName, saveProfile } from './store';

/**
 * Copies a profile's settings and mod files into a new profile (new id). Playtime, worlds and
 * anything account-related stay behind; the config folder is copied only when asked.
 */
export async function duplicateProfile(id: string, options: DuplicateOptions): Promise<Profile> {
  const source = await getProfile(id);
  const name = String(options?.name ?? '').trim() || `${source.name} (copy)`;
  const copy = await createProfile({
    name,
    mcVersion: source.mcVersion,
    loader: source.loader,
    loaderVersion: source.loaderVersion || undefined,
    memoryMb: source.memoryMb
  });

  const fromMods = paths.modsDir(source.id);
  const toMods = paths.modsDir(copy.id);
  await mkdir(toMods, { recursive: true });
  for (const mod of source.mods) {
    try {
      const file = safeFileName(mod.fileName);
      await copyFile(join(fromMods, file), join(toMods, file));
      copy.mods = [...copy.mods, { ...mod, fileName: file }];
    } catch {
      // jar missing on disk: re-download tracked mods, drop local ones we can't recover
      if (mod.source === 'local') continue;
      await addModFile(copy, mod).catch(() => undefined);
    }
  }

  if (options?.copyConfig) {
    const fromConfig = join(paths.instanceDir(source.id), 'config');
    if (await stat(fromConfig).then((s) => s.isDirectory(), () => false)) {
      await cp(fromConfig, join(paths.instanceDir(copy.id), 'config'), { recursive: true, errorOnExist: false, force: true });
    }
  }
  return saveProfile(copy);
}

export async function copyShareCode(id: string): Promise<ShareCodeResult> {
  const profile = await getProfile(id);
  const shared = profile.mods.filter((m) => m.source === 'modrinth');
  const code = encodeShareCode({
    name: profile.name,
    mcVersion: profile.mcVersion,
    loader: profile.loader,
    memoryMb: profile.memoryMb,
    mods: shared.map((m) => [m.projectId, m.versionId])
  });
  clipboard.writeText(code);
  return { code, skipped: profile.mods.filter((m) => m.source !== 'modrinth').map((m) => m.title) };
}

/** Creates a profile from a share code and installs its mods (exact versions when they still fit). */
export async function importShareCode(raw: unknown): Promise<BatchResult> {
  const payload = decodeShareCode(raw);
  const profile = await createProfile({
    name: payload.name,
    mcVersion: payload.mcVersion,
    loader: payload.loader,
    memoryMb: payload.memoryMb
  });
  const installed: string[] = [];
  const skipped: string[] = [];
  if (!payload.mods.length) return { profile, installed, skipped };

  const loaders = modLoaders(profile);
  let titles: Record<string, string> = {};
  try {
    titles = await modrinthProjectTitles(payload.mods.map(([projectId]) => projectId));
  } catch {
    // titles are cosmetic; fall back to file names
  }

  for (const [projectId, versionId] of payload.mods) {
    const title = titles[projectId] ?? projectId;
    try {
      let mod: InstalledMod | null = null;
      try {
        const v = await modrinthVersion(versionId);
        const file = v.files.find((f) => f.primary) ?? v.files[0];
        const fits = v.project_id === projectId && v.game_versions.includes(profile.mcVersion) && v.loaders.some((l) => loaders.includes(l));
        if (fits && file) {
          mod = { source: 'modrinth', projectId, versionId: v.id, title, fileName: file.filename, url: file.url, hashes: { sha1: file.hashes.sha1, sha512: file.hashes.sha512 } };
        }
      } catch {
        // version removed: fall back to the newest compatible one
      }
      mod ??= (await provider('modrinth').resolve(projectId, profile.mcVersion, loaders)).mod;
      await addModFile(profile, mod);
      installed.push(mod.title);
    } catch (err) {
      skipped.push(`${title}: ${(err as Error).message}`);
    }
  }
  return { profile: await saveProfile(profile), installed, skipped };
}
