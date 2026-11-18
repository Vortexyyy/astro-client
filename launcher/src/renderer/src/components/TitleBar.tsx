import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { AppState } from '../App';
import { astro, errorText } from '../api';
import SkinHead from './SkinHead';
import Icon, { type IconName } from './Icon';

export type Page = 'home' | 'mods' | 'profiles' | 'console' | 'settings';

const NAV: Array<{ id: Page; label: string; icon: IconName }> = [
  { id: 'home', label: 'Home', icon: 'home' },
  { id: 'mods', label: 'Mods', icon: 'package' },
  { id: 'profiles', label: 'Profiles', icon: 'layers' },
  { id: 'console', label: 'Console', icon: 'terminal' },
  { id: 'settings', label: 'Settings', icon: 'settings' }
];

export default function TitleBar({ app, page }: { app: AppState; page: Page }) {
  const navRef = useRef<HTMLElement>(null);
  const [indicator, setIndicator] = useState<{ x: number; w: number } | null>(null);
  const updateCount = app.modUpdates && app.modUpdates.profileId === app.selectedId ? app.modUpdates.updates.length : 0;

  // The active-tab glow slides between tabs; measured from the DOM so it follows the real widths.
  useLayoutEffect(() => {
    const measure = () => {
      const el = navRef.current?.querySelector<HTMLElement>('.tab.active');
      if (el) setIndicator({ x: el.offsetLeft, w: el.offsetWidth });
    };
    measure();
    void document.fonts?.ready.then(measure);
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [page, updateCount > 0]);

  return (
    <header className="titlebar">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true">
          <span />
        </span>
        <span className="brand-name">ASTRO</span>
      </div>

      <nav className="tabs" aria-label="Main" ref={navRef}>
        {indicator && (
          <span
            className="tab-indicator"
            aria-hidden="true"
            style={{ transform: `translateX(${indicator.x}px)`, width: indicator.w }}
          />
        )}
        {NAV.map((n, i) => (
          <button
            key={n.id}
            className={`tab ${page === n.id ? 'active' : ''}`}
            onClick={() => app.navigate(n.id)}
            aria-current={page === n.id ? 'page' : undefined}
            title={
              n.id === 'profiles' && updateCount
                ? `${n.label}: ${updateCount} mod update${updateCount > 1 ? 's' : ''} available (Ctrl+${i + 1})`
                : `${n.label} (Ctrl+${i + 1})`
            }
          >
            <Icon name={n.icon} size={15} />
            {n.label}
            {n.id === 'console' && app.game.running && <span className="live-dot" title="Game running" />}
            {n.id === 'profiles' && updateCount > 0 && (
              <span className="tab-badge" aria-label={`${updateCount} mod updates`}>
                {updateCount > 99 ? '99+' : updateCount}
              </span>
            )}
          </button>
        ))}
      </nav>

      <div className="titlebar-right">
        <button className="icon-button titlebar-shortcuts" aria-label="Keyboard shortcuts" title="Keyboard shortcuts (?)" onClick={app.showShortcuts}>
          <Icon name="keyboard" size={17} />
        </button>
        <AccountMenu app={app} />
        <div className="window-controls">
          <button aria-label="Minimize" onClick={() => astro.windowControl('minimize')}>
            <svg viewBox="0 0 12 12"><path d="M2 6h8" /></svg>
          </button>
          <button aria-label="Maximize" onClick={() => astro.windowControl('maximize')}>
            <svg viewBox="0 0 12 12"><rect x="2" y="2" width="8" height="8" rx="1" /></svg>
          </button>
          <button className="close" aria-label="Close" onClick={() => astro.windowControl('close')}>
            <svg viewBox="0 0 12 12"><path d="M2.5 2.5l7 7M9.5 2.5l-7 7" /></svg>
          </button>
        </div>
      </div>
    </header>
  );
}

function AccountMenu({ app }: { app: AppState }) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const s = app.settings;
  const active = s?.accounts.find((a) => a.uuid === s.activeAccount) ?? null;

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    window.addEventListener('mousedown', close);
    return () => window.removeEventListener('mousedown', close);
  }, [open]);

  async function run(task: () => Promise<void>) {
    setBusy(true);
    try {
      await task();
    } catch (err) {
      app.notify(errorText(err), 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="account" ref={ref}>
      <button className="account-button" onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-haspopup="menu">
        {active ? (
          <>
            <SkinHead skinUrl={active.skinUrl} name={active.name} />
            <span className="account-name">{active.name}</span>
          </>
        ) : (
          <span className="account-name muted">{s?.offlineAllowed ? `${s.offlineName} · offline` : 'Sign in'}</span>
        )}
        <svg className="chevron" viewBox="0 0 12 12" aria-hidden="true"><path d="M3 4.5l3 3 3-3" /></svg>
      </button>

      {open && s && (
        <div className="account-menu" role="menu">
          <p className="menu-label">Accounts</p>
          {s.accounts.map((a) => (
            <div key={a.uuid} className={`account-row ${a.uuid === s.activeAccount ? 'active' : ''}`}>
              <button
                className="account-pick"
                role="menuitemradio"
                aria-checked={a.uuid === s.activeAccount}
                onClick={() => run(async () => app.setSettings(await astro.updateSettings({ activeAccount: a.uuid })))}
              >
                <SkinHead skinUrl={a.skinUrl} name={a.name} size={24} />
                <span>{a.name}</span>
                {a.uuid === s.activeAccount && <span className="pill">Active</span>}
              </button>
              <button
                className="icon-button"
                aria-label={`Remove ${a.name}`}
                title="Remove account"
                onClick={async () => {
                  if (
                    await app.confirm({
                      title: 'Remove account?',
                      message: `Sign out and remove ${a.name} from Astro?`,
                      confirmLabel: 'Remove',
                      danger: true
                    })
                  )
                    void run(async () => app.setSettings(await astro.removeAccount(a.uuid)));
                }}
              >
                <Icon name="x" />
              </button>
            </div>
          ))}
          {s.accounts.length === 0 && <p className="muted menu-empty">No accounts yet.</p>}
          <button
            className="btn-microsoft"
            disabled={busy}
            onClick={() =>
              run(async () => {
                app.setSettings(await astro.addAccount());
                app.notify('Signed in');
              })
            }
          >
            <MicrosoftLogo />
            {busy ? 'Waiting for Microsoft…' : 'Add Microsoft account'}
          </button>
        </div>
      )}
    </div>
  );
}

export function MicrosoftLogo() {
  return (
    <svg viewBox="0 0 21 21" width="16" height="16" aria-hidden="true">
      <rect x="1" y="1" width="9" height="9" fill="#f25022" />
      <rect x="11" y="1" width="9" height="9" fill="#7fba00" />
      <rect x="1" y="11" width="9" height="9" fill="#00a4ef" />
      <rect x="11" y="11" width="9" height="9" fill="#ffb900" />
    </svg>
  );
}
