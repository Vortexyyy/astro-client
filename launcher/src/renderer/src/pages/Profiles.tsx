import { useEffect, useRef, useState, type DragEvent, type FormEvent } from 'react';
import type { Bundle, LoaderId, ModUpdate, Profile } from '@shared/types';
import type { AppState } from '../App';
import { astro, errorText } from '../api';
import VersionSelect, { defaultVersion, useGameVersions } from '../components/VersionSelect';
import Icon from '../components/Icon';

const LOADERS: Array<{ id: LoaderId; label: string }> = [
  { id: 'fabric', label: 'Fabric' },
  { id: 'quilt', label: 'Quilt' },
  { id: 'vanilla', label: 'Vanilla' }
];
const BADGE: Record<string, string> = { modrinth: 'MR', curseforge: 'CF', local: 'LOCAL' };

export function loaderText(loader: LoaderId, version: string): string {
  return loader === 'vanilla' ? 'Vanilla' : `${loader === 'fabric' ? 'Fabric' : 'Quilt'} ${version}`;
}

const hasFiles = (e: DragEvent) => Array.from(e.dataTransfer.types).includes('Files');

/** Drag-and-drop (or pick) .jar files into a profile's mods folder. */
function useJarDrop(app: AppState, profile: Profile | null, run: (label: string, task: () => Promise<void>) => Promise<void>) {
  const [dragging, setDragging] = useState(false);
  const depth = useRef(0); // dragenter/leave fire for every child element
  const enabled = profile !== null && profile.loader !== 'vanilla';

  function addFiles(files: File[]) {
    if (!profile) return;
    const jars = files.map((f) => astro.pathForFile(f)).filter((p) => /\.jar$/i.test(p));
    const ignored = files.length - jars.length;
    if (!jars.length) return app.notify('Only .jar files can be added as mods.', 'error');
    void run('Adding mods', async () => {
      const result = await astro.addModFiles(profile.id, jars);
      app.upsert(result.profile);
      const failed = result.skipped.length + ignored;
      const added = result.installed.length ? `Added ${result.installed.join(', ')} to ${profile.name}` : 'No mods added';
      app.notify(failed ? `${added}. Skipped: ${[...result.skipped, ...(ignored ? [`${ignored} non-jar file(s)`] : [])].join('; ')}` : added, result.installed.length ? 'info' : 'error');
    });
  }

  const handlers = enabled
    ? {
        onDragEnter: (e: DragEvent) => {
          if (!hasFiles(e)) return;
          e.preventDefault();
          depth.current++;
          setDragging(true);
        },
        onDragOver: (e: DragEvent) => {
          if (!hasFiles(e)) return;
          e.preventDefault();
          e.dataTransfer.dropEffect = 'copy';
        },
        onDragLeave: () => {
          depth.current = Math.max(0, depth.current - 1);
          if (depth.current === 0) setDragging(false);
        },
        onDrop: (e: DragEvent) => {
          e.preventDefault();
          depth.current = 0;
          setDragging(false);
          addFiles(Array.from(e.dataTransfer.files));
        }
      }
    : {};
  return { dragging: enabled && dragging, handlers, addFiles };
}

export default function Profiles({ app }: { app: AppState }) {
  const profile = app.profiles.find((p) => p.id === app.selectedId) ?? null;
  const versions = useGameVersions();
  const [bundles, setBundles] = useState<Bundle[]>([]);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [mcVersion, setMcVersion] = useState('');
  const [loader, setLoader] = useState<LoaderId>('fabric');
  const [memoryGb, setMemoryGb] = useState(4);
  const [chosenBundles, setChosenBundles] = useState<Set<string>>(new Set(['essentials', 'performance']));
  const [updates, setUpdates] = useState<ModUpdate[] | null>(null);

  useEffect(() => {
    astro.listBundles().then(setBundles, () => undefined);
  }, []);

  useEffect(() => setUpdates(null), [app.selectedId]);

  useEffect(() => {
    if (!mcVersion && versions.length) setMcVersion(defaultVersion(versions) ?? '');
  }, [versions, mcVersion]);

  async function run(label: string, task: () => Promise<void>) {
    setBusy(label);
    try {
      await task();
    } catch (err) {
      app.notify(errorText(err), 'error');
    } finally {
      setBusy(null);
    }
  }

  const drop = useJarDrop(app, profile, run);
  const picker = useRef<HTMLInputElement>(null);

  const create = (e: FormEvent) => {
    e.preventDefault();
    void run('Creating profile', async () => {
      let p = await astro.createProfile({ name, mcVersion, loader, memoryMb: memoryGb * 1024 });
      app.upsert(p);
      app.select(p.id);
      setCreating(false);
      setName('');
      const skipped: string[] = [];
      if (loader !== 'vanilla') {
        for (const id of chosenBundles) {
          setBusy(`Installing ${bundles.find((b) => b.id === id)?.name ?? id}`);
          const result = await astro.installBundle(p.id, id);
          p = result.profile;
          app.upsert(p);
          skipped.push(...result.skipped);
        }
      }
      app.notify(
        skipped.length ? `${p.name} is ready. Not available for ${mcVersion}: ${skipped.map((s) => s.split(':')[0]).join(', ')}` : `${p.name} is ready`
      );
    });
  };

  const toggleBundle = (id: string) =>
    setChosenBundles((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <section className="page">
      <header className="page-header">
        <div>
          <h2>Profiles</h2>
          <p className="muted">Each profile has its own mods, configs and worlds.</p>
        </div>
        <div className="row">
          <button className="btn-ghost" onClick={app.openImport} disabled={Boolean(busy)}>
            Import from another launcher
          </button>
          <button className="btn-primary" onClick={() => setCreating((c) => !c)}>
            {creating ? 'Cancel' : 'New profile'}
          </button>
        </div>
      </header>

      {creating && (
        <form className="card create-form" onSubmit={create}>
          <div className="form-grid">
            <label>
              Name
              <input className="input" required maxLength={48} value={name} onChange={(e) => setName(e.target.value)} autoFocus />
            </label>
            <div className="field">
              Minecraft version
              <VersionSelect value={mcVersion} onChange={setMcVersion} />
            </div>
            <div className="field">
              Mod loader
              <div className="segmented" role="radiogroup" aria-label="Mod loader">
                {LOADERS.map((l) => (
                  <button
                    type="button"
                    key={l.id}
                    role="radio"
                    aria-checked={loader === l.id}
                    className={loader === l.id ? 'on' : ''}
                    onClick={() => setLoader(l.id)}
                  >
                    {l.label}
                  </button>
                ))}
              </div>
            </div>
            <label>
              Memory: {memoryGb} GB
              <input type="range" min={2} max={16} value={memoryGb} onChange={(e) => setMemoryGb(Number(e.target.value))} />
            </label>
          </div>

          {loader !== 'vanilla' && (
            <>
              <p className="card-label bundles-label">Start with bundles</p>
              <div className="bundle-grid">
                {bundles.map((b) => (
                  <label key={b.id} className={`bundle ${chosenBundles.has(b.id) ? 'on' : ''}`}>
                    <input type="checkbox" checked={chosenBundles.has(b.id)} onChange={() => toggleBundle(b.id)} />
                    <strong>{b.name}</strong>
                    <span className="muted small-text">{b.description}</span>
                    <span className="small-text bundle-mods">{b.mods.join(' · ')}</span>
                  </label>
                ))}
              </div>
            </>
          )}
          <div className="row">
            <button className="btn-primary" disabled={Boolean(busy)}>
              {busy ?? 'Create profile'}
            </button>
          </div>
        </form>
      )}

      <div className="profiles-layout">
        <ul className="profile-list">
          {app.profiles.map((p) => (
            <li key={p.id}>
              <button className={`profile-item ${p.id === app.selectedId ? 'active' : ''}`} onClick={() => app.select(p.id)}>
                <strong>{p.name}</strong>
                <span className="muted">
                  {p.mcVersion} · {p.loader === 'vanilla' ? 'Vanilla' : `${p.mods.length} mods`}
                </span>
              </button>
            </li>
          ))}
          {app.profiles.length === 0 && (
            <li className="muted empty-profiles">
              No profiles yet.{' '}
              <button className="link" onClick={app.openImport}>
                Import one from another launcher
              </button>
            </li>
          )}
        </ul>

        {profile && (
          <div className={`card profile-detail ${drop.dragging ? 'dropping' : ''}`} {...drop.handlers}>
            {drop.dragging && (
              <div className="drop-overlay" aria-hidden="true">
                <Icon name="download" size={28} />
                <strong>Drop .jar files to add them to {profile.name}</strong>
                <span className="muted">They're copied into this profile's mods folder</span>
              </div>
            )}
            <div className="page-header">
              <div>
                <h3>{profile.name}</h3>
                <p className="muted">
                  Minecraft {profile.mcVersion} · {loaderText(profile.loader, profile.loaderVersion)} · {profile.memoryMb / 1024} GB
                </p>
              </div>
              <div className="row">
                <button className="btn-ghost" onClick={() => run('Opening', () => astro.openProfileFolder(profile.id))}>
                  Open folder
                </button>
                <button
                  className="btn-ghost"
                  disabled={Boolean(busy)}
                  onClick={() =>
                    run('Exporting', async () => {
                      const path = await astro.exportProfile(profile.id);
                      if (path) app.notify(`Exported to ${path}`);
                    })
                  }
                >
                  Export
                </button>
                <button
                  className="btn-danger"
                  disabled={Boolean(busy)}
                  onClick={async () => {
                    if (
                      await app.confirm({
                        title: 'Delete profile?',
                        message: `Move "${profile.name}" (including its worlds) to the recycle bin?`,
                        confirmLabel: 'Move to recycle bin',
                        danger: true
                      })
                    )
                      void run('Deleting', async () => {
                        await astro.deleteProfile(profile.id);
                        await app.refresh();
                      });
                  }}
                >
                  Delete
                </button>
              </div>
            </div>

            {profile.loader !== 'vanilla' && (
              <>
                <div className="row spread mods-heading">
                  <h4>Mods · {profile.mods.length}</h4>
                  <span className="row">
                    <input
                      ref={picker}
                      type="file"
                      accept=".jar"
                      multiple
                      hidden
                      onChange={(e) => {
                        drop.addFiles(Array.from(e.target.files ?? []));
                        e.target.value = '';
                      }}
                    />
                    <button className="btn-ghost small" disabled={Boolean(busy)} onClick={() => picker.current?.click()}>
                      <Icon name="plus" size={13} /> Add .jar files
                    </button>
                  <button
                    className="btn-ghost small"
                    disabled={Boolean(busy) || profile.mods.length === 0}
                    onClick={() => run('Checking for updates', async () => setUpdates(await astro.checkUpdates(profile.id)))}
                  >
                    {busy === 'Checking for updates' ? 'Checking…' : 'Check for updates'}
                  </button>
                  </span>
                </div>

                {updates && (
                  <div className={`updates ${updates.length ? '' : 'none'}`}>
                    {updates.length === 0 ? (
                      <span>Everything is up to date.</span>
                    ) : (
                      <>
                        <div className="row spread">
                          <strong>
                            {updates.length} update{updates.length > 1 ? 's' : ''} available
                          </strong>
                          <button
                            className="btn-primary small"
                            disabled={Boolean(busy)}
                            onClick={() =>
                              run('Updating', async () => {
                                const result = await astro.applyUpdates(profile.id, updates.map((u) => u.projectId));
                                app.upsert(result.profile);
                                setUpdates([]);
                                app.notify(
                                  `Updated ${result.installed.length} mod(s)${result.skipped.length ? `; ${result.skipped.length} failed` : ''}`,
                                  result.skipped.length ? 'error' : 'info'
                                );
                              })
                            }
                          >
                            {busy === 'Updating' ? 'Updating…' : 'Update all'}
                          </button>
                        </div>
                        <ul>
                          {updates.map((u) => (
                            <li key={u.projectId}>
                              {u.title} <span className="muted update-arrow"><Icon name="arrowRight" size={12} /> {u.newVersion}</span>
                            </li>
                          ))}
                        </ul>
                      </>
                    )}
                  </div>
                )}

                {profile.mods.length === 0 && <p className="muted">No mods yet. Find some under Mods, or drag .jar files here.</p>}
                {profile.mods.length > 0 && <p className="drop-hint">Drag .jar files anywhere on this card to add them.</p>}
                <ul className="installed-list">
                  {profile.mods.map((m) => (
                    <li key={`${m.source}:${m.projectId}`}>
                      <span className={`badge ${m.source}`} title={m.source === 'local' ? 'Imported file not found on Modrinth' : m.source}>
                        {BADGE[m.source]}
                      </span>
                      <span className="grow">
                        {m.title}
                        <small className="muted"> {m.fileName}</small>
                      </span>
                      <button
                        className="btn-ghost small"
                        disabled={Boolean(busy)}
                        onClick={() => run('Removing', async () => app.upsert(await astro.removeMod(profile.id, m.projectId)))}
                      >
                        Remove
                      </button>
                    </li>
                  ))}
                </ul>
              </>
            )}
            {profile.loader === 'vanilla' && <p className="muted">Vanilla profile: no mod loader, so no mods.</p>}
          </div>
        )}
      </div>
    </section>
  );
}
