import { useEffect, useRef } from 'react';
import type { CrashInfo } from '@shared/types';
import { astro, errorText } from '../api';
import Icon from './Icon';

/** Shown when the game exits abnormally. All text arrives already redacted from the main process. */
export default function CrashDialog({
  crash,
  notify,
  onClose
}: {
  crash: CrashInfo;
  notify: (message: string, kind?: 'info' | 'error') => void;
  onClose: () => void;
}) {
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const act = (task: () => Promise<void>, ok?: string) =>
    task().then(
      () => ok && notify(ok),
      (err) => notify(errorText(err), 'error')
    );

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal confirm-dialog crash-dialog" role="alertdialog" aria-modal="true" aria-labelledby="crash-title" aria-describedby="crash-text">
        <div className="crash-head">
          <div className="confirm-icon danger">
            <Icon name="alert" size={22} />
          </div>
          <div className="grow">
            <p className="card-label">
              {crash.profileName} crashed{crash.exitCode !== null ? ` · exit code ${crash.exitCode}` : ''}
            </p>
            <h3 id="crash-title">{crash.title}</h3>
          </div>
        </div>
        <p id="crash-text">{crash.summary}</p>
        {crash.tips.length > 0 && (
          <ul className="crash-tips">
            {crash.tips.map((t) => (
              <li key={t}>{t}</li>
            ))}
          </ul>
        )}
        {crash.evidence.length > 0 && (
          <pre className="crash-evidence mono" aria-label="Matching log lines">
            {crash.evidence.join('\n')}
          </pre>
        )}
        <p className="muted small-text">Worked out on this PC from the game log. Nothing was uploaded.</p>
        <footer className="modal-footer crash-actions">
          {crash.crashReport && (
            <button className="btn-ghost small" onClick={() => act(() => astro.openCrashReport(crash.profileId, crash.crashReport!))}>
              <Icon name="file" size={13} /> Open crash report
            </button>
          )}
          <button className="btn-ghost small" onClick={() => act(() => astro.openLatestLog(crash.profileId))}>
            <Icon name="file" size={13} /> Open log
          </button>
          <button className="btn-ghost small" onClick={() => act(() => astro.copyCrashDetails(), 'Crash details copied (personal paths removed)')}>
            <Icon name="copy" size={13} /> Copy details
          </button>
          <span className="grow" />
          <button ref={closeRef} className="btn-primary" onClick={onClose}>
            Close
          </button>
        </footer>
      </div>
    </div>
  );
}
