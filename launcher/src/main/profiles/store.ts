import { shell } from 'electron';
import { spawn } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { basename, isAbsolute, join } from 'node:path';
import type { BatchResult, InstalledMod, LoaderId, ModSource, NewProfile, Profile } from '@shared/types';
import { compatibleLoaders, latestLoaderVersion } from '../game/loaders';
import { downloadVerified } from '../net/http';
import { paths } from '../paths';
import { modrinthProjectTitles, modrinthVersionsByHash } from '../providers/modrinth';
import { provider } from '../providers/provider';

const ID_PATTERN = /^[0-9a-f-]{36}$/;
const LOADERS: LoaderId[] = ['fabric', 'quilt', 'vanilla'];

export function assertProfileId(id: string): void {
  if (!ID_PATTERN.test(id)) throw new Error('Invalid profile id');
}

/** Keeps a file name from an API or a pack inside the mods folder. */
export function safeFileName(name: string): string {
  const base = basename(name.replace(/\\/g, '/'));
  if (!base || base.startsWith('.') || !base.endsWith('.jar')) throw new Error(`Unsafe mod file name: ${name}`);
  return base;
}

export async function listProfiles(): Promise<Profile[]> {
  await mkdir(paths.profilesDir, { recursive: true });
  const entries = await readdir(paths.profilesDir, { withFileTypes: true });
  const profiles: Profile[] = [];
  for (const e of entries) {
    if (!e.isDirectory() || !ID_PATTERN.test(e.name)) continue;
    try {
      profiles.push(await getProfile(e.name));
    } catch {
      // ignore half-written or foreign folders
    }
  }
  return profiles.sort((a, b) => (b.lastPlayed ?? b.createdAt) - (a.lastPlayed ?? a.createdAt));
}

export async function getProfile(id: string): Promise<Profile> {
  assertProfileId(id);
  const profile = JSON.parse(await readFile(paths.profileFile(id), 'utf8')) as Profile;
  profile.loader ??= 'fabric'; // profiles from 0.1 had no loader field
  return profile;
}

export async function saveProfile(profile: Profile): Promise<Profile> {
  assertProfileId(profile.id);
  await mkdir(paths.modsDir(profile.id), { recursive: true });
  await writeFile(paths.profileFile(profile.id), JSON.stringify(profile, null, 2));
  return profile;
}

export async function createProfile(input: NewProfile & { loaderVersion?: string; id?: string }): Promise<Profile> {
  const name = input.name.trim();
  if (!name) throw new Error('Profile name is required.');
  // a fixed id is only used when restoring a profile from a backup
  if (input.id !== undefined) {
    assertProfileId(input.id);
    if (await getProfile(input.id).then(() => true, () => false)) throw new Error('That profile already exists on this device.');
  }
  const loader = input.loader ?? 'fabric';
  if (!LOADERS.includes(loader)) throw new Error('Unsupported mod loader');
  return saveProfile({
    id: input.id ?? randomUUID(),
    name: name.slice(0, 48),
    mcVersion: input.mcVersion,
    loader,
    loaderVersion: loader === 'vanilla' ? '' : (input.loaderVersion ?? (await latestLoaderVersion(loader, input.mcVersion))),
    memoryMb: Math.min(Math.max(input.memoryMb ?? 4096, 1024), 32768),
    mods: [],
    createdAt: Date.now()
  });
}

export async function deleteProfile(id: string): Promise<void> {
  assertProfileId(id);
  // Recycle bin rather than rm -rf: profiles may hold worlds.
  await shell.trashItem(paths.profileDir(id));
}

export async function openProfileFolder(id: string): Promise<void> {
  assertProfileId(id);
  const dir = paths.instanceDir(id);
  await mkdir(dir, { recursive: true });
  // shell.openPath reports failure as a returned message rather than throwing
  const error = await shell.openPath(dir);
  if (!error) return;
  if (process.platform !== 'win32') throw new Error(`Couldn't open the folder: ${error}`);
  await new Promise<void>((resolve, reject) => {
    const child = spawn('explorer.exe', [dir], { detached: true, stdio: 'ignore' });
    child.once('error', (err) => reject(new Error(`Couldn't open the folder: ${err.message}`)));
    child.once('spawn', () => {
      child.unref();
      resolve();
    });
  });
}

export function modLoaders(profile: Profile): string[] {
  const loaders = compatibleLoaders(profile.loader);
  if (!loaders.length) throw new Error('Vanilla profiles cannot have mods. Create a Fabric or Quilt profile instead.');
  return loaders;
}

/** Downloads one mod file into the profile and records it, replacing an older file of the same project. */
export async function addModFile(profile: Profile, mod: InstalledMod): Promise<void> {
  const fileName = safeFileName(mod.fileName);
  await downloadVerified(mod.url, join(paths.modsDir(profile.id), fileName), mod.hashes);
  const previous = profile.mods.find((m) => m.source === mod.source && m.projectId === mod.projectId);
  if (previous && previous.fileName !== fileName) {
    await rm(join(paths.modsDir(profile.id), safeFileName(previous.fileName)), { force: true });
  }
  profile.mods = [...profile.mods.filter((m) => m !== previous), { ...mod, fileName }];
}

/** Installs a project plus all of its required dependencies (breadth-first, same source) into `profile`. */
export async function installInto(profile: Profile, source: ModSource, projectId: string): Promise<void> {
  const loaders = modLoaders(profile);
  const api = provider(source);
  const queue = [projectId];
  const seen = new Set<string>();

  while (queue.length) {
    const id = queue.shift()!;
    if (seen.has(id)) continue;
    seen.add(id);
    const isRoot = id === projectId;
    // Dependencies already in the profile are kept as-is; the requested mod is always (re)installed.
    if (!isRoot && profile.mods.some((m) => m.source === source && m.projectId === id)) continue;

    const { mod, requiredDeps } = await api.resolve(id, profile.mcVersion, loaders);
    if (!isRoot && profile.mods.some((m) => m.source === source && m.projectId === mod.projectId)) continue;
    await addModFile(profile, mod);
    queue.push(...requiredDeps);
  }
}

export async function installMod(profileId: string, source: ModSource, projectId: string): Promise<Profile> {
  const profile = await getProfile(profileId);
  await installInto(profile, source, projectId);
  return saveProfile(profile);
}

const MAX_JAR_BYTES = 256 * 1024 * 1024;

/**
 * Copies jars dragged into the launcher into the profile's mods folder. Each one is identified on
 * Modrinth by hash where possible so it gets update checks; unknown jars are kept as local mods.
 */
export async function addLocalMods(profileId: string, filePaths: string[]): Promise<BatchResult> {
  const profile = await getProfile(profileId);
  modLoaders(profile);
  const installed: string[] = [];
  const skipped: string[] = [];
  const incoming = new Map<string, { fileName: string; data: Buffer }>();

  for (const filePath of filePaths.slice(0, 200)) {
    const label = basename(String(filePath));
    try {
      if (typeof filePath !== 'string' || !isAbsolute(filePath) || !/\.jar$/i.test(filePath)) throw new Error('not a .jar file');
      const info = await stat(filePath);
      if (!info.isFile()) throw new Error('not a file');
      if (info.size > MAX_JAR_BYTES) throw new Error('file is too large');
      const data = await readFile(filePath);
      // jars are zip archives; anything else would just crash the game at startup
      if (data.length < 4 || data.readUInt32LE(0) !== 0x04034b50) throw new Error('not a valid jar');
      const sha1 = createHash('sha1').update(data).digest('hex');
      if (incoming.has(sha1) || profile.mods.some((m) => m.hashes.sha1 === sha1)) throw new Error('already installed');
      incoming.set(sha1, { fileName: safeFileName(label.replace(/\.jar$/i, '.jar')), data });
    } catch (err) {
      skipped.push(`${label} (${(err as Error).message})`);
    }
  }
  if (!incoming.size) return { profile, installed, skipped };

  let versions: Awaited<ReturnType<typeof modrinthVersionsByHash>> = {};
  let titles: Record<string, string> = {};
  try {
    versions = await modrinthVersionsByHash([...incoming.keys()]);
    titles = await modrinthProjectTitles([...new Set(Object.values(versions).map((v) => v.project_id))]);
  } catch {
    // offline: the jars still install, just without update checks
  }

  const modsDir = paths.modsDir(profile.id);
  await mkdir(modsDir, { recursive: true });
  for (const [sha1, { fileName, data }] of incoming) {
    const v = versions[sha1];
    const file = v?.files.find((f) => f.hashes.sha1 === sha1);
    const mod: InstalledMod =
      v && file
        ? { source: 'modrinth', projectId: v.project_id, versionId: v.id, title: titles[v.project_id] ?? fileName, fileName, url: file.url, hashes: { sha1, sha512: file.hashes.sha512 } }
        : { source: 'local', projectId: `local:${fileName}`, versionId: '', title: fileName.replace(/\.jar$/, ''), fileName, url: '', hashes: { sha1 } };
    // a dropped jar replaces an older file of the same mod, or a different jar with the same name
    const replaced = profile.mods.filter((m) => (m.source === mod.source && m.projectId === mod.projectId) || m.fileName === fileName);
    for (const old of replaced) {
      if (old.fileName !== fileName) await rm(join(modsDir, safeFileName(old.fileName)), { force: true });
    }
    await writeFile(join(modsDir, fileName), data);
    profile.mods = [...profile.mods.filter((m) => !replaced.includes(m)), mod];
    installed.push(mod.title);
  }
  return { profile: await saveProfile(profile), installed, skipped };
}

export async function removeMod(profileId: string, projectId: string): Promise<Profile> {
  const profile = await getProfile(profileId);
  const mod = profile.mods.find((m) => m.projectId === projectId);
  if (!mod) return profile;
  await rm(join(paths.modsDir(profileId), safeFileName(mod.fileName)), { force: true });
  profile.mods = profile.mods.filter((m) => m !== mod);
  return saveProfile(profile);
}
