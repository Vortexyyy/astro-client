import { useEffect, useState, type FormEvent } from 'react';
import type { JavaMode, LauncherTheme, UpdateState } from '@shared/types';
import type { AppState } from '../App';
import { astro, errorText } from '../api';
import BackupCard from '../components/BackupCard';
import Icon from '../components/Icon';

const THEMES: Array<{ id: LauncherTheme; name: string; note: string }> = [
  { id: 'nebula', name: 'Nebula', note: 'Violet with a cyan glow' },
  { id: 'aurora', name: 'Aurora', note: 'Cyan lead, violet trim' },
  { id: 'amethyst', name: 'Amethyst', note: 'Deep purple and lavender' }
];
import SkinHead from '../components/SkinHead';
import { MicrosoftLogo } from '../components/TitleBar';

export default function Settings({ app }: { app: AppState }) {
  const s = app.settings;
  const [javaMode, setJavaMode] = useState<JavaMode>('bundled');
  const [javaPath, setJavaPath] = useState('');
  const [hideOnLaunch, setHideOnLaunch] = useState(false);
  const [autoInstallAstro, setAutoInstallAstro] = useState(true);
  const [offlineName, setOfflineName] = useState('');
  const [cfKey, setCfKey] = useState('');
  const [busy, setBusy] = useState(false);
  const [version, setVersion] = useState('');

  const [update, setUpdate] = useState<UpdateState>({ status: 'idle' });

  useEffect(() => {
    astro.appVersion().then(setVersion, () => undefined);
    astro.updateState().then(setUpdate, () => undefined);
    return astro.onUpdateState(setUpdate);
  }, []);

  // Re-sync the form only when its own saved values change, so switching the theme or an account
  // doesn't wipe edits that haven't been saved yet.
  useEffect(() => {
    if (!s) return;
    setJavaMode(s.javaMode);
    setJavaPath(s.javaPath);
    setHideOnLaunch(s.hideOnLaunch);
    setAutoInstallAstro(s.autoInstallAstro);
    setOfflineName(s.offlineName);
  }, [s?.javaMode, s?.javaPath, s?.hideOnLaunch, s?.autoInstallAstro, s?.offlineName]);

  /** Appearance and mod-update switches apply at once (optimistically) and save in the background. */
  const setInstant = (patch: { theme?: LauncherTheme; reduceEffects?: boolean; checkModUpdates?: boolean; autoUpdateMods?: boolean }) => {
    if (!s) return;
    const previous = s;
    app.setSettings({ ...s, ...patch });
    astro.updateSettings(patch).then(app.setSettings, (err) => {
      app.setSettings(previous);
      app.notify(errorText(err), 'error');
    });
  };

  async function run(task: () => Promise<void>, ok?: string) {
    setBusy(true);
    try {
      await task();
      if (ok) app.notify(ok);
    } catch (err) {
      app.notify(errorText(err), 'error');
    } finally {
      setBusy(false);
    }
  }

  const save = (e: FormEvent) => {
    e.preventDefault();
    void run(async () => {
      app.setSettings(
        await astro.updateSettings({
          javaMode,
          javaPath,
          hideOnLaunch,
          autoInstallAstro,
          ...(s?.offlineAllowed ? { offlineName } : {}),
          ...(cfKey ? { curseforgeApiKey: cfKey } : {})
        })
      );
      setCfKey('');
    }, 'Settings saved');
  };

  if (!s) return null;

  return (
    <section className="page settings">
      <header className="page-header">
        <div>
          <h2>Settings</h2>
          <p className="muted">Tokens and keys are encrypted with your operating system's keychain.</p>
        </div>
        {version && <span className="pill version-pill">Astro Launcher v{version}</span>}
      </header>

      <div className="card update-card">
        <div className="grow">
          <h3>Launcher updates</h3>
          <p className="muted small-text">{updateText(update, version)}</p>
          {update.status === 'downloading' && (
            <div className="progress">
              <div style={{ width: `${update.percent ?? 0}%` }} />
            </div>
          )}
        </div>
        {update.status === 'ready' ? (
          <button className="btn-primary" onClick={() => void astro.installUpdate()}>
            Restart to update
          </button>
        ) : (
          <button
            className="btn-ghost"
            disabled={update.status === 'checking' || update.status === 'downloading' || update.status === 'disabled'}
            onClick={() => astro.checkForUpdates().then(setUpdate, (err) => app.notify(errorText(err), 'error'))}
          >
            {update.status === 'checking' ? 'Checking…' : 'Check for updates'}
          </button>
        )}
      </div>

      <div className="card appearance-card">
        <div className="row spread">
          <div>
            <h3>Appearance</h3>
            <p className="muted small-text">Pick an accent. It applies right away and is remembered.</p>
          </div>
          <button type="button" className="btn-ghost small" onClick={app.showShortcuts}>
            <Icon name="keyboard" size={14} />
            Keyboard shortcuts
          </button>
        </div>
        <div className="theme-grid" role="radiogroup" aria-label="Accent theme">
          {THEMES.map((t) => (
            <label key={t.id} className={`theme-option ${s.theme === t.id ? 'on' : ''}`} data-preview={t.id}>
              <input type="radio" name="theme" checked={s.theme === t.id} onChange={() => setInstant({ theme: t.id })} />
              <span className="theme-swatch" aria-hidden="true">
                <span />
              </span>
              <span className="theme-text">
                <strong>{t.name}</strong>
                <span className="muted small-text">{t.note}</span>
              </span>
              <Icon name="check" size={16} className="theme-check" />
            </label>
          ))}
        </div>
        <label className="switch-row">
          <input type="checkbox" checked={s.reduceEffects} onChange={(e) => setInstant({ reduceEffects: e.target.checked })} />
          <span className="switch" aria-hidden="true" />
          <span>
            Reduce effects
            <span className="muted small-text switch-note">Stills the starfield and animations and turns off glass blur. Good for slower PCs.</span>
          </span>
        </label>
      </div>

      <div className="card mod-updates-card">
        <div>
          <h3>Mod updates</h3>
          <p className="muted small-text">Uses the same check as the Profiles page. Applies right away.</p>
        </div>
        <label className="switch-row">
          <input type="checkbox" checked={s.checkModUpdates} onChange={(e) => setInstant({ checkModUpdates: e.target.checked })} />
          <span className="switch" aria-hidden="true" />
          <span>
            Check mod updates on launch
            <span className="muted small-text switch-note">
              Checks the selected profile when Astro starts and before each launch, and shows a count on the Profiles tab.
            </span>
          </span>
        </label>
        <label className={`switch-row ${s.checkModUpdates ? '' : 'disabled'}`}>
          <input
            type="checkbox"
            checked={s.autoUpdateMods}
            disabled={!s.checkModUpdates}
            onChange={(e) => setInstant({ autoUpdateMods: e.target.checked })}
          />
          <span className="switch" aria-hidden="true" />
          <span>
            Install updates automatically
            <span className="muted small-text switch-note">Off: you press Update all yourself. On: found updates install before the game starts.</span>
          </span>
        </label>
      </div>

      <BackupCard app={app} />

      <div className="card">
        <div className="row spread">
          <div>
            <h3>Minecraft accounts</h3>
            <p className="muted small-text">
              You sign in on Microsoft's own page. Astro never sees your password, only a session token.
            </p>
          </div>
          <button
            className="btn-microsoft"
            disabled={busy}
            onClick={() => run(async () => app.setSettings(await astro.addAccount()), 'Signed in')}
          >
            <MicrosoftLogo />
            {busy ? 'Waiting for Microsoft…' : 'Add Microsoft account'}
          </button>
        </div>
        <ul className="account-list">
          {s.accounts.map((a) => (
            <li key={a.uuid}>
              <SkinHead skinUrl={a.skinUrl} name={a.name} size={36} />
              <div className="grow">
                <strong>{a.name}</strong>
                <p className="muted small-text mono">{a.uuid}</p>
              </div>
              {a.uuid === s.activeAccount ? (
                <span className="pill">Active</span>
              ) : (
                <button
                  className="btn-ghost small"
                  disabled={busy}
                  onClick={() => run(async () => app.setSettings(await astro.updateSettings({ activeAccount: a.uuid })))}
                >
                  Use
                </button>
              )}
              <button
                className="btn-danger small"
                disabled={busy}
                onClick={async () => {
                  if (
                    await app.confirm({
                      title: 'Remove account?',
                      message: `Remove ${a.name} from Astro?`,
                      confirmLabel: 'Remove',
                      danger: true
                    })
                  )
                    void run(async () => app.setSettings(await astro.removeAccount(a.uuid)));
                }}
              >
                Remove
              </button>
            </li>
          ))}
          {s.accounts.length === 0 && (
            <li className="muted">
              {s.offlineAllowed
                ? `No account yet. This development build launches offline as ${s.offlineName}; release builds require sign-in.`
                : 'No account yet. Sign in with a Microsoft account that owns Minecraft: Java Edition.'}
            </li>
          )}
        </ul>
      </div>

      <form className="card settings-form" onSubmit={save}>
        <h3>Game</h3>
        <fieldset className="radio-cards">
          <legend className="muted small-text">Java runtime</legend>
          <label className={javaMode === 'bundled' ? 'on' : ''}>
            <input type="radio" name="java" checked={javaMode === 'bundled'} onChange={() => setJavaMode('bundled')} />
            <strong>Bundled (recommended)</strong>
            <span className="muted small-text">Downloads Mojang's official Java runtime for each Minecraft version.</span>
          </label>
          <label className={javaMode === 'custom' ? 'on' : ''}>
            <input type="radio" name="java" checked={javaMode === 'custom'} onChange={() => setJavaMode('custom')} />
            <strong>Custom</strong>
            <span className="muted small-text">Use your own java/javaw executable.</span>
          </label>
        </fieldset>
        {javaMode === 'custom' && (
          <label className="field">
            Java executable
            <input className="input" value={javaPath} onChange={(e) => setJavaPath(e.target.value)} placeholder="C:\Program Files\Java\jdk-21\bin\javaw.exe" required />
          </label>
        )}
        <label className="switch-row">
          <input type="checkbox" checked={hideOnLaunch} onChange={(e) => setHideOnLaunch(e.target.checked)} />
          <span className="switch" aria-hidden="true" />
          Hide the launcher while the game is running
        </label>
        <label className="switch-row">
          <input type="checkbox" checked={autoInstallAstro} onChange={(e) => setAutoInstallAstro(e.target.checked)} />
          <span className="switch" aria-hidden="true" />
          Install Astro Client, Fabric API and performance mods (Sodium, Lithium…) when launching
        </label>
        {s.offlineAllowed && (
          <label className="field">
            Offline name (development builds only)
            <input className="input" value={offlineName} onChange={(e) => setOfflineName(e.target.value)} pattern="\w{3,16}" />
          </label>
        )}

        <h3>Integrations</h3>
        <label className="field">
          CurseForge API key
          <input
            className="input"
            type="password"
            autoComplete="off"
            value={cfKey}
            onChange={(e) => setCfKey(e.target.value)}
            placeholder={s.hasCurseforgeKey ? '•••••••• saved, type to replace' : 'Paste a key from console.curseforge.com'}
          />
        </label>
        <div>
          <button className="btn-primary" disabled={busy}>
            Save settings
          </button>
        </div>
      </form>
    </section>
  );
}

function updateText(u: UpdateState, current: string): string {
  switch (u.status) {
    case 'checking':
      return 'Checking for a new version…';
    case 'downloading':
      return `Downloading v${u.version} (${u.percent ?? 0}%) in the background…`;
    case 'ready':
      return `v${u.version} is ready. It installs when you close Astro, or restart now.`;
    case 'none':
      return `You're on the latest version${current ? ` (v${current})` : ''}.`;
    case 'disabled':
      return u.error ?? 'Updates are off in this build.';
    case 'error':
      return `Couldn't check for updates: ${u.error ?? 'unknown error'}`;
    default:
      return 'Astro checks for updates when it starts and every few hours.';
  }
}
