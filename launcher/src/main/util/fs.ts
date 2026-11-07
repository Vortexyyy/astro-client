import { resolve, sep } from 'node:path';

/** Resolves `rel` inside `base`, refusing anything that escapes it (zip-slip / path traversal). */
export function within(base: string, rel: string): string {
  const root = resolve(base);
  const target = resolve(root, rel);
  if (target !== root && !target.startsWith(root + sep)) throw new Error(`Blocked path outside ${root}: ${rel}`);
  return target;
}
