import { useEffect, useRef } from 'react';
import Icon from './Icon';

export const SHORTCUTS: Array<{ keys: string[]; label: string }> = [
  { keys: ['Ctrl', 'Enter'], label: 'Launch the selected profile' },
  { keys: ['Ctrl', '1'], label: 'Home' },
  { keys: ['Ctrl', '2'], label: 'Mods' },
  { keys: ['Ctrl', '3'], label: 'Profiles' },
  { keys: ['Ctrl', '4'], label: 'Console' },
  { keys: ['Ctrl', '5'], label: 'Settings' },
  { keys: ['Ctrl', 'L'], label: 'Log helper: analyze a log' },
  { keys: ['?'], label: 'Show this list' },
  { keys: ['Esc'], label: 'Close dialogs' }
];

/** Cheat sheet for the launcher's keyboard shortcuts (opened with ? or the titlebar button). */
export default function ShortcutsDialog({ onClose, onOpenLogHelper }: { onClose: () => void; onOpenLogHelper: () => void }) {
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal shortcuts-dialog" role="dialog" aria-modal="true" aria-labelledby="shortcuts-title">
        <header className="modal-header">
          <div className="row">
            <span className="confirm-icon" aria-hidden="true">
              <Icon name="keyboard" size={20} />
            </span>
            <h3 id="shortcuts-title">Keyboard shortcuts</h3>
          </div>
          <button ref={closeRef} className="icon-button" aria-label="Close" onClick={onClose}>
            <Icon name="x" />
          </button>
        </header>
        <dl className="shortcut-list">
          {SHORTCUTS.map((s) => (
            <div key={s.label} className="shortcut-row">
              <dt>{s.label}</dt>
              <dd>
                {s.keys.map((k) => (
                  <kbd key={k}>{k}</kbd>
                ))}
              </dd>
            </div>
          ))}
        </dl>
        <footer className="modal-footer shortcuts-foot">
          <span className="muted small-text grow">Game crashed? Paste its log and get the cause in plain English.</span>
          <button className="btn-ghost small" onClick={onOpenLogHelper}>
            <Icon name="sparkles" size={13} /> Analyze a log
          </button>
        </footer>
      </div>
    </div>
  );
}
