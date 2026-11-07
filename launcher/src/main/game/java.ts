import { access, chmod, mkdir, readFile, symlink, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { downloadVerified, fetchVerified, getJson, MOJANG_HOSTS } from '../net/http';
import { paths } from '../paths';
import { within } from '../util/fs';

/**
 * Downloads the same Java runtimes the official launcher uses, from Mojang's runtime manifest,
 * verifying every file's SHA-1. Each Minecraft version declares the runtime it needs
 * (1.21.x → "java-runtime-delta", Java 21).
 */
const RUNTIME_INDEX =
  'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';
const VERSION_MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const PARALLEL_DOWNLOADS = 8;

interface RuntimeIndex {
  [platform: string]: Record<string, Array<{ manifest: { sha1: string; url: string }; version: { name: string } }>>;
}

type RuntimeFile =
  | { type: 'directory' }
  | { type: 'file'; executable?: boolean; downloads: { raw: { sha1: string; url: string; size: number } } }
  | { type: 'link'; target: string };

export interface JavaRuntime {
  component: string;
  version: string;
  executable: string;
}

function platformKey(): string {
  const arm = process.arch === 'arm64';
  switch (process.platform) {
    case 'win32':
      return arm ? 'windows-arm64' : process.arch === 'ia32' ? 'windows-x86' : 'windows-x64';
    case 'darwin':
      return arm ? 'mac-os-arm64' : 'mac-os';
    default:
      return process.arch === 'ia32' ? 'linux-i386' : 'linux';
  }
}

function javaExecutable(root: string): string {
  if (process.platform === 'win32') return join(root, 'bin', 'javaw.exe'); // javaw: no console window
  if (process.platform === 'darwin') return join(root, 'jre.bundle', 'Contents', 'Home', 'bin', 'java');
  return join(root, 'bin', 'java');
}

const componentCache = new Map<string, string>();

/** The runtime component a Minecraft version asks for (falls back to Java 8 for very old versions). */
export async function requiredRuntime(mcVersion: string): Promise<string> {
  const cached = componentCache.get(mcVersion);
  if (cached) return cached;
  const manifest = await getJson<{ versions: Array<{ id: string; url: string }> }>(VERSION_MANIFEST);
  const entry = manifest.versions.find((v) => v.id === mcVersion);
  if (!entry) throw new Error(`Unknown Minecraft version ${mcVersion}`);
  const version = await getJson<{ javaVersion?: { component: string } }>(entry.url);
  const component = version.javaVersion?.component ?? 'jre-legacy';
  componentCache.set(mcVersion, component);
  return component;
}

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

export async function ensureJavaRuntime(
  mcVersion: string,
  onProgress: (current: number, total: number) => void
): Promise<JavaRuntime> {
  const component = await requiredRuntime(mcVersion);
  const index = await getJson<RuntimeIndex>(RUNTIME_INDEX);
  const release = index[platformKey()]?.[component]?.[0];
  if (!release) throw new Error(`Mojang has no ${component} runtime for ${platformKey()}.`);

  const root = join(paths.runtimesDir, component);
  const marker = join(root, '.astro-manifest');
  const exe = javaExecutable(root);
  const installed = await readFile(marker, 'utf8').catch(() => '');
  if (installed === release.manifest.sha1 && (await exists(exe))) {
    return { component, version: release.version.name, executable: exe };
  }

  const manifestData = await fetchVerified(release.manifest.url, { sha1: release.manifest.sha1 }, MOJANG_HOSTS);
  const files = Object.entries((JSON.parse(manifestData.toString('utf8')) as { files: Record<string, RuntimeFile> }).files);

  for (const [rel, f] of files) if (f.type === 'directory') await mkdir(within(root, rel), { recursive: true });

  const downloads = files.filter((e): e is [string, Extract<RuntimeFile, { type: 'file' }>] => e[1].type === 'file');
  let done = 0;
  let next = 0;
  const worker = async () => {
    while (next < downloads.length) {
      const [rel, f] = downloads[next++];
      const dest = within(root, rel);
      await downloadVerified(f.downloads.raw.url, dest, { sha1: f.downloads.raw.sha1 }, MOJANG_HOSTS);
      if (f.executable && process.platform !== 'win32') await chmod(dest, 0o755);
      onProgress(++done, downloads.length);
    }
  };
  await Promise.all(Array.from({ length: PARALLEL_DOWNLOADS }, worker));

  if (process.platform !== 'win32') {
    for (const [rel, f] of files) {
      if (f.type !== 'link') continue;
      const dest = within(root, rel);
      within(root, join(dirname(rel), f.target)); // the link must also point inside the runtime
      if (!(await exists(dest))) await symlink(f.target, dest);
    }
  }

  await writeFile(marker, release.manifest.sha1);
  return { component, version: release.version.name, executable: exe };
}
