import type { BatchResult, Bundle } from '@shared/types';
import { getProfile, installInto, saveProfile } from './store';

/** Curated one-click mod sets (Modrinth slugs). Mods missing for a version are skipped, not fatal. */
export const BUNDLES: Bundle[] = [
  {
    id: 'essentials',
    name: 'Essentials',
    description: 'Fabric API and Mod Menu, which most mods and Astro rely on.',
    mods: ['fabric-api', 'modmenu']
  },
  {
    id: 'performance',
    name: 'Performance',
    description: 'Faster rendering, less memory and higher FPS.',
    mods: ['sodium', 'lithium', 'ferrite-core', 'immediatelyfast', 'entityculling']
  },
  {
    id: 'shaders',
    name: 'Shaders',
    description: 'Iris shader support (pulls in Sodium).',
    mods: ['iris']
  },
  {
    id: 'quality',
    name: 'Quality of life',
    description: 'Hunger previews, inventory tweaks and better tooltips.',
    mods: ['appleskin', 'mouse-tweaks', 'jade']
  }
];

export async function installBundle(profileId: string, bundleId: string): Promise<BatchResult> {
  const bundle = BUNDLES.find((b) => b.id === bundleId);
  if (!bundle) throw new Error('Unknown bundle');
  const profile = await getProfile(profileId);
  const installed: string[] = [];
  const skipped: string[] = [];
  for (const slug of bundle.mods) {
    try {
      await installInto(profile, 'modrinth', slug);
      installed.push(slug);
    } catch (err) {
      skipped.push(`${slug}: ${(err as Error).message}`);
    }
    await saveProfile(profile); // keep progress if a later mod fails
  }
  return { profile, installed, skipped };
}
