import { useCallback, useEffect, useState } from 'react';
import type { ConsoleLine, GameState, Profile, PublicSettings } from '@shared/types';
import { astro, errorText } from './api';
import ConfirmDialog, { type ConfirmRequest } from './components/ConfirmDialog';
import ImportWizard from './components/ImportWizard';
import Starfield from './components/Starfield';
import TitleBar, { type Page } from './components/TitleBar';
import Browse from './pages/Browse';
import Console from './pages/Console';
import Home from './pages/Home';
import Profiles from './pages/Profiles';
import Settings from './pages/Settings';
import Icon from './components/Icon';

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
  const [importOpen, setImportOpen] = useState(false);
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
      offConsole();
      offState();
      offLaunch();
    };
  }, [refresh, notify]);

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

  const state: AppState = {
    profiles,
    selectedId,
    settings,
    game,
    launch,
    console: consoleLines,
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
    confirm
  };

  return (
    <div className="app">
      <Starfield />
      <TitleBar app={state} page={page} />
      <main className={`content page-${page}`} key={page}>
        {page === 'home' && <Home app={state} />}
        {page === 'mods' && <Browse app={state} />}
        {page === 'profiles' && <Profiles app={state} />}
        {page === 'console' && <Console app={state} />}
        {page === 'settings' && <Settings app={state} />}
      </main>
      {importOpen && <ImportWizard app={state} onClose={() => setImportOpen(false)} />}
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
