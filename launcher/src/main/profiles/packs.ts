import AdmZip from 'adm-zip';
import { dialog, type BrowserWindow } from 'electron';
import { existsSync } from 'node:fs';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import type { ImportResult, InstalledMod, LoaderId, Profile } from '@shared/types';
import { downloadVerified, isAllowedDownload } from '../net/http';
import { paths } from '../paths';
import { within } from '../util/fs';
import { addModFile, createProfile, getProfile, safeFileName, saveProfile } from './store';

const FORMAT = 'astropack';
const FORMAT_VERSION = 1;

interface AstroManifest {
  format: typeof FORMAT;
  formatVersion: number;
  name: string;
  mcVersion: string;
  loader?: LoaderId;
  loaderVersion: string;
  memoryMb: number;
  mods: InstalledMod[];
}

interface MrpackIndex {
  formatVersion: number;
  game: string;
  name: string;
  files: Array<{
    path: string;
    hashes: { sha1?: string; sha512?: string };
    downloads: string[];
    env?: { client?: 'required' | 'optional' | 'unsupported' };
  }>;
  dependencies: Record<string, string>;
}

async function extractPrefix(zip: AdmZip, prefix: string, destDir: string): Promise<void> {
  for (const entry of zip.getEntries()) {
    if (entry.isDirectory || !entry.entryName.startsWith(prefix)) continue;
    const rel = entry.entryName.slice(prefix.length);
    if (!rel) continue;
    const target = within(destDir, rel);
    await mkdir(dirname(target), { recursive: true });
    await writeFile(target, entry.getData());
  }
}

export async function exportProfile(win: BrowserWindow, id: string): Promise<string | null> {
  const profile = await getProfile(id);
  const { canceled, filePath } = await dialog.showSaveDialog(win, {
    title: 'Export profile',
    defaultPath: `${profile.name.replace(/[^\w\- ]+/g, '_')}.astropack`,
    filters: [{ name: 'Astro modpack', extensions: ['astropack'] }]
  });
  if (canceled || !filePath) return null;

  const manifest: AstroManifest = {
    format: FORMAT,
    formatVersion: FORMAT_VERSION,
    name: profile.name,
    mcVersion: profile.mcVersion,
    loader: profile.loader,
    loaderVersion: profile.loaderVersion,
    memoryMb: profile.memoryMb,
    mods: profile.mods
  };
  const zip = new AdmZip();
  zip.addFile('manifest.json', Buffer.from(JSON.stringify(manifest, null, 2)));
  const configDir = join(paths.instanceDir(id), 'config');
  if (existsSync(configDir)) zip.addLocalFolder(configDir, 'overrides/config');
  await zip.writeZipPromise(filePath);
  return filePath;
}

export async function importProfile(win: BrowserWindow): Promise<ImportResult | null> {
  const { canceled, filePaths } = await dialog.showOpenDialog(win, {
    title: 'Import modpack',
    properties: ['openFile'],
    filters: [{ name: 'Modpacks', extensions: ['astropack', 'mrpack'] }]
  });
  if (canceled || !filePaths[0]) return null;

  const zip = new AdmZip(filePaths[0]);
  if (zip.getEntry('manifest.json')) return importAstropack(zip);
  if (zip.getEntry('modrinth.index.json')) return importMrpack(zip);
  throw new Error('Not an .astropack or .mrpack file.');
}

async function importAstropack(zip: AdmZip): Promise<ImportResult> {
  const m = JSON.parse(zip.readAsText('manifest.json')) as AstroManifest;
  if (m.format !== FORMAT || m.formatVersion > FORMAT_VERSION || !Array.isArray(m.mods)) {
    throw new Error('Unsupported .astropack version.');
  }
  const profile = await createProfile({
    name: m.name,
    mcVersion: m.mcVersion,
    loader: m.loader ?? 'fabric',
    loaderVersion: m.loaderVersion,
    memoryMb: m.memoryMb
  });
  const skipped: string[] = [];
  for (const mod of m.mods) {
    try {
      await addModFile(profile, mod);
    } catch (err) {
      skipped.push(`${mod.title}: ${(err as Error).message}`);
    }
  }
  await extractPrefix(zip, 'overrides/', paths.instanceDir(profile.id));
  return { profile: await saveProfile(profile), skipped };
}

/** Modrinth CDN URLs look like https://cdn.modrinth.com/data/<project>/versions/<version>/<file>. */
function modrinthIds(url: string): { projectId: string; versionId: string } | null {
  const m = /^https:\/\/cdn\.modrinth\.com\/data\/([^/]+)\/versions\/([^/]+)\//.exec(url);
  return m ? { projectId: m[1], versionId: m[2] } : null;
}

async function importMrpack(zip: AdmZip): Promise<ImportResult> {
  const index = JSON.parse(zip.readAsText('modrinth.index.json')) as MrpackIndex;
  const mcVersion = index.dependencies?.minecraft;
  const fabric = index.dependencies?.['fabric-loader'];
  const quilt = index.dependencies?.['quilt-loader'];
  if (index.game !== 'minecraft' || !mcVersion) throw new Error('Invalid .mrpack.');
  if (index.dependencies?.['forge'] || index.dependencies?.['neoforge']) throw new Error('Forge/NeoForge modpacks are not supported yet.');

  const profile: Profile = await createProfile({
    name: index.name,
    mcVersion,
    loader: quilt ? 'quilt' : fabric ? 'fabric' : 'vanilla',
    ...(quilt || fabric ? { loaderVersion: quilt ?? fabric } : {})
  });
  const instance = paths.instanceDir(profile.id);
  const skipped: string[] = [];

  for (const file of index.files) {
    if (file.env?.client === 'unsupported') continue;
    const url = file.downloads.find((u) => isAllowedDownload(u));
    if (!url) {
      skipped.push(`${file.path}: no download from a trusted host`);
      continue;
    }
    try {
      const ids = file.path.startsWith('mods/') ? modrinthIds(url) : null;
      if (ids) {
        await addModFile(profile, {
          source: 'modrinth',
          ...ids,
          title: file.path.slice('mods/'.length).replace(/\.jar$/, ''),
          fileName: safeFileName(file.path),
          url,
          hashes: file.hashes
        });
      } else {
        await downloadVerified(url, within(instance, file.path), file.hashes);
      }
    } catch (err) {
      skipped.push(`${file.path}: ${(err as Error).message}`);
    }
  }
  await extractPrefix(zip, 'overrides/', instance);
  await extractPrefix(zip, 'client-overrides/', instance);
  return { profile: await saveProfile(profile), skipped };
}
