import { app } from 'electron';
import { copyFile, mkdir, readdir, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import type { Profile } from '@shared/types';
import { paths } from '../paths';
import { installInto, saveProfile } from '../profiles/store';
import { exists } from '../importers/inspect';
import * as gameConsole from './console';

/** Jars built by `./gradlew copyToLauncher` in /mod, shipped as app resources. */
function bundleDir(): string {
  return app.isPackaged ? join(process.resourcesPath, 'astro-mod') : join(app.getAppPath(), 'resources', 'astro-mod');
}

const JAR = /^astroclient-(.+)\+mc(.+)\.jar$/;

/** Minecraft versions that have a bundled Astro Client build. */
export async function astroVersions(): Promise<Map<string, string>> {
  const out = new Map<string, string>();
  try {
    for (const file of await readdir(bundleDir())) {
      const m = JAR.exec(file);
      if (m) out.set(m[2], file);
    }
  } catch {
    // no bundled builds (e.g. mod not built yet)
  }
  return out;
}

/** Mods that make the biggest FPS difference on Fabric, all compatible with Astro's HUD. */
const PERFORMANCE_MODS = ['sodium', 'lithium', 'ferrite-core', 'immediatelyfast', 'entityculling'];

function hasMod(profile: Profile, slug: string, files: string[]): boolean {
  const needle = slug.replace('-', '').toLowerCase();
  return (
    profile.mods.some((m) => m.title.replace(/[\s-]/g, '').toLowerCase() === needle) ||
    files.some((f) => f.replace(/[\s_-]/g, '').toLowerCase().startsWith(needle))
  );
}

/**
 * Before launch: put the matching Astro Client jar in the profile, make sure Fabric API is there,
 * and add the performance mods. Failures are logged and never block the launch.
 */
export async function prepareAstro(profile: Profile, status: (message: string) => void): Promise<Profile> {
  if (profile.loader === 'vanilla') return profile;
  const modsDir = paths.modsDir(profile.id);
  await mkdir(modsDir, { recursive: true });

  const builds = await astroVersions();
  const jar = builds.get(profile.mcVersion);
  const files = await readdir(modsDir);
  if (!jar) {
    gameConsole.log(`Astro Client has no build for Minecraft ${profile.mcVersion}; launching without it.`, 'warn');
  } else if (!files.includes(jar)) {
    status('Installing Astro Client…');
    for (const old of files.filter((f) => JAR.test(f))) await rm(join(modsDir, old), { force: true });
    await copyFile(join(bundleDir(), jar), join(modsDir, jar));
    gameConsole.log(`Installed ${jar}`, 'launcher');
  }

  const wanted = [...(jar ? ['fabric-api'] : []), ...PERFORMANCE_MODS].filter((slug) => !hasMod(profile, slug, files));
  if (wanted.length) {
    status('Installing performance mods…');
    for (const slug of wanted) {
      try {
        await installInto(profile, 'modrinth', slug);
        gameConsole.log(`Installed ${slug}`, 'launcher');
      } catch (err) {
        gameConsole.log(`Skipped ${slug}: ${(err as Error).message}`, 'warn');
      }
    }
    profile = await saveProfile(profile);
  }
  return profile;
}

/**
 * A profile's first launch gets FPS-friendly video settings (VSync off, no frame cap).
 * Existing options.txt files (including imported ones) are never touched.
 */
export async function ensureFpsDefaults(profile: Profile): Promise<void> {
  const file = join(paths.instanceDir(profile.id), 'options.txt');
  if (await exists(file)) return;
  await mkdir(paths.instanceDir(profile.id), { recursive: true });
  // maxFps 260 is Minecraft's "Unlimited"
  await writeFile(file, 'enableVsync:false\nmaxFps:260\n');
}
