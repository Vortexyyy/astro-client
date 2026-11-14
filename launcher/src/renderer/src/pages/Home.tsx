import { useEffect, useState, type FormEvent } from 'react';
import type { AppState } from '../App';
import { astro, errorText } from '../api';
import { MicrosoftLogo } from '../components/TitleBar';
import SkinHead from '../components/SkinHead';
import Icon from '../components/Icon';

function useElapsed(since: number | null): string {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!since) return;
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, [since]);
  if (!since) return '';
  const s = Math.max(0, Math.floor((now - since) / 1000));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

function timeAgo(when: number): string {
  const minutes = Math.round((Date.now() - when) / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  return days === 1 ? 'yesterday' : `${days} days ago`;
}

export default function Home({ app }: { app: AppState }) {
  const s = app.settings;
  const profile = app.profiles.find((p) => p.id === app.selectedId) ?? null;
  const account = s?.accounts.find((a) => a.uuid === s.activeAccount) ?? null;
  const elapsed = useElapsed(app.game.startedAt);
  const busy = app.launch.launching || app.game.running;
  const needsAccount = !account && !s?.offlineAllowed;

  const label = app.game.running ? 'Playing' : app.launch.launching ? 'Launching' : 'Launch Game';
  const loaderName = profile ? (profile.loader === 'vanilla' ? 'Vanilla' : profile.loader === 'quilt' ? 'Quilt' : 'Fabric') : null;

  return (
    <section className="home">
      <div className="home-sky" aria-hidden="true">
        <div className="sky-glow" />
        <div className="planet">
          <div className="planet-shade" />
        </div>
        <div className="planet-orbit">
          <div className="planet-orbit-spin" />
        </div>
        <div className="horizon" />
      </div>

      <div className="hero">
        <p className="eyebrow">
          <span className="eyebrow-line" aria-hidden="true" />
          Astro Client · {profile ? `${loaderName} ${profile.mcVersion}` : 'Minecraft'}
          <span className="eyebrow-line" aria-hidden="true" />
        </p>
        <h1 className="wordmark">ASTRO</h1>

        {needsAccount ? (
          <button className="btn-launch microsoft" onClick={() => app.navigate('settings')}>
            <span className="launch-label">
              <MicrosoftLogo /> Sign in to play
            </span>
          </button>
        ) : profile ? (
          <button
            className={`btn-launch ${busy ? 'busy' : ''}`}
            onClick={() => app.play()}
            disabled={busy}
            title={busy ? undefined : 'Launch (Ctrl+Enter)'}
          >
            <span className="launch-shine" aria-hidden="true" />
            <span className="launch-label">
              {!busy && <Icon name="play" size={18} className="launch-icon" />}
              {label}
              {app.game.running && <small>{elapsed}</small>}
            </span>
          </button>
        ) : (
          <div className="empty-actions">
            <button className="btn-launch" onClick={() => app.navigate('profiles')}>
              <span className="launch-shine" aria-hidden="true" />
              <span className="launch-label">
                <Icon name="plus" size={18} className="launch-icon" />
                Create a profile
              </span>
            </button>
            <button className="link" onClick={app.openImport}>
              or import from Lunar, Modrinth, Prism, CurseForge…
            </button>
          </div>
        )}

        {app.profiles.length > 0 && (
          <label className="profile-chip">
            <span className="muted">Profile</span>
            <select value={app.selectedId ?? ''} onChange={(e) => app.select(e.target.value)} disabled={busy} aria-label="Profile">
              {app.profiles.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} · {p.mcVersion} · {p.mods.length} mods
                </option>
              ))}
            </select>
          </label>
        )}

        {profile && (
          <ul className="hero-stats" aria-label="Profile details">
            <li>
              <Icon name="layers" size={14} />
              {loaderName} {profile.mcVersion}
            </li>
            <li>
              <Icon name="package" size={14} />
              {profile.mods.length} {profile.mods.length === 1 ? 'mod' : 'mods'}
            </li>
            <li>
              <Icon name="cpu" size={14} />
              {Math.round((profile.memoryMb / 1024) * 10) / 10} GB
            </li>
            <li>
              <Icon name="clock" size={14} />
              {profile.lastPlayed ? `Played ${timeAgo(profile.lastPlayed)}` : 'Never played'}
            </li>
          </ul>
        )}

        <div className="launch-status" aria-live="polite">
          {app.game.running ? (
            <span>
              Running {app.game.profileName}.{' '}
              <button className="link" onClick={() => app.navigate('console')}>
                Open console
              </button>{' '}
              ·{' '}
              <button className="link danger" onClick={() => astro.killGame()}>
                Force stop
              </button>
            </span>
          ) : (
            app.launch.status && <span>{app.launch.task ? `${app.launch.task} · ${app.launch.pct}%` : app.launch.status}</span>
          )}
          {app.launch.pct !== null && (
            <div className="progress">
              <div style={{ width: `${app.launch.pct}%` }} />
            </div>
          )}
        </div>
      </div>

      <div className="home-bottom">
        <ServerList app={app} disabled={busy || needsAccount || !profile} />
        <div className="card player-card">
          <p className="card-label">Playing as</p>
          {account ? (
            <div className="row">
              <SkinHead skinUrl={account.skinUrl} name={account.name} size={40} />
              <div>
                <strong>{account.name}</strong>
                <p className="muted small-text">Microsoft account</p>
              </div>
            </div>
          ) : (
            <div className="row">
              <SkinHead name={s?.offlineName ?? '?'} size={40} />
              <div>
                <strong>{s?.offlineAllowed ? s.offlineName : 'Not signed in'}</strong>
                <p className="muted small-text">{s?.offlineAllowed ? 'Offline (development build)' : 'Sign in to play'}</p>
              </div>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

function ServerList({ app, disabled }: { app: AppState; disabled: boolean }) {
  const servers = app.settings?.servers ?? [];
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');

  async function add(e: FormEvent) {
    e.preventDefault();
    try {
      app.setSettings(await astro.addServer(name, address));
      setAdding(false);
      setName('');
      setAddress('');
    } catch (err) {
      app.notify(errorText(err), 'error');
    }
  }

  return (
    <div className="servers">
      <p className="card-label">Quick join</p>
      <div className="server-row">
        {servers.map((srv) => (
          <div key={srv.id} className="server-card">
            <span className="server-icon" aria-hidden="true">
              {srv.name.slice(0, 1).toUpperCase()}
            </span>
            <div className="server-text">
              <strong>{srv.name}</strong>
              <span className="muted">{srv.address}</span>
            </div>
            <button className="btn-join" disabled={disabled} onClick={() => app.play(srv.address)}>
              Join
            </button>
            <button
              className="icon-button server-remove"
              aria-label={`Remove ${srv.name}`}
              onClick={async () => app.setSettings(await astro.removeServer(srv.id))}
            >
              <Icon name="x" />
            </button>
          </div>
        ))}

        {adding ? (
          <form className="server-card add-form" onSubmit={add}>
            <input className="input" placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
            <input className="input" placeholder="play.example.net" value={address} onChange={(e) => setAddress(e.target.value)} required />
            <button className="btn-join">Save</button>
            <button type="button" className="icon-button" aria-label="Cancel" onClick={() => setAdding(false)}>
              <Icon name="x" />
            </button>
          </form>
        ) : (
          <button className="server-card add" onClick={() => setAdding(true)}>
            <span className="server-icon">
              <Icon name="plus" size={18} />
            </span>
            <span>Add server</span>
          </button>
        )}
      </div>
    </div>
  );
}
