import { readFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { basename, isAbsolute, join } from 'node:path';
import type { ImportSourceId } from '@shared/types';
import {
  compareVersions,
  countWorlds,
  exists,
  findDirs,
  inspectMods,
  type Loader,
  modFiles,
  normalizeLoader,
  parseIni,
  parseVersionId,
  readJson,
  subdirs
} from './inspect';
import { lunarModuleSettings } from './lunar';

/** A found instance, with the real paths that never leave the main process. */
export interface Detected {
  source: ImportSourceId;
  sourceLabel: string;
  name: string;
  mcVersion: string | null;
  loader: Loader;
  loaderVersion: string | null;
  gameDir: string;
  /** additional folders whose jars belong in `mods` (Lunar keeps per-version mod folders) */
  extraModDirs: string[];
  modCount: number;
  worldCount: number;
  extras: string[];
  /** Astro module settings to write to config/astroclient.json */
  astroConfig?: Record<string, unknown>;
}

async function finish(d: Omit<Detected, 'modCount' | 'worldCount'>): Promise<Detected> {
  let modCount = 0;
  for (const dir of [join(d.gameDir, 'mods'), ...d.extraModDirs]) modCount += (await modFiles(dir)).length;
  return { ...d, modCount, worldCount: await countWorlds(d.gameDir) };
}

/** Fills a missing version/loader by looking at the mod jars themselves. */
async function withGuesses(d: Omit<Detected, 'modCount' | 'worldCount'>): Promise<Detected> {
  // Vanilla profiles (e.g. "Latest release") may share a mods folder with others, so their jars say nothing.
  if (d.loader === 'unknown' || (!d.mcVersion && d.loader !== 'vanilla')) {
    const guess = await inspectMods([join(d.gameDir, 'mods'), ...d.extraModDirs]);
    d = { ...d, mcVersion: d.mcVersion ?? guess.mcVersion, loader: d.loader === 'unknown' ? guess.loader : d.loader };
  }
  return finish(d);
}

// ── Official Minecraft Launcher ─────────────────────────────────────────

interface LauncherProfiles {
  profiles?: Record<string, { name?: string; type?: string; lastVersionId?: string; gameDir?: string }>;
}

export async function scanVanilla(): Promise<Detected[]> {
  const roots = await findDirs(['.minecraft', 'minecraft']);
  const out: Detected[] = [];
  for (const root of roots) {
    const file = await readJson<LauncherProfiles>(join(root, 'launcher_profiles.json'));
    for (const p of Object.values(file?.profiles ?? {})) {
      const parsed = parseVersionId(p.lastVersionId ?? '');
      const name = p.name || (p.type === 'latest-snapshot' ? 'Latest snapshot' : 'Latest release');
      out.push(
        await withGuesses({
          source: 'vanilla',
          sourceLabel: 'Minecraft Launcher',
          name,
          ...parsed,
          loader: p.type === 'latest-release' || p.type === 'latest-snapshot' ? 'vanilla' : parsed.loader,
          gameDir: p.gameDir && isAbsolute(p.gameDir) ? p.gameDir : root,
          extraModDirs: [],
          extras: []
        })
      );
    }
  }
  return out;
}

// ── Prism Launcher / PolyMC / MultiMC (same instance format) ────────────

interface MmcPack {
  components?: Array<{ uid: string; version?: string }>;
}

const MMC_LOADERS: Record<string, Loader> = {
  'net.fabricmc.fabric-loader': 'fabric',
  'org.quiltmc.quilt-loader': 'quilt',
  'net.minecraftforge': 'forge',
  'net.neoforged': 'neoforge'
};

async function scanMmcInstance(dir: string, source: ImportSourceId, label: string): Promise<Detected | null> {
  let cfg: Record<string, string>;
  try {
    cfg = parseIni(await readFile(join(dir, 'instance.cfg'), 'utf8'));
  } catch {
    return null;
  }
  const pack = await readJson<MmcPack>(join(dir, 'mmc-pack.json'));
  const components = pack?.components ?? [];
  const mc = components.find((c) => c.uid === 'net.minecraft')?.version ?? null;
  const loaderComponent = components.find((c) => MMC_LOADERS[c.uid]);
  const gameDir = (await exists(join(dir, '.minecraft'))) ? join(dir, '.minecraft') : join(dir, 'minecraft');
  return withGuesses({
    source,
    sourceLabel: label,
    name: cfg['name'] || basename(dir),
    mcVersion: mc,
    loader: loaderComponent ? MMC_LOADERS[loaderComponent.uid] : 'vanilla',
    loaderVersion: loaderComponent?.version ?? null,
    gameDir,
    extraModDirs: [],
    extras: []
  });
}

/** Scans an MMC-style launcher root (or a bare instances folder). */
export async function scanMmcRoot(root: string, source: ImportSourceId, label: string, cfgName?: string): Promise<Detected[]> {
  let instancesDir = join(root, 'instances');
  if (cfgName) {
    try {
      const custom = parseIni(await readFile(join(root, cfgName), 'utf8'))['InstanceDir'];
      if (custom) instancesDir = isAbsolute(custom) ? custom : join(root, custom);
    } catch {
      // default location
    }
  }
  if (!(await exists(instancesDir))) instancesDir = root;
  const out: Detected[] = [];
  for (const name of await subdirs(instancesDir)) {
    const found = await scanMmcInstance(join(instancesDir, name), source, label);
    if (found) out.push(found);
  }
  return out;
}

export async function scanPrismFamily(): Promise<Detected[]> {
  const out: Detected[] = [];
  const families: Array<[string[], ImportSourceId, string, string]> = [
    [['PrismLauncher', join('.var', 'app', 'org.prismlauncher.PrismLauncher', 'data', 'PrismLauncher')], 'prism', 'Prism Launcher', 'prismlauncher.cfg'],
    [['PolyMC'], 'polymc', 'PolyMC', 'polymc.cfg'],
    [['MultiMC'], 'multimc', 'MultiMC', 'multimc.cfg']
  ];
  for (const [names, source, label, cfg] of families) {
    for (const root of await findDirs(names)) out.push(...(await scanMmcRoot(root, source, label, cfg)));
  }
  return out;
}

// ── CurseForge app ──────────────────────────────────────────────────────

interface CfInstance {
  name?: string;
  gameVersion?: string;
  baseModLoader?: { name?: string; minecraftVersion?: string } | null;
}

async function scanCurseForgeInstance(dir: string): Promise<Detected | null> {
  const json = await readJson<CfInstance>(join(dir, 'minecraftinstance.json'));
  if (!json) return null;
  const loaderName = json.baseModLoader?.name ?? ''; // e.g. "fabric-0.15.11-1.20.4", "forge-47.2.0"
  const m = /^[a-z]+-([^-]+)/i.exec(loaderName);
  return withGuesses({
    source: 'curseforge',
    sourceLabel: 'CurseForge',
    name: json.name || basename(dir),
    mcVersion: json.gameVersion ?? json.baseModLoader?.minecraftVersion ?? null,
    loader: loaderName ? normalizeLoader(loaderName) : 'vanilla',
    loaderVersion: m?.[1] ?? null,
    gameDir: dir,
    extraModDirs: [],
    extras: []
  });
}

export async function scanCurseForge(): Promise<Detected[]> {
  const home = homedir();
  const roots = [join(home, 'curseforge', 'minecraft', 'Instances'), join(home, 'Documents', 'curseforge', 'minecraft', 'Instances')];
  const out: Detected[] = [];
  for (const root of roots) {
    for (const name of await subdirs(root)) {
      const found = await scanCurseForgeInstance(join(root, name));
      if (found) out.push(found);
    }
  }
  return out;
}

// ── Modrinth App ────────────────────────────────────────────────────────

interface ModrinthRow {
  name: string;
  path: string;
  game_version: string | null;
  loader: string | null;
  loader_version: string | null;
}

/**
 * Reads instance metadata from the Modrinth App's SQLite database (read-only). Only instance
 * name/version/loader columns are queried; account tables are never touched. Handles the
 * current schema (instances + instance_content_sets) and the older `profiles` table.
 */
async function modrinthRows(root: string): Promise<ModrinthRow[]> {
  const dbFile = join(root, 'app.db');
  if (!(await exists(dbFile))) return [];
  const { DatabaseSync } = await import('node:sqlite');
  const db = new DatabaseSync(dbFile, { readOnly: true });
  try {
    const tables = new Set(
      (db.prepare("select name from sqlite_master where type='table'").all() as Array<{ name: string }>).map((t) => t.name)
    );
    if (tables.has('instances') && tables.has('instance_content_sets')) {
      return db
        .prepare(
          `select i.name, i.path, s.game_version, s.loader, s.loader_version
           from instances i left join instance_content_sets s on s.id = i.applied_content_set_id`
        )
        .all() as unknown as ModrinthRow[];
    }
    if (tables.has('profiles')) {
      return db
        .prepare('select name, path, game_version, mod_loader as loader, mod_loader_version as loader_version from profiles')
        .all() as unknown as ModrinthRow[];
    }
    return [];
  } finally {
    db.close();
  }
}

export async function scanModrinth(): Promise<Detected[]> {
  const out: Detected[] = [];
  for (const root of await findDirs(['ModrinthApp', 'com.modrinth.theseus'])) {
    let rows: ModrinthRow[] = [];
    try {
      rows = await modrinthRows(root);
    } catch {
      // database locked or unknown schema: fall back to the folders below
    }
    const known = new Set(rows.map((r) => r.path));
    for (const r of rows) {
      const gameDir = join(root, 'profiles', basename(r.path));
      if (!(await exists(gameDir))) continue;
      out.push(
        await withGuesses({
          source: 'modrinth',
          sourceLabel: 'Modrinth App',
          name: r.name || basename(r.path),
          mcVersion: r.game_version,
          loader: normalizeLoader(r.loader),
          loaderVersion: r.loader_version,
          gameDir,
          extraModDirs: [],
          extras: []
        })
      );
    }
    // profiles the database doesn't list (or very old versions with profile.json per folder)
    for (const name of await subdirs(join(root, 'profiles'))) {
      if (known.has(name)) continue;
      const legacy = await readJson<{ metadata?: { name?: string; game_version?: string; loader?: string; loader_version?: { id?: string } } }>(
        join(root, 'profiles', name, 'profile.json')
      );
      out.push(
        await withGuesses({
          source: 'modrinth',
          sourceLabel: 'Modrinth App',
          name: legacy?.metadata?.name ?? name,
          mcVersion: legacy?.metadata?.game_version ?? null,
          loader: legacy ? normalizeLoader(legacy.metadata?.loader) : 'unknown',
          loaderVersion: legacy?.metadata?.loader_version?.id ?? null,
          gameDir: join(root, 'profiles', name),
          extraModDirs: [],
          extras: []
        })
      );
    }
  }
  return out;
}

// ── ATLauncher ──────────────────────────────────────────────────────────

interface AtlInstance {
  id?: string;
  minecraftVersion?: string;
  launcher?: { name?: string; loaderVersion?: { type?: string; version?: string } | null };
}

export async function scanAtLauncher(): Promise<Detected[]> {
  const out: Detected[] = [];
  for (const root of await findDirs(['ATLauncher', 'atlauncher'])) {
    for (const name of await subdirs(join(root, 'instances'))) {
      const dir = join(root, 'instances', name);
      const json = await readJson<AtlInstance>(join(dir, 'instance.json'));
      if (!json) continue;
      const lv = json.launcher?.loaderVersion;
      out.push(
        await withGuesses({
          source: 'atlauncher',
          sourceLabel: 'ATLauncher',
          name: json.launcher?.name || name,
          mcVersion: json.minecraftVersion ?? json.id ?? null,
          loader: lv ? normalizeLoader(lv.type) : 'vanilla',
          loaderVersion: lv?.version ?? null,
          gameDir: dir,
          extraModDirs: [],
          extras: []
        })
      );
    }
  }
  return out;
}

// ── GDLauncher (legacy "next" and current "carbon") ─────────────────────

export async function scanGdLauncher(): Promise<Detected[]> {
  const out: Detected[] = [];
  for (const root of await findDirs(['gdlauncher_next'])) {
    for (const name of await subdirs(join(root, 'instances'))) {
      const dir = join(root, 'instances', name);
      const json = await readJson<{ loader?: { loaderType?: string; mcVersion?: string; loaderVersion?: string } }>(join(dir, 'config.json'));
      if (!json) continue;
      out.push(
        await withGuesses({
          source: 'gdlauncher',
          sourceLabel: 'GDLauncher',
          name,
          mcVersion: json.loader?.mcVersion ?? null,
          loader: normalizeLoader(json.loader?.loaderType),
          loaderVersion: json.loader?.loaderVersion ?? null,
          gameDir: dir,
          extraModDirs: [],
          extras: []
        })
      );
    }
  }
  for (const root of await findDirs([join('gdlauncher_carbon', 'data')])) {
    for (const name of await subdirs(join(root, 'instances'))) {
      const dir = join(root, 'instances', name);
      type Carbon = { name?: string; game_configuration?: { version?: { Standard?: { release?: string; modloaders?: Array<{ type_?: string; version?: string }> } } } };
      const json = await readJson<Carbon>(join(dir, 'instance.json'));
      if (!json) continue;
      const std = json.game_configuration?.version?.Standard;
      const ml = std?.modloaders?.[0];
      out.push(
        await withGuesses({
          source: 'gdlauncher',
          sourceLabel: 'GDLauncher',
          name: json.name || name,
          mcVersion: std?.release ?? null,
          loader: ml ? normalizeLoader(ml.type_) : 'vanilla',
          loaderVersion: ml?.version ?? null,
          gameDir: (await exists(join(dir, 'instance'))) ? join(dir, 'instance') : dir,
          extraModDirs: [],
          extras: []
        })
      );
    }
  }
  return out;
}

// ── Lunar Client ────────────────────────────────────────────────────────

/**
 * Lunar keeps one shared game folder (profiles/main), Fabric mods per version
 * (profiles/<x>/mods/fabric-<mc>) and named settings profiles (settings/game/<name>/mods.json).
 * Each settings profile becomes one candidate so its HUD settings can be carried over.
 */
export async function scanLunar(): Promise<Detected[]> {
  const root = join(homedir(), '.lunarclient');
  if (!(await exists(root))) return [];
  const gameDir = join(root, 'profiles', 'main');
  if (!(await exists(gameDir))) return [];

  let newest: { mc: string; dir: string } | null = null;
  for (const group of await subdirs(join(root, 'profiles'))) {
    for (const folder of await subdirs(join(root, 'profiles', group, 'mods'))) {
      const m = /^fabric-(\d+\.\d+(?:\.\d+)?)$/.exec(folder);
      if (m && (!newest || compareVersions(m[1], newest.mc) > 0)) newest = { mc: m[1], dir: join(root, 'profiles', group, 'mods', folder) };
    }
  }

  const settingsRoot = join(root, 'settings', 'game');
  const names = await subdirs(settingsRoot);
  const out: Detected[] = [];
  for (const name of names.length ? names : ['Default']) {
    const mapped = await lunarModuleSettings(join(settingsRoot, name, 'mods.json'));
    out.push(
      await finish({
        source: 'lunar',
        sourceLabel: 'Lunar Client',
        name: `Lunar · ${name}`,
        mcVersion: newest?.mc ?? null,
        loader: 'fabric',
        loaderVersion: null,
        gameDir,
        extraModDirs: newest ? [newest.dir] : [],
        extras: mapped.summary,
        astroConfig: mapped.config
      })
    );
  }
  return out;
}

// ── Any folder the user picks ───────────────────────────────────────────

/** Recognises a launcher instance, a launcher root, or a plain .minecraft-style folder. */
export async function scanFolder(dir: string): Promise<Detected[]> {
  if (await exists(join(dir, 'instance.cfg'))) {
    const one = await scanMmcInstance(dir, 'folder', 'Folder');
    return one ? [one] : [];
  }
  if (await exists(join(dir, 'minecraftinstance.json'))) {
    const one = await scanCurseForgeInstance(dir);
    return one ? [{ ...one, source: 'folder', sourceLabel: 'Folder' }] : [];
  }
  if (await exists(join(dir, 'instances'))) {
    const many = await scanMmcRoot(dir, 'folder', 'Folder');
    if (many.length) return many;
  }
  const gameDir = (await exists(join(dir, '.minecraft'))) ? join(dir, '.minecraft') : dir;
  return [
    await withGuesses({
      source: 'folder',
      sourceLabel: 'Folder',
      name: basename(dir),
      mcVersion: null,
      loader: 'unknown',
      loaderVersion: null,
      gameDir,
      extraModDirs: [],
      extras: []
    })
  ];
}
