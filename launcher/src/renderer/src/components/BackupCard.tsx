import { useEffect, useState } from 'react';
import type { BackupStatus } from '@shared/types';
import type { AppState } from '../App';
import { astro, errorText } from '../api';

function ago(ms: number | null): string {
  if (!ms) return 'not yet';
  const s = Math.round((Date.now() - ms) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.round(s / 60)} min ago`;
  return new Date(ms).toLocaleString();
}

/** Settings card for the serverless backup: a JSON file in a folder the player's cloud drive syncs. */
export default function BackupCard({ app }: { app: AppState }) {
  const [status, setStatus] = useState<BackupStatus | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [restoreIds, setRestoreIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    astro.backupStatus().then(setStatus, () => undefined);
    return astro.onBackupStatus(setStatus);
  }, []);

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

  if (!status) return null;
  const elsewhere = status.profiles.filter((p) => !p.onThisDevice);

  return (
    <div className="card backup-card">
      <div className="row spread">
        <div>
          <h3>Backup &amp; sync</h3>
          <p className="muted small-text">
            Pick a folder inside OneDrive, Google Drive or Dropbox and Astro keeps your profiles, servers and settings backed up
            there. On another PC, pick the same folder to restore them. No account needed.
          </p>
        </div>
      </div>

      {status.folder ? (
        <>
          <div className="row spread backup-folder">
            <span className="mono small-text grow" title={status.folder}>
              {status.folder}
            </span>
            <span className={`sync-state ${status.error ? 'error' : ''}`}>
              {status.busy ? 'Backing up…' : status.error ? 'Backup failed' : `Backed up ${ago(status.lastBackupAt)}`}
            </span>
          </div>
          {status.error && <p className="small-text warn-text">{status.error}</p>}
          <div className="row">
            <button className="btn-ghost small" disabled={status.busy || Boolean(busy)} onClick={() => run('Backing up', async () => setStatus(await astro.backupNow()))}>
              Back up now
            </button>
            <button className="btn-ghost small" disabled={Boolean(busy)} onClick={() => run('Choosing', async () => setStatus(await astro.backupChooseFolder()))}>
              Change folder
            </button>
            <button className="btn-ghost small" disabled={Boolean(busy)} onClick={() => run('Stopping', async () => setStatus(await astro.backupStop()))}>
              Turn off
            </button>
          </div>
        </>
      ) : (
        <div className="row">
          <button className="btn-primary" disabled={Boolean(busy)} onClick={() => run('Choosing', async () => setStatus(await astro.backupChooseFolder()))}>
            Choose backup folder
          </button>
        </div>
      )}

      {elsewhere.length > 0 && (
        <div className="cloud-restore">
          <p className="card-label">In the backup, not on this PC</p>
          {elsewhere.map((p) => (
            <label key={p.id} className="switch-row compact">
              <input
                type="checkbox"
                checked={restoreIds.has(p.id)}
                onChange={(e) =>
                  setRestoreIds((s) => {
                    const next = new Set(s);
                    if (e.target.checked) next.add(p.id);
                    else next.delete(p.id);
                    return next;
                  })
                }
              />
              <span className="switch" aria-hidden="true" />
              {p.name} <span className="muted">· {p.mcVersion} · {p.modCount} mods</span>
            </label>
          ))}
          <button
            className="btn-primary small"
            disabled={!restoreIds.size || Boolean(busy)}
            onClick={() =>
              run('Restoring', async () => {
                const result = await astro.backupRestore([...restoreIds]);
                for (const p of result.restored) app.upsert(p);
                setRestoreIds(new Set());
                app.notify(
                  `Restored ${result.restored.length} profile(s)${result.skipped.length ? `; ${result.skipped.length} file(s) couldn't be restored` : ''}`,
                  result.skipped.length ? 'error' : 'info'
                );
              })
            }
          >
            {busy === 'Restoring' ? 'Restoring…' : 'Restore on this PC'}
          </button>
        </div>
      )}
      <p className="muted small-text">
        Saved: profiles and their mod lists, servers, launcher preferences, Minecraft account names. Never saved: passwords,
        Microsoft tokens, API keys, worlds. To share a profile with friends, use Profiles → Export and send them the file.
      </p>
    </div>
  );
}
