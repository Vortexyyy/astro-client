import type { BatchResult, ModUpdate } from '@shared/types';
import { modrinthLatestByHash } from '../providers/modrinth';
import { provider } from '../providers/provider';
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
