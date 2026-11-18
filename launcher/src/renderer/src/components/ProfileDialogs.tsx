import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import type { Profile } from '@shared/types';
import type { AppState } from '../App';
import { astro, errorText } from '../api';
import Icon from './Icon';

function Dialog({ title, icon, onClose, busy, children }: { title: string; icon: 'copy' | 'download'; onClose: () => void; busy: boolean; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose, busy]);
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && !busy && onClose()}>
      <div className="modal confirm-dialog profile-dialog" role="dialog" aria-modal="true" aria-labelledby="profile-dialog-title">
        <div className="crash-head">
          <div className="confirm-icon">
            <Icon name={icon} size={20} />
          </div>
          <h3 id="profile-dialog-title">{title}</h3>
        </div>
        {children}
      </div>
    </div>
  );
}

export function DuplicateDialog({ app, profile, onClose }: { app: AppState; profile: Profile; onClose: () => void }) {
  const [name, setName] = useState(`${profile.name} (copy)`.slice(0, 48));
  const [copyConfig, setCopyConfig] = useState(true);
  const [busy, setBusy] = useState(false);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    astro.duplicateProfile(profile.id, { name, copyConfig }).then(
      (copy) => {
        app.upsert(copy);
        app.select(copy.id);
        app.notify(`Created ${copy.name} with ${copy.mods.length} mod(s)`);
        onClose();
      },
      (err) => {
        app.notify(errorText(err), 'error');
        setBusy(false);
      }
    );
  };

  return (
    <Dialog title={`Duplicate ${profile.name}`} icon="copy" onClose={onClose} busy={busy}>
      <form className="dialog-form" onSubmit={submit}>
        <label className="field">
          Name
          <input className="input" required maxLength={48} value={name} onChange={(e) => setName(e.target.value)} autoFocus />
        </label>
        {profile.loader !== 'vanilla' && (
          <label className="switch-row">
            <input type="checkbox" checked={copyConfig} onChange={(e) => setCopyConfig(e.target.checked)} />
            <span className="switch" aria-hidden="true" />
            <span>
              Also copy the config folder
              <span className="muted small-text switch-note">Keeps mod settings. Worlds, playtime and accounts are never copied.</span>
            </span>
          </label>
        )}
        <footer className="modal-footer">
          <button type="button" className="btn-ghost" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className="btn-primary" disabled={busy}>
            {busy ? 'Copying…' : 'Duplicate'}
          </button>
        </footer>
      </form>
    </Dialog>
  );
}

const CODE_LIMIT = 16_000;

export function ImportCodeDialog({ app, onClose }: { app: AppState; onClose: () => void }) {
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    astro.importShareCode(code.trim()).then(
      (result) => {
        app.upsert(result.profile);
        app.select(result.profile.id);
        app.notify(
          result.skipped.length
            ? `${result.profile.name} created with ${result.installed.length} mod(s); ${result.skipped.length} couldn't be installed`
            : `${result.profile.name} created with ${result.installed.length} mod(s)`,
          result.skipped.length ? 'error' : 'info'
        );
        onClose();
      },
      (err) => {
        setError(errorText(err));
        setBusy(false);
      }
    );
  };

  return (
    <Dialog title="Import a share code" icon="download" onClose={onClose} busy={busy}>
      <form className="dialog-form" onSubmit={submit}>
        <p className="muted small-text">
          Paste a code starting with <span className="mono">ASTRO1-</span>. It creates a new profile and downloads its mods from Modrinth.
        </p>
        <textarea
          className="input code-input mono"
          value={code}
          maxLength={CODE_LIMIT}
          onChange={(e) => setCode(e.target.value)}
          placeholder="ASTRO1-…"
          spellCheck={false}
          autoFocus
          required
          aria-invalid={error ? true : undefined}
        />
        {error && (
          <p className="form-error small-text" role="alert">
            {error}
          </p>
        )}
        <footer className="modal-footer">
          <button type="button" className="btn-ghost" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className="btn-primary" disabled={busy || !code.trim()}>
            {busy ? 'Installing mods…' : 'Import'}
          </button>
        </footer>
      </form>
    </Dialog>
  );
}
