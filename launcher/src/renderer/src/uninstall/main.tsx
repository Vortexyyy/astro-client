import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import type { UninstallApi, UninstallInfo, UninstallItemId, UninstallResult } from '@shared/types';
import '../fonts';
import '../styles.css';
import './uninstall.css';

declare global {
  interface Window {
    astroUninstall: UninstallApi;
  }
}

const api = window.astroUninstall;

function formatSize(bytes: number): string {
  if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
  if (bytes >= 1024 ** 2) return `${Math.round(bytes / 1024 ** 2)} MB`;
  if (bytes > 0) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return 'empty';
}

type Phase = 'choose' | 'confirm' | 'working' | 'done';

function Uninstaller() {
  const [info, setInfo] = useState<UninstallInfo | null>(null);
  const [selected, setSelected] = useState<Set<UninstallItemId>>(new Set());
  const [phase, setPhase] = useState<Phase>('choose');
  const [result, setResult] = useState<UninstallResult | null>(null);

  useEffect(() => {
    api.info().then((i) => {
      setInfo(i);
      setSelected(new Set(i.items.filter((x) => x.defaultOn).map((x) => x.id)));
    });
  }, []);

  const toggle = (id: UninstallItemId) =>
    setSelected((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const freed = info?.items.filter((i) => selected.has(i.id)).reduce((n, i) => n + i.bytes, 0) ?? 0;

  async function uninstall() {
    setPhase('working');
    setResult(await api.run([...selected]));
    setPhase('done');
  }

  return (
    <div className="uninstall">
      <header className="u-titlebar">
        <span className="u-mark" aria-hidden="true">
          <span />
        </span>
        <span className="u-brand">ASTRO</span>
        <button className="u-close" aria-label="Close" onClick={() => api.close()} disabled={phase === 'working'}>
          <svg viewBox="0 0 12 12">
            <path d="M2.5 2.5l7 7M9.5 2.5l-7 7" />
          </svg>
        </button>
      </header>

      <main className="u-body">
        {phase === 'done' ? (
          <section className="u-done">
            <div className="u-planet" aria-hidden="true" />
            <h1>{result?.removedApp ? 'Astro is being removed' : 'Clean-up finished'}</h1>
            <p className="muted">
              {result?.removedApp
                ? 'Thanks for flying with Astro. This window closes in a moment.'
                : (result?.note ?? 'Done.')}
            </p>
            {result && result.failed.length > 0 && (
              <p className="small-text warn-text">Some files were in use and were left behind: {result.failed.join(', ')}</p>
            )}
            {!result?.removedApp && (
              <button className="btn-primary" onClick={() => api.close()}>
                Close
              </button>
            )}
          </section>
        ) : (
          <>
            <h1>Uninstall Astro Launcher</h1>
            <p className="muted small-text">
              Choose what to remove from this PC. The launcher itself is always removed.
              {info && !info.packaged && ' (Development build: only your data is cleaned up.)'}
            </p>

            <div className="u-items" role="group" aria-label="What to remove">
              {!info && <p className="muted">Measuring folders…</p>}
              {info?.items.map((item) => (
                <label key={item.id} className={`u-item ${selected.has(item.id) ? 'on' : ''}`}>
                  <input
                    type="checkbox"
                    checked={selected.has(item.id)}
                    onChange={() => toggle(item.id)}
                    disabled={phase !== 'choose'}
                  />
                  <span className="check" aria-hidden="true" />
                  <span className="u-item-text">
                    <strong>{item.label}</strong>
                    <span className="muted small-text">{item.description}</span>
                  </span>
                  <span className="u-size">{formatSize(item.bytes)}</span>
                </label>
              ))}
            </div>

            {phase === 'confirm' && (
              <div className="callout error u-confirm">
                {selected.has('profiles')
                  ? 'Your profiles and worlds will go to the Recycle Bin. '
                  : 'Your profiles and worlds will be kept. '}
                Astro Launcher will be removed{freed > 0 ? ` and ${formatSize(freed)} freed` : ''}. Continue?
              </div>
            )}
          </>
        )}
      </main>

      {phase !== 'done' && (
        <footer className="u-footer">
          <span className="muted small-text grow">{info ? `Installed in ${info.installDir}` : ''}</span>
          <button className="btn-ghost" onClick={() => (phase === 'confirm' ? setPhase('choose') : api.close())} disabled={phase === 'working'}>
            {phase === 'confirm' ? 'Back' : 'Cancel'}
          </button>
          <button
            className="btn-danger u-go"
            disabled={!info || phase === 'working'}
            onClick={() => (phase === 'confirm' ? void uninstall() : setPhase('confirm'))}
          >
            {phase === 'working' ? 'Removing…' : phase === 'confirm' ? 'Yes, uninstall' : 'Uninstall'}
          </button>
        </footer>
      )}
    </div>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Uninstaller />
  </StrictMode>
);
