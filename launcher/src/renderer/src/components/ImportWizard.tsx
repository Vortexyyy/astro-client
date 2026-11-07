import { useEffect, useMemo, useState } from 'react';
import type { ImportCandidate, ImportOptions, ImportProgress, ImportReport } from '@shared/types';
import type { AppState } from '../App';
import { astro, errorText } from '../api';
import VersionSelect from './VersionSelect';
import Icon from './Icon';

const DEFAULT_OPTIONS: ImportOptions = {
  mods: true,
  configs: true,
  resourcePacks: true,
  shaderPacks: true,
  gameOptions: true,
  worlds: false
};

const OPTION_LABELS: Array<[keyof ImportOptions, string, string]> = [
  ['mods', 'Mods', 'Copied and matched on Modrinth for updates'],
  ['configs', 'Mod configs', 'config/ folder'],
  ['resourcePacks', 'Resource packs', ''],
  ['shaderPacks', 'Shader packs', ''],
  ['gameOptions', 'Options & servers', 'options.txt, keybinds, server list'],
  ['worlds', 'Worlds', 'Can be large']
];

const LOADER_LABEL: Record<ImportCandidate['loader'], string> = {
  fabric: 'Fabric',
  quilt: 'Quilt',
  vanilla: 'Vanilla',
  forge: 'Forge',
  neoforge: 'NeoForge',
  unknown: 'Unknown'
};

type Phase = 'scan' | 'pick' | 'importing' | 'done';

export default function ImportWizard({ app, onClose }: { app: AppState; onClose(): void }) {
  const [phase, setPhase] = useState<Phase>('scan');
  const [candidates, setCandidates] = useState<ImportCandidate[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [versions, setVersions] = useState<Record<string, string>>({});
  const [options, setOptions] = useState<ImportOptions>(DEFAULT_OPTIONS);
  const [progress, setProgress] = useState<ImportProgress | null>(null);
  const [reports, setReports] = useState<ImportReport[]>([]);

  useEffect(() => {
    astro.scanLaunchers().then(
      (found) => {
        setCandidates(found);
        setPhase('pick');
      },
      (err) => {
        app.notify(errorText(err), 'error');
        setPhase('pick');
      }
    );
    return astro.onImportProgress(setProgress);
    // scan once when the wizard opens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && phase !== 'importing' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [phase, onClose]);

  const groups = useMemo(() => {
    const map = new Map<string, ImportCandidate[]>();
    for (const c of candidates) map.set(c.sourceLabel, [...(map.get(c.sourceLabel) ?? []), c]);
    return [...map.entries()];
  }, [candidates]);

  const versionOf = (c: ImportCandidate) => versions[c.key] ?? c.mcVersion ?? '';
  const ready = [...selected].every((key) => {
    const c = candidates.find((x) => x.key === key);
    return c && versionOf(c);
  });

  const toggle = (key: string) =>
    setSelected((s) => {
      const next = new Set(s);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  async function browse() {
    try {
      const found = await astro.pickImportFolder();
      if (!found.length) return;
      setCandidates((c) => [...c, ...found]);
      setSelected((s) => new Set([...s, ...found.map((f) => f.key)]));
    } catch (err) {
      app.notify(errorText(err), 'error');
    }
  }

  async function modpackFile() {
    try {
      const result = await astro.importProfile();
      if (!result) return;
      app.upsert(result.profile);
      app.select(result.profile.id);
      app.notify(`Imported ${result.profile.name}`);
      onClose();
    } catch (err) {
      app.notify(errorText(err), 'error');
    }
  }

  async function run() {
    setPhase('importing');
    const requests = candidates
      .filter((c) => selected.has(c.key))
      .map((c) => ({ key: c.key, name: c.name, mcVersion: versionOf(c) }));
    try {
      const result = await astro.importInstances(requests, options);
      setReports(result);
      for (const r of result) if (r.profile) app.upsert(r.profile);
      const first = result.find((r) => r.profile)?.profile;
      if (first) app.select(first.id);
    } catch (err) {
      app.notify(errorText(err), 'error');
    }
    setPhase('done');
  }

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && phase !== 'importing' && onClose()}>
      <div className="modal import-wizard" role="dialog" aria-modal="true" aria-labelledby="import-title">
        <header className="modal-header">
          <div>
            <h2 id="import-title">Import into Astro</h2>
            <p className="muted small-text">
              Bring profiles, mods, configs and worlds over from other launchers. Your originals are never changed.
            </p>
          </div>
          <button className="icon-button" aria-label="Close" onClick={onClose} disabled={phase === 'importing'}>
            <Icon name="x" />
          </button>
        </header>

        {phase === 'scan' && <p className="muted center scan-note">Looking for other launchers on this computer…</p>}

        {phase === 'pick' && (
          <>
            <div className="import-list">
              {groups.length === 0 && (
                <p className="muted center scan-note">No other launchers found. Choose a folder or a modpack file instead.</p>
              )}
              {groups.map(([label, items]) => (
                <section key={label} className="import-group">
                  <h4>
                    {label} <span className="muted">· {items.length}</span>
                  </h4>
                  {items.map((c) => (
                    <label key={c.key} className={`import-row ${selected.has(c.key) ? 'on' : ''}`}>
                      <input type="checkbox" checked={selected.has(c.key)} onChange={() => toggle(c.key)} />
                      <span className="check" aria-hidden="true" />
                      <span className="import-main">
                        <strong>{c.name}</strong>
                        <span className="muted small-text path" title={c.gameDir}>
                          {c.gameDir}
                        </span>
                        {c.extras.length > 0 && <span className="small-text extras">HUD settings: {c.extras.join(', ')}</span>}
                        {c.warning && <span className="small-text warn-text">{c.warning}</span>}
                      </span>
                      <span className={`loader-badge ${c.loader}`}>{LOADER_LABEL[c.loader]}</span>
                      {c.mcVersion && !versions[c.key] ? (
                        <span className="version-tag">{c.mcVersion}</span>
                      ) : (
                        <span onClick={(e) => e.preventDefault()}>
                          <VersionSelect
                            className="input version-select"
                            value={versionOf(c)}
                            onChange={(v) => setVersions((all) => ({ ...all, [c.key]: v }))}
                            allowSnapshots={false}
                            placeholder="Pick version…"
                            ariaLabel={`Minecraft version for ${c.name}`}
                          />
                        </span>
                      )}
                      <span className="counts small-text muted">
                        {c.modCount} mods
                        <br />
                        {c.worldCount} worlds
                      </span>
                    </label>
                  ))}
                </section>
              ))}
            </div>

            <div className="import-options">
              {OPTION_LABELS.map(([key, label, hint]) => (
                <label key={key} className="switch-row compact" title={hint}>
                  <input type="checkbox" checked={options[key]} onChange={(e) => setOptions((o) => ({ ...o, [key]: e.target.checked }))} />
                  <span className="switch" aria-hidden="true" />
                  {label}
                </label>
              ))}
            </div>

            <footer className="modal-footer">
              <button className="btn-ghost" onClick={browse}>
                Choose folder…
              </button>
              <button className="btn-ghost" onClick={modpackFile}>
                Modpack file…
              </button>
              <span className="grow" />
              <button className="btn-primary" disabled={!selected.size || !ready} onClick={run}>
                {selected.size ? `Import ${selected.size} profile${selected.size > 1 ? 's' : ''}` : 'Select profiles'}
              </button>
            </footer>
          </>
        )}

        {phase === 'importing' && (
          <div className="import-progress">
            <p>
              <strong>{progress?.name ?? 'Starting…'}</strong>
              {progress && (
                <span className="muted">
                  {' '}
                  ({progress.index + 1} of {progress.total})
                </span>
              )}
            </p>
            <p className="muted">{progress?.step ?? 'Preparing'}…</p>
            <div className="progress indeterminate">
              <div />
            </div>
          </div>
        )}

        {phase === 'done' && (
          <>
            <ul className="import-results">
              {reports.map((r, i) => (
                <li key={i} className={r.error ? 'failed' : ''}>
                  <strong>{r.candidateName}</strong>
                  {r.error ? (
                    <span className="warn-text">{r.error}</span>
                  ) : (
                    <>
                      <span className="muted small-text">
                        Copied {r.copied.join(', ') || 'nothing'}.{' '}
                        {r.identifiedMods + r.localMods > 0 &&
                          `${r.identifiedMods} of ${r.identifiedMods + r.localMods} mods matched on Modrinth (those get update checks).`}
                      </span>
                      {r.warnings.map((w) => (
                        <span key={w} className="small-text warn-text">
                          {w}
                        </span>
                      ))}
                    </>
                  )}
                </li>
              ))}
            </ul>
            <footer className="modal-footer">
              <span className="grow" />
              <button className="btn-primary" onClick={onClose}>
                Done
              </button>
            </footer>
          </>
        )}
      </div>
    </div>
  );
}
