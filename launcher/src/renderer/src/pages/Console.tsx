import { useEffect, useMemo, useRef, useState } from 'react';
import type { LogLevel } from '@shared/types';
import type { AppState } from '../App';
import { astro } from '../api';
import Icon from '../components/Icon';

const LEVELS: Array<{ id: LogLevel; label: string }> = [
  { id: 'info', label: 'Info' },
  { id: 'warn', label: 'Warn' },
  { id: 'error', label: 'Error' },
  { id: 'launcher', label: 'Launcher' },
  { id: 'debug', label: 'Debug' }
];

const RENDER_LIMIT = 2000;

export default function Console({ app }: { app: AppState }) {
  const [filter, setFilter] = useState('');
  const [levels, setLevels] = useState<Set<LogLevel>>(new Set(['info', 'warn', 'error', 'launcher']));
  const [follow, setFollow] = useState(true);
  const ref = useRef<HTMLDivElement>(null);

  const visible = useMemo(() => {
    const q = filter.trim().toLowerCase();
    const out = app.console.filter((l) => levels.has(l.level) && (!q || l.text.toLowerCase().includes(q)));
    return out.length > RENDER_LIMIT ? out.slice(-RENDER_LIMIT) : out;
  }, [app.console, filter, levels]);

  const counts = useMemo(() => {
    const c: Record<LogLevel, number> = { info: 0, warn: 0, error: 0, launcher: 0, debug: 0 };
    for (const l of app.console) c[l.level]++;
    return c;
  }, [app.console]);

  useEffect(() => {
    if (follow) ref.current?.scrollTo({ top: ref.current.scrollHeight });
  }, [visible, follow]);

  const onScroll = () => {
    const el = ref.current;
    if (!el) return;
    setFollow(el.scrollHeight - el.scrollTop - el.clientHeight < 24);
  };

  const toggle = (level: LogLevel) =>
    setLevels((s) => {
      const next = new Set(s);
      if (next.has(level)) next.delete(level);
      else next.add(level);
      return next;
    });

  const copy = () => navigator.clipboard.writeText(visible.map((l) => l.text).join('\n'));
  const logsProfile = app.game.profileId ?? app.selectedId;

  return (
    <section className="console-page">
      <header className="console-toolbar">
        <div className="console-title">
          <h2>Console</h2>
          <span className={`state-pill ${app.game.running ? 'running' : ''}`}>
            {app.game.running ? `Running · ${app.game.profileName}` : 'Idle'}
          </span>
        </div>
        <input
          className="input grow"
          type="search"
          placeholder="Filter log…"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          aria-label="Filter log"
        />
        <div className="level-toggles" role="group" aria-label="Log levels">
          {LEVELS.map((l) => (
            <button key={l.id} className={`level ${l.id} ${levels.has(l.id) ? 'on' : ''}`} onClick={() => toggle(l.id)} aria-pressed={levels.has(l.id)}>
              {l.label}
              <span>{counts[l.id]}</span>
            </button>
          ))}
        </div>
      </header>

      <div className="console-output" ref={ref} onScroll={onScroll} role="log" aria-live="off">
        {visible.length === 0 && <p className="muted console-empty">No output yet. Launch the game and its log streams here.</p>}
        {visible.map((l) => (
          <div key={l.id} className={`log-line ${l.level}`}>
            <time>{new Date(l.time).toLocaleTimeString([], { hour12: false })}</time>
            <span>{l.text}</span>
          </div>
        ))}
      </div>

      <footer className="console-actions">
        <button className="btn-ghost small" onClick={copy}>
          Copy
        </button>
        <button className="btn-ghost small" onClick={() => app.clearConsole()}>
          Clear
        </button>
        {logsProfile && (
          <button className="btn-ghost small" onClick={() => astro.openLogsFolder(logsProfile)}>
            Open logs folder
          </button>
        )}
        <button className="btn-ghost small" onClick={() => app.openLogHelper()} title="Find the cause of a crash or error (Ctrl+L)">
          <Icon name="sparkles" size={13} /> Analyze a log
        </button>
        {!follow && (
          <button className="btn-ghost small" onClick={() => setFollow(true)}>
            Jump to latest
          </button>
        )}
        <span className="grow" />
        {app.game.running && (
          <button className="btn-danger small" onClick={() => astro.killGame()}>
            Force stop game
          </button>
        )}
      </footer>
    </section>
  );
}
