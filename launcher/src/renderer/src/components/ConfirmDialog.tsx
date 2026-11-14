import { useEffect, useRef } from 'react';
import Icon from './Icon';

export interface ConfirmRequest {
  title: string;
  message: string;
  confirmLabel: string;
  danger?: boolean;
}

/** In-app replacement for window.confirm, styled like the rest of the launcher. */
export default function ConfirmDialog({ request, onAnswer }: { request: ConfirmRequest; onAnswer: (ok: boolean) => void }) {
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    // focus the safe choice first, so Enter never confirms a destructive action by accident
    cancelRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onAnswer(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onAnswer]);

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onAnswer(false)}>
      <div className="modal confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" aria-describedby="confirm-text">
        <div className={`confirm-icon ${request.danger ? 'danger' : ''}`}>
          <Icon name="alert" size={22} />
        </div>
        <div>
          <h3 id="confirm-title">{request.title}</h3>
          <p id="confirm-text" className="muted">
            {request.message}
          </p>
        </div>
        <footer className="modal-footer">
          <button ref={cancelRef} className="btn-ghost" onClick={() => onAnswer(false)}>
            Cancel
          </button>
          <button className={request.danger ? 'btn-danger solid' : 'btn-primary'} onClick={() => onAnswer(true)}>
            {request.confirmLabel}
          </button>
        </footer>
      </div>
    </div>
  );
}
