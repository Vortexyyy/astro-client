import { dialog, type BrowserWindow } from 'electron';
import { createHash, randomUUID } from 'node:crypto';
import { cp, mkdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import type {
  ImportCandidate,
  ImportOptions,
  ImportProgress,
  ImportReport,
  ImportRequest,
  InstalledMod,
  LoaderId
} from '@shared/types';
import { paths } from '../paths';
import { createProfile, saveProfile } from '../profiles/store';
import { modrinthProjectTitles, modrinthVersionsByHash } from '../providers/modrinth';
import { exists, modFiles } from './inspect';
import {
  type Detected,
  scanAtLauncher,
  scanCurseForge,
  scanFolder,
  scanGdLauncher,
  scanLunar,
  scanModrinth,
  scanPrismFamily,
  scanVanilla
} from './sources';

/** Found instances by opaque key. The renderer only ever sees keys, never chooses source paths. */
const registry = new Map<string, Detected>();

function register(found: Detected[]): ImportCandidate[] {
  return found.map((d) => {
    const key = randomUUID();
    registry.set(key, d);
    const runnable = d.loader === 'fabric' || d.loader === 'quilt' || d.loader === 'vanilla';
    return {
      key,
      source: d.source,
      sourceLabel: d.sourceLabel,
      name: d.name,
      mcVersion: d.mcVersion,
      loader: d.loader,
      loaderVersion: d.loaderVersion,
      gameDir: d.gameDir,
      modCount: d.modCount,
      worldCount: d.worldCount,
      extras: d.extras,
      warning: runnable
        ? undefined
        : d.loader === 'unknown'
          ? 'Mod loader unknown: imports as vanilla (worlds, packs and settings only).'
          : `${d.loader === 'forge' ? 'Forge' : 'NeoForge'} isn't supported yet: imports as vanilla without its mods.`
    };
  });
}

export async function scanAll(): Promise<ImportCandidate[]> {
  registry.clear();
  const scanners = [scanLunar, scanModrinth, scanPrismFamily, scanCurseForge, scanVanilla, scanAtLauncher, scanGdLauncher];
  const results = await Promise.all(scanners.map((s) => s().catch(() => [] as Detected[])));
  return register(results.flat());
}

export async function pickFolder(win: BrowserWindow): Promise<ImportCandidate[]> {
  const { canceled, filePaths } = await dialog.showOpenDialog(win, {
    title: 'Choose an instance, launcher or .minecraft folder',
    properties: ['openDirectory']
  });
  if (canceled || !filePaths[0]) return [];
  return register(await scanFolder(filePaths[0]));
}

const VERSION = /^[\w.\-+]{1,32}$/;

const COPY_DIRS: Array<[keyof ImportOptions, string]> = [
  ['configs', 'config'],
  ['resourcePacks', 'resourcepacks'],
  ['shaderPacks', 'shaderpacks'],
  ['worlds', 'saves']
];
const OPTION_FILES = ['options.txt', 'optionsof.txt', 'optionsshaders.txt', 'servers.dat'];

async function importOne(
  req: ImportRequest,
  options: ImportOptions,
  progress: (step: string) => void
): Promise<ImportReport> {
  const d = registry.get(req.key);
  if (!d) throw new Error('This instance is no longer available. Scan again.');
  if (!VERSION.test(req.mcVersion)) throw new Error('Choose a Minecraft version.');
  const report: ImportReport = { candidateName: d.name, profile: null, copied: [], identifiedMods: 0, localMods: 0, warnings: [] };

  const loader: LoaderId = d.loader === 'fabric' || d.loader === 'quilt' ? d.loader : 'vanilla';
  if (loader === 'vanilla' && d.modCount > 0 && options.mods) report.warnings.push('Mods were not copied: the source uses an unsupported mod loader.');
  const keepLoaderVersion = loader !== 'vanilla' && d.loaderVersion && req.mcVersion === d.mcVersion;

  progress('Creating profile');
  const profile = await createProfile({
    name: req.name.trim() || d.name,
    mcVersion: req.mcVersion,
    loader,
    ...(keepLoaderVersion ? { loaderVersion: d.loaderVersion! } : {})
  });
  report.profile = profile;
  const instance = paths.instanceDir(profile.id);

  for (const [option, folder] of COPY_DIRS) {
    if (!options[option] || !(await exists(join(d.gameDir, folder)))) continue;
    progress(`Copying ${folder}`);
    await cp(join(d.gameDir, folder), join(instance, folder), { recursive: true, force: false, errorOnExist: false });
    report.copied.push(folder);
  }
  if (options.gameOptions) {
    for (const file of OPTION_FILES) {
      if (!(await exists(join(d.gameDir, file)))) continue;
      await cp(join(d.gameDir, file), join(instance, file), { force: false, errorOnExist: false });
      report.copied.push(file);
    }
  }

  if (options.mods && loader !== 'vanilla') {
    progress('Copying mods');
    const modsDir = paths.modsDir(profile.id);
    await mkdir(modsDir, { recursive: true });
    const hashes = new Map<string, string>(); // sha1 -> file name (enabled jars only)
    for (const dir of [join(d.gameDir, 'mods'), ...d.extraModDirs]) {
      for (const file of await modFiles(dir)) {
        const data = await readFile(join(dir, file));
        await writeFile(join(modsDir, file), data);
        if (file.endsWith('.jar')) hashes.set(createHash('sha1').update(data).digest('hex'), file);
      }
    }
    if (hashes.size) report.copied.push(`mods (${hashes.size})`);

    progress('Identifying mods on Modrinth');
    let versions: Awaited<ReturnType<typeof modrinthVersionsByHash>> = {};
    let titles: Record<string, string> = {};
    try {
      versions = await modrinthVersionsByHash([...hashes.keys()]);
      titles = await modrinthProjectTitles([...new Set(Object.values(versions).map((v) => v.project_id))]);
    } catch {
      report.warnings.push('Could not reach Modrinth; mods were copied but will not get update checks yet.');
    }
    const mods: InstalledMod[] = [];
    for (const [sha1, fileName] of hashes) {
      const v = versions[sha1];
      const file = v?.files.find((f) => f.hashes.sha1 === sha1);
      if (v && file) {
        mods.push({
          source: 'modrinth',
          projectId: v.project_id,
          versionId: v.id,
          title: titles[v.project_id] ?? fileName,
          fileName,
          url: file.url,
          hashes: { sha1, sha512: file.hashes.sha512 }
        });
        report.identifiedMods++;
      } else {
        mods.push({ source: 'local', projectId: `local:${fileName}`, versionId: '', title: fileName.replace(/\.jar$/, ''), fileName, url: '', hashes: { sha1 } });
        report.localMods++;
      }
    }
    profile.mods = mods;
  }

  if (d.astroConfig && Object.keys(d.astroConfig).length) {
    progress('Applying Lunar HUD settings');
    await mkdir(join(instance, 'config'), { recursive: true });
    await writeFile(join(instance, 'config', 'astroclient.json'), JSON.stringify(d.astroConfig, null, 2));
    report.copied.push('Lunar HUD settings → Astro');
  }

  report.profile = await saveProfile(profile);
  return report;
}

export async function importMany(
  requests: ImportRequest[],
  options: ImportOptions,
  emit: (p: ImportProgress) => void
): Promise<ImportReport[]> {
  const reports: ImportReport[] = [];
  for (let i = 0; i < requests.length; i++) {
    const name = registry.get(requests[i].key)?.name ?? requests[i].name;
    try {
      reports.push(await importOne(requests[i], options, (step) => emit({ name, step, index: i, total: requests.length })));
    } catch (err) {
      reports.push({ candidateName: name, profile: null, copied: [], identifiedMods: 0, localMods: 0, warnings: [], error: (err as Error).message });
    }
  }
  return reports;
}
