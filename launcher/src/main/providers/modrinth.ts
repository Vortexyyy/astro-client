import { getJson, postJson } from '../net/http';
import type { ModProvider } from './provider';

const API = 'https://api.modrinth.com/v2';

interface SearchResponse {
  hits: Array<{
    project_id: string;
    slug: string;
    title: string;
    description: string;
    author: string;
    icon_url: string | null;
    downloads: number;
  }>;
}

interface Version {
  id: string;
  project_id: string;
  name: string;
  files: Array<{ url: string; filename: string; primary: boolean; hashes: { sha1?: string; sha512?: string } }>;
  dependencies: Array<{ project_id: string | null; dependency_type: 'required' | 'optional' | 'incompatible' | 'embedded' }>;
}

interface Project {
  title: string;
}

const q = (value: unknown) => encodeURIComponent(JSON.stringify(value));

export const modrinth: ModProvider = {
  async search(query, mcVersion, loaders, offset) {
    const facets = [['project_type:mod'], loaders.map((l) => `categories:${l}`), [`versions:${mcVersion}`]];
    const url = `${API}/search?query=${encodeURIComponent(query)}&facets=${q(facets)}&limit=20&offset=${offset}`;
    const res = await getJson<SearchResponse>(url);
    return res.hits.map((h) => ({
      source: 'modrinth' as const,
      id: h.project_id,
      slug: h.slug,
      title: h.title,
      description: h.description,
      author: h.author,
      iconUrl: h.icon_url ?? undefined,
      downloads: h.downloads
    }));
  },

  async resolve(projectId, mcVersion, loaders) {
    const id = encodeURIComponent(projectId);
    const [versions, project] = await Promise.all([
      getJson<Version[]>(`${API}/project/${id}/version?loaders=${q(loaders)}&game_versions=${q([mcVersion])}`),
      getJson<Project>(`${API}/project/${id}`)
    ]);
    const version = versions[0]; // newest first
    if (!version) throw new Error(`${project.title} has no ${loaders.join('/')} build for ${mcVersion}.`);
    const file = version.files.find((f) => f.primary) ?? version.files[0];
    return {
      mod: {
        source: 'modrinth',
        projectId: version.project_id,
        versionId: version.id,
        title: project.title,
        fileName: file.filename,
        url: file.url,
        hashes: { sha1: file.hashes.sha1, sha512: file.hashes.sha512 }
      },
      requiredDeps: version.dependencies
        .filter((d) => d.dependency_type === 'required' && d.project_id)
        .map((d) => d.project_id!)
    };
  }
};

// ── Bulk lookups by file hash ───────────────────────────────────────────

const HASH_BATCH = 200;

async function batched<T>(hashes: string[], fetchBatch: (batch: string[]) => Promise<Record<string, T>>): Promise<Record<string, T>> {
  const out: Record<string, T> = {};
  for (let i = 0; i < hashes.length; i += HASH_BATCH) Object.assign(out, await fetchBatch(hashes.slice(i, i + HASH_BATCH)));
  return out;
}

/** Which Modrinth version (if any) each local jar is, keyed by SHA-1. */
export function modrinthVersionsByHash(hashes: string[]): Promise<Record<string, Version & { version_number: string }>> {
  if (!hashes.length) return Promise.resolve({});
  return batched(hashes, (batch) => postJson(`${API}/version_files`, { hashes: batch, algorithm: 'sha1' }));
}

/** Newest version compatible with the profile for each installed file, keyed by SHA-1. */
export function modrinthLatestByHash(
  hashes: string[],
  mcVersion: string,
  loaders: string[]
): Promise<Record<string, Version & { version_number: string }>> {
  if (!hashes.length) return Promise.resolve({});
  return batched(hashes, (batch) =>
    postJson(`${API}/version_files/update`, { hashes: batch, algorithm: 'sha1', loaders, game_versions: [mcVersion] })
  );
}

export async function modrinthProjectTitles(ids: string[]): Promise<Record<string, string>> {
  const out: Record<string, string> = {};
  for (let i = 0; i < ids.length; i += 100) {
    const batch = ids.slice(i, i + 100);
    const projects = await getJson<Array<{ id: string; title: string }>>(`${API}/projects?ids=${q(batch)}`);
    for (const p of projects) out[p.id] = p.title;
  }
  return out;
}

/** One exact Modrinth version (used when importing a share code). */
export function modrinthVersion(versionId: string): Promise<Version & { version_number: string; game_versions: string[]; loaders: string[] }> {
  return getJson(`${API}/version/${encodeURIComponent(versionId)}`);
}
