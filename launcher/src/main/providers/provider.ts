import type { InstalledMod, ModSearchHit, ModSource } from '@shared/types';
import { curseforge } from './curseforge';
import { modrinth } from './modrinth';

export interface ResolvedMod {
  mod: InstalledMod;
  /** project ids (same source) this file hard-requires */
  requiredDeps: string[];
}

export interface ModProvider {
  /** `loaders` are the mod loaders the profile accepts, e.g. ['quilt', 'fabric']. */
  search(query: string, mcVersion: string, loaders: string[], offset: number): Promise<ModSearchHit[]>;
  /** Newest file of a project compatible with `mcVersion` and one of `loaders`. */
  resolve(projectId: string, mcVersion: string, loaders: string[]): Promise<ResolvedMod>;
}

export function provider(source: ModSource): ModProvider {
  return source === 'modrinth' ? modrinth : curseforge;
}
