import { app, type BrowserWindow } from 'electron';
import { autoUpdater } from 'electron-updater';
import type { UpdateState } from '@shared/types';

const CHECK_EVERY_MS = 4 * 60 * 60 * 1000;

let state: UpdateState = { status: 'idle' };
let getWindow: () => BrowserWindow | null = () => null;

function set(next: UpdateState): void {
  state = next;
  getWindow()?.webContents.send('update:state', state);
}

export function updateState(): UpdateState {
  return state;
}

export async function checkForUpdates(): Promise<UpdateState> {
  if (!app.isPackaged) {
    set({ status: 'disabled', error: 'Updates only run in the installed app.' });
    return state;
  }
  if (state.status === 'downloading' || state.status === 'ready') return state;
  try {
    await autoUpdater.checkForUpdates();
  } catch (err) {
    set({ status: 'error', error: (err as Error).message });
  }
  return state;
}

/** Closes Astro and runs the downloaded installer, which reopens it. */
export function installUpdate(): void {
  if (state.status === 'ready') autoUpdater.quitAndInstall(true, true);
}

/**
 * Releases are published to the public astro-releases repo (see "publish" in package.json); the
 * source repo stays private, so no token has to ship inside the app. New versions download in the
 * background and install when Astro is closed, or straight away from Settings.
 */
export function startUpdater(windowGetter: () => BrowserWindow | null): void {
  getWindow = windowGetter;
  if (!app.isPackaged) return;
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.on('checking-for-update', () => set({ status: 'checking' }));
  autoUpdater.on('update-not-available', () => set({ status: 'none' }));
  autoUpdater.on('update-available', (info) => set({ status: 'downloading', version: info.version, percent: 0 }));
  autoUpdater.on('download-progress', (p) => set({ ...state, status: 'downloading', percent: Math.round(p.percent) }));
  autoUpdater.on('update-downloaded', (info) => set({ status: 'ready', version: info.version }));
  autoUpdater.on('error', (err) => set({ status: 'error', error: err.message }));
  void checkForUpdates();
  setInterval(() => void checkForUpdates(), CHECK_EVERY_MS).unref();
}
