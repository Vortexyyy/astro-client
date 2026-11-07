import { useEffect, useState } from 'react';
import type { GameVersionInfo } from '@shared/types';
import { astro } from '../api';

let cache: GameVersionInfo[] | null = null;

/** All Minecraft versions, loaded once per session. */
export function useGameVersions(): GameVersionInfo[] {
  const [versions, setVersions] = useState<GameVersionInfo[]>(cache ?? []);
  useEffect(() => {
    if (cache) return;
    astro.listGameVersions().then((v) => {
      cache = v;
      setVersions(v);
    }, () => undefined);
  }, []);
  return versions;
}

/** Newest version with an Astro Client build (what new profiles should default to). */
export function defaultVersion(versions: GameVersionInfo[]): string | null {
  return versions.find((v) => v.astro)?.id ?? versions.find((v) => v.type === 'release')?.id ?? null;
}

interface Props {
  value: string;
  onChange(version: string): void;
  allowSnapshots?: boolean;
  placeholder?: string;
  className?: string;
  ariaLabel?: string;
}

/**
 * Version dropdown: versions with Astro Client first, then every release, then (optionally) snapshots.
 */
export default function VersionSelect({ value, onChange, allowSnapshots = true, placeholder, className = 'input', ariaLabel }: Props) {
  const versions = useGameVersions();
  const [snapshots, setSnapshots] = useState(false);
  const astroReady = versions.filter((v) => v.astro);
  const releases = versions.filter((v) => v.type === 'release' && !v.astro);
  const snaps = versions.filter((v) => v.type === 'snapshot');
  const known = versions.some((v) => v.id === value);

  return (
    <span className="version-select-wrap">
      <select className={className} value={value} onChange={(e) => onChange(e.target.value)} aria-label={ariaLabel ?? 'Minecraft version'}>
        {placeholder && <option value="">{placeholder}</option>}
        {!known && value && <option value={value}>{value}</option>}
        {astroReady.length > 0 && (
          <optgroup label="★ Astro Client ready">
            {astroReady.map((v) => (
              <option key={v.id} value={v.id}>
                {v.id} ★
              </option>
            ))}
          </optgroup>
        )}
        <optgroup label="Releases">
          {releases.map((v) => (
            <option key={v.id}>{v.id}</option>
          ))}
        </optgroup>
        {allowSnapshots && snapshots && (
          <optgroup label="Snapshots">
            {snaps.map((v) => (
              <option key={v.id}>{v.id}</option>
            ))}
          </optgroup>
        )}
      </select>
      {allowSnapshots && (
        <label className="snapshot-toggle small-text muted">
          <input type="checkbox" checked={snapshots} onChange={(e) => setSnapshots(e.target.checked)} /> Show snapshots
        </label>
      )}
    </span>
  );
}
