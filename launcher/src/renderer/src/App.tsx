import { useCallback, useEffect, useRef, useState } from 'react';
import type { ConsoleLine, CrashInfo, GameState, ModUpdateStatus, Profile, ProfileStats, PublicSettings } from '@shared/types';
import { astro, errorText } from './api';
import ConfirmDialog, { type ConfirmRequest } from './components/ConfirmDialog';
import CrashDialog from './components/CrashDialog';
import ImportWizard from './components/ImportWizard';
import Starfield from './components/Starfield';
import TitleBar, { type Page } from './components/TitleBar';
import Browse from './pages/Browse';
import Console from './pages/Console';
import Home from './pages/Home';
import Profiles from './pages/Profiles';
import Settings from './pages/Settings';
import Icon from './components/Icon';
import ShortcutsDialog from './components/ShortcutsDialog';

const PAGE_KEYS: Page[] = ['home', 'mods', 'profiles', 'console', 'settings'];
/** Elements that get the pointer-following spotlight. */
const SPOTLIT = '.card, .server-card, .mod-card, .profile-item, .bundle';

const MAX_CONSOLE_LINES = 5000;

export interface LaunchProgress {
  status: string | null;
  task: string | null;
  pct: number | null;
  launching: boolean;
}

export interface AppState {
  profiles: Profile[];
  selectedId: string | null;
  settings: PublicSettings | null;
  game: GameState;
  launch: LaunchProgress;
  console: ConsoleLine[];
  stats: Record<string, ProfileStats>;
  /** latest mod update check for a profile (automatic or from the Check for updates button) */
  modUpdates: ModUpdateStatus | null;
  setModUpdates(status: ModUpdateStatus | null): void;
  select(id: string): void;
  refresh(): Promise<void>;
  upsert(profile: Profile): void;
  setSettings(s: PublicSettings): void;
  notify(message: string, kind?: 'info' | 'error'): void;
  play(server?: string): Promise<void>;
  navigate(page: Page): void;
  clearConsole(): Promise<void>;
  openImport(): void;
  confirm(request: ConfirmRequest): Promise<boolean>;
  showShortcuts(): void;
}

const idleGame: GameState = { running: false, profileId: null, profileName: null, startedAt: null };
const idleLaunch: LaunchProgress = { status: null, task: null, pct: null, launching: false };

export default function App() {
  const [page, setPage] = useState<Page>('home');
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [settings, setSettings] = useState<PublicSettings | null>(null);
  const [game, setGame] = useState<GameState>(idleGame);
  const [launch, setLaunch] = useState<LaunchProgress>(idleLaunch);
  const [consoleLines, setConsoleLines] = useState<ConsoleLine[]>([]);
  const [stats, setStats] = useState<Record<string, ProfileStats>>({});
  const [modUpdates, setModUpdates] = useState<ModUpdateStatus | null>(null);
  const [crash, setCrash] = useState<CrashInfo | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const [toast, setToast] = useState<{ message: string; kind: 'info' | 'error' } | null>(null);
  const [pendingConfirm, setPendingConfirm] = useState<{
    request: ConfirmRequest;
    resolve: (ok: boolean) => void;
  } | null>(null);

  const notify = useCallback((message: string, kind: 'info' | 'error' = 'info') => {
    setToast({ message, kind });
    window.setTimeout(() => setToast((t) => (t?.message === message ? null : t)), 5000);
  }, []);

  const confirm = useCallback(
    (request: ConfirmRequest) =>
      new Promise<boolean>((resolve) => {
        // a new request supersedes one still open, which counts as cancelled
        setPendingConfirm((prev) => {
          prev?.resolve(false);
          return { request, resolve };
        });
      }),
    []
  );

  const answerConfirm = useCallback((ok: boolean) => {
    setPendingConfirm((prev) => {
      prev?.resolve(ok);
      return null;
    });
  }, []);

  const refresh = useCallback(async () => {
    try {
      const list = await astro.listProfiles();
      setProfiles(list);
      setSelectedId((id) => (id && list.some((p) => p.id === id) ? id : (list[0]?.id ?? null)));
    } catch (err) {
      notify(errorText(err), 'error');
    }
  }, [notify]);

  useEffect(() => {
    void refresh();
    astro.getSettings().then(setSettings, (err) => notify(errorText(err), 'error'));
    astro.getGameState().then(setGame, () => undefined);
    astro.getConsole().then(setConsoleLines, () => undefined);
    astro.getStats().then(setStats, () => undefined);
    const offStats = astro.onStats(setStats);
    const offCrash = astro.onCrash(setCrash);
    const offUpdates = astro.onModUpdates(setModUpdates);

    const offConsole = astro.onConsole((batch) =>
      setConsoleLines((lines) => {
        const next = lines.concat(batch);
        return next.length > MAX_CONSOLE_LINES ? next.slice(-MAX_CONSOLE_LINES) : next;
      })
    );
    const offState = astro.onGameState((state) => {
      setGame(state);
      if (!state.running) setLaunch(idleLaunch);
    });
    const offLaunch = astro.onLaunchEvent((e) => {
      if (e.type === 'status') setLaunch((l) => ({ ...l, status: e.message, task: null, pct: null }));
      if (e.type === 'progress')
        setLaunch((l) => ({ ...l, task: e.task, pct: e.total ? Math.round((e.current / e.total) * 100) : 0 }));
      if (e.type === 'error') setLaunch({ ...idleLaunch, status: e.message });
    });
    return () => {
      offStats();
      offCrash();
      offUpdates();
      offConsole();
      offState();
      offLaunch();
    };
  }, [refresh, notify]);

  // Applied automatic updates are announced once, whoever triggered the check.
  useEffect(() => {
    const applied = modUpdates?.applied;
    if (!applied || !applied.installed.length) return;
    void refresh();
    notify(
      `Updated ${applied.installed.length} mod(s) automatically${applied.skipped.length ? `; ${applied.skipped.length} failed` : ''}`,
      applied.skipped.length ? 'error' : 'info'
    );
  }, [modUpdates, refresh, notify]);

  // Start-up mod update check for the selected profile (once per launcher start).
  const startupChecked = useRef(false);
  useEffect(() => {
    if (startupChecked.current || !settings || !selectedId) return;
    startupChecked.current = true;
    if (!settings.checkModUpdates) return;
    astro.autoCheckUpdates(selectedId).then((status) => status && setModUpdates(status), () => undefined);
  }, [settings, selectedId]);

  const upsert = useCallback((profile: Profile) => {
    setProfiles((list) => {
      const i = list.findIndex((p) => p.id === profile.id);
      return i === -1 ? [profile, ...list] : list.map((p) => (p.id === profile.id ? profile : p));
    });
  }, []);

  const play = useCallback(
    async (server?: string) => {
      if (!selectedId) return notify('Create a profile first.', 'error');
      setLaunch({ ...idleLaunch, launching: true, status: 'Starting…' });
      try {
        await astro.launch(selectedId, server ? { server } : undefined);
        setLaunch((l) => ({ ...l, launching: false, task: null, pct: null }));
        void refresh();
      } catch (err) {
        setLaunch({ ...idleLaunch, status: errorText(err) });
        notify(errorText(err), 'error');
      }
    },
    [selectedId, notify, refresh]
  );

  // Accent preset and "reduce effects" are plain attributes on <html>; the CSS swaps tokens.
  const theme = settings?.theme ?? 'nebula';
  const reduceEffects = settings?.reduceEffects ?? false;
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);
  useEffect(() => {
    document.documentElement.dataset.effects = reduceEffects ? 'reduced' : 'full';
  }, [reduceEffects]);

  // Pause every CSS animation while the window is unfocused or hidden, so the launcher costs
  // nothing while it sits next to a running game (the starfield canvas pauses itself the same way).
  useEffect(() => {
    const root = document.documentElement;
    const update = () => {
      const idle = document.visibilityState !== 'visible' || !document.hasFocus();
      if (idle) root.dataset.idle = '';
      else delete root.dataset.idle;
    };
    update();
    window.addEventListener('focus', update);
    window.addEventListener('blur', update);
    document.addEventListener('visibilitychange', update);
    return () => {
      window.removeEventListener('focus', update);
      window.removeEventListener('blur', update);
      document.removeEventListener('visibilitychange', update);
    };
  }, []);

  // Pointer-following spotlight on glass cards: one delegated listener, at most one style write per frame.
  useEffect(() => {
    let frame = 0;
    let target: HTMLElement | null = null;
    let x = 0;
    let y = 0;
    const onMove = (e: PointerEvent) => {
      if (document.documentElement.dataset.effects === 'reduced') return;
      target = (e.target as Element | null)?.closest?.<HTMLElement>(SPOTLIT) ?? null;
      x = e.clientX;
      y = e.clientY;
      if (!frame && target)
        frame = requestAnimationFrame(() => {
          frame = 0;
          if (!target) return;
          const r = target.getBoundingClientRect();
          target.style.setProperty('--mx', `${x - r.left}px`);
          target.style.setProperty('--my', `${y - r.top}px`);
        });
    };
    window.addEventListener('pointermove', onMove, { passive: true });
    return () => {
      window.removeEventListener('pointermove', onMove);
      cancelAnimationFrame(frame);
    };
  }, []);

  const account = settings?.accounts.find((a) => a.uuid === settings.activeAccount) ?? null;
  const canPlay =
    Boolean(selectedId) && Boolean(settings) && (Boolean(account) || Boolean(settings?.offlineAllowed)) && !launch.launching && !game.running;
  const modalOpen = importOpen || pendingConfirm !== null || shortcutsOpen || crash !== null;

  // Keyboard shortcuts (listed in ShortcutsDialog)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (modalOpen || e.repeat) return;
      const el = e.target as HTMLElement | null;
      const typing = el?.matches?.('input, textarea, select, [contenteditable="true"]') ?? false;
      const ctrl = e.ctrlKey || e.metaKey;
      if (ctrl && !e.altKey && !e.shiftKey && /^[1-5]$/.test(e.key)) {
        e.preventDefault();
        setPage(PAGE_KEYS[Number(e.key) - 1]);
      } else if (ctrl && e.key === 'Enter') {
        e.preventDefault();
        if (canPlay) void play();
      } else if ((e.key === '?' && !typing && !ctrl) || (ctrl && e.key === '/')) {
        e.preventDefault();
        setShortcutsOpen(true);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [modalOpen, canPlay, play]);

  const closeShortcuts = useCallback(() => setShortcutsOpen(false), []);

  const state: AppState = {
    profiles,
    selectedId,
    settings,
    game,
    launch,
    console: consoleLines,
    stats,
    modUpdates,
    setModUpdates,
    select: setSelectedId,
    refresh,
    upsert,
    setSettings,
    notify,
    play,
    navigate: setPage,
    clearConsole: async () => {
      await astro.clearConsole();
      setConsoleLines([]);
    },
    openImport: () => setImportOpen(true),
    confirm,
    showShortcuts: () => setShortcutsOpen(true)
  };

  return (
    <div className="app">
      <Starfield still={reduceEffects} theme={theme} />
      <TitleBar app={state} page={page} />
      <main className={`content page-${page}`} key={page}>
        {page === 'home' && <Home app={state} />}
        {page === 'mods' && <Browse app={state} />}
        {page === 'profiles' && <Profiles app={state} />}
        {page === 'console' && <Console app={state} />}
        {page === 'settings' && <Settings app={state} />}
      </main>
      {importOpen && <ImportWizard app={state} onClose={() => setImportOpen(false)} />}
      {shortcutsOpen && <ShortcutsDialog onClose={closeShortcuts} />}
      {crash && <CrashDialog crash={crash} notify={notify} onClose={() => setCrash(null)} />}
      {pendingConfirm && <ConfirmDialog request={pendingConfirm.request} onAnswer={answerConfirm} />}
      {toast && (
        <div className={`toast toast-${toast.kind}`} role={toast.kind === 'error' ? 'alert' : 'status'}>
          <Icon name={toast.kind === 'error' ? 'alert' : 'check'} size={18} className="toast-icon" />
          <span className="toast-text">{toast.message}</span>
          <button className="icon-button toast-close" aria-label="Dismiss" onClick={() => setToast(null)}>
            <Icon name="x" size={14} />
          </button>
        </div>
      )}
    </div>
  );
}
