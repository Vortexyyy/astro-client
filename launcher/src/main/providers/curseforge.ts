import { getCurseforgeKey } from '../settings';
import { getJson } from '../net/http';
import type { ModProvider } from './provider';

const API = 'https://api.curseforge.com/v1';
const GAME_MINECRAFT = 432;
const CLASS_MODS = 6;
const LOADER_FABRIC = 4;
const LOADER_QUILT = 5;

// CurseForge filters by a single loader; Quilt profiles search Quilt-tagged files first.
const loaderType = (loaders: string[]) => (loaders[0] === 'quilt' ? LOADER_QUILT : LOADER_FABRIC);
const SORT_POPULARITY = 2;
const HASH_SHA1 = 1;
const RELATION_REQUIRED = 3;

interface CfMod {
  id: number;
  name: string;
  slug: string;
  summary: string;
  downloadCount: number;
  authors: Array<{ name: string }>;
  logo: { thumbnailUrl: string } | null;
}

interface CfFile {
  id: number;
  fileName: string;
  downloadUrl: string | null;
  hashes: Array<{ value: string; algo: number }>;
  dependencies: Array<{ modId: number; relationType: number }>;
}

async function cf<T>(path: string): Promise<T> {
  const key = await getCurseforgeKey();
  if (!key) throw new Error('Add a CurseForge API key in Settings to use CurseForge.');
  return getJson<T>(`${API}${path}`, { 'x-api-key': key });
}

export const curseforge: ModProvider = {
  async search(query, mcVersion, loaders, offset) {
    const params = new URLSearchParams({
      gameId: String(GAME_MINECRAFT),
      classId: String(CLASS_MODS),
      modLoaderType: String(loaderType(loaders)),
      gameVersion: mcVersion,
      searchFilter: query,
      sortField: String(SORT_POPULARITY),
      sortOrder: 'desc',
      pageSize: '20',
      index: String(offset)
    });
    const res = await cf<{ data: CfMod[] }>(`/mods/search?${params}`);
    return res.data.map((m) => ({
      source: 'curseforge' as const,
      id: String(m.id),
      slug: m.slug,
      title: m.name,
      description: m.summary,
      author: m.authors[0]?.name ?? 'Unknown',
      iconUrl: m.logo?.thumbnailUrl,
      downloads: m.downloadCount
    }));
  },

  async resolve(projectId, mcVersion, loaders) {
    const id = encodeURIComponent(projectId);
    const params = new URLSearchParams({
      gameVersion: mcVersion,
      modLoaderType: String(loaderType(loaders)),
      pageSize: '1'
    });
    const [files, mod] = await Promise.all([
      cf<{ data: CfFile[] }>(`/mods/${id}/files?${params}`),
      cf<{ data: CfMod }>(`/mods/${id}`)
    ]);
    const file = files.data[0];
    if (!file) throw new Error(`${mod.data.name} has no ${loaders.join('/')} build for ${mcVersion}.`);
    if (!file.downloadUrl) {
      // Authors can opt out of third-party distribution; the API then returns no URL.
      throw new Error(`${mod.data.name}'s author has disabled downloads from third-party launchers.`);
    }
    return {
      mod: {
        source: 'curseforge',
        projectId: String(mod.data.id),
        versionId: String(file.id),
        title: mod.data.name,
        fileName: file.fileName,
        url: file.downloadUrl,
        hashes: { sha1: file.hashes.find((h) => h.algo === HASH_SHA1)?.value }
      },
      requiredDeps: file.dependencies
        .filter((d) => d.relationType === RELATION_REQUIRED)
        .map((d) => String(d.modId))
    };
  }
};
