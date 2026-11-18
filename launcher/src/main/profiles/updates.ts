import type { BatchResult, ModUpdate, ModUpdateStatus } from '@shared/types';
import { modrinthLatestByHash } from '../providers/modrinth';
import { provider } from '../providers/provider';
import { getModUpdateSettings } from '../settings';
import { addModFile, getProfile, modLoaders, saveProfile } from './store';

/**
 * Finds newer compatible versions of a profile's tracked mods.
 * Modrinth mods are checked in one bulk request by file hash; CurseForge mods one by one.
 */
export async function checkUpdates(profileId: string): Promise<ModUpdate[]> {
  const profile = await getProfile(profileId);
  const loaders = modLoaders(profile);
  const updates: ModUpdate[] = [];

  const modrinthMods = profile.mods.filter((m) => m.source === 'modrinth' && m.hashes.sha1);
  const latest = await modrinthLatestByHash(modrinthMods.map((m) => m.hashes.sha1!), profile.mcVersion, loaders);
  for (const m of modrinthMods) {
    const v = latest[m.hashes.sha1!];
    if (v && v.id !== m.versionId) {
      updates.push({ projectId: m.projectId, source: 'modrinth', title: m.title, currentFile: m.fileName, newVersion: v.version_number });
    }
  }

  for (const m of profile.mods.filter((x) => x.source === 'curseforge')) {
    try {
      const { mod } = await provider('curseforge').resolve(m.projectId, profile.mcVersion, loaders);
      if (mod.versionId !== m.versionId) {
        updates.push({ projectId: m.projectId, source: 'curseforge', title: m.title, currentFile: m.fileName, newVersion: mod.fileName });
      }
    } catch {
      // no key / no compatible file: nothing to offer
    }
  }
  return updates;
}

export async function applyUpdates(profileId: string, projectIds: string[]): Promise<BatchResult> {
  const profile = await getProfile(profileId);
  const loaders = modLoaders(profile);
  const installed: string[] = [];
  const skipped: string[] = [];
  for (const id of projectIds) {
    const current = profile.mods.find((m) => m.projectId === id && m.source !== 'local');
    if (!current || current.source === 'local') continue;
    try {
      const { mod } = await provider(current.source).resolve(id, profile.mcVersion, loaders);
      await addModFile(profile, mod);
      installed.push(mod.title);
    } catch (err) {
      skipped.push(`${current.title}: ${(err as Error).message}`);
    }
  }
  return { profile: await saveProfile(profile), installed, skipped };
}

const AUTO_CHECK_TIMEOUT_MS = 10_000;

/**
 * The automatic check (launcher start and before each launch). Honours "Check mod updates on
 * launch"; installs only when "Install updates automatically" is on. Returns null when it is off,
 * the profile has no tracked mods, or the check failed / timed out (never blocks a launch).
 */
export async function autoCheckUpdates(profileId: string): Promise<ModUpdateStatus | null> {
  const settings = await getModUpdateSettings();
  if (!settings.check) return null;
  const profile = await getProfile(profileId);
  if (profile.loader === 'vanilla' || !profile.mods.some((m) => m.source !== 'local')) return { profileId, updates: [] };

  let timer: NodeJS.Timeout | undefined;
  const timeout = new Promise<null>((resolve) => {
    timer = setTimeout(() => resolve(null), AUTO_CHECK_TIMEOUT_MS);
  });
  const updates = await Promise.race([checkUpdates(profileId).catch(() => null), timeout]);
  clearTimeout(timer);
  if (!updates) return null;
  if (!settings.autoInstall || !updates.length) return { profileId, updates };

  const result = await applyUpdates(profileId, updates.map((u) => u.projectId));
  const failed = new Set(result.skipped.map((s) => s.split(':')[0]));
  return {
    profileId,
    updates: updates.filter((u) => failed.has(u.title)),
    applied: { installed: result.installed, skipped: result.skipped }
  };
}
