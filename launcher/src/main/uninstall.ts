import { app, BrowserWindow, ipcMain, shell } from 'electron';
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { readdir, rm, stat } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import type { UninstallInfo, UninstallItemId, UninstallResult } from '@shared/types';
import { paths } from './paths';

/**
 * Astro's own uninstaller (`Astro Launcher.exe --uninstall`): a branded window where the player
 * picks what to remove, which then hands over to the regular NSIS uninstaller for the app files.
 */
const UNINSTALLER = 'Uninstall Astro Launcher.exe';

/** Folder size in bytes; unreadable files are skipped. */
async function sizeOf(path: string): Promise<number> {
  try {
    const s = await stat(path);
    if (!s.isDirectory()) return s.size;
    let total = 0;
    for (const entry of await readdir(path)) total += await sizeOf(join(path, entry));
    return total;
  } catch {
    return 0;
  }
}

/** Everything in userData that isn't profiles, game files or Java: settings, sign-ins, caches. */
async function settingsEntries(): Promise<string[]> {
  const keep = new Set(['profiles', 'minecraft', 'runtimes']);
  try {
    return (await readdir(paths.root)).filter((name) => !keep.has(name)).map((name) => join(paths.root, name));
  } catch {
    return [];
  }
}

async function info(): Promise<UninstallInfo> {
  const settings = await settingsEntries();
  let settingsSize = 0;
  for (const p of settings) settingsSize += await sizeOf(p);
  return {
    packaged: app.isPackaged,
    installDir: dirname(process.execPath),
    items: [
      {
        id: 'profiles',
        label: 'Profiles, worlds & mods',
        description: 'Your saved worlds, screenshots and mod setups. Moved to the Recycle Bin, so you can still get them back.',
        bytes: await sizeOf(paths.profilesDir),
        defaultOn: false
      },
      {
        id: 'game',
        label: 'Downloaded game files',
        description: 'Minecraft versions, libraries and sounds. Astro downloads them again if you reinstall.',
        bytes: await sizeOf(paths.gameRoot),
        defaultOn: true
      },
      {
        id: 'java',
        label: 'Java runtimes',
        description: "Mojang's Java that Astro downloaded. Not your system Java.",
        bytes: await sizeOf(paths.runtimesDir),
        defaultOn: true
      },
      {
        id: 'settings',
        label: 'Settings & sign-ins',
        description: 'Launcher settings, saved Microsoft sign-ins and caches. Your backup folder is not touched.',
        bytes: settingsSize,
        defaultOn: true
      }
    ]
  };
}

/** Astro's own files in userData (Chromium's caches next to them are locked while we run). */
const OWN_FILES = ['settings.json', 'backup-state.json', 'cloud-state.json', 'astro-session.bin'];

/**
 * Deletes what Chromium still has open once this app has exited: a hidden PowerShell waits a few
 * seconds, then removes the listed paths. Only used by the installed app during uninstall.
 */
function cleanUpAfterExit(targets: string[]): void {
  if (!targets.length) return;
  const list = targets.map((t) => `'${t.replace(/'/g, "''")}'`).join(',');
  const script = `Start-Sleep -Seconds 4; foreach ($p in @(${list})) { Remove-Item -LiteralPath $p -Recurse -Force -ErrorAction SilentlyContinue }`;
  spawn('powershell.exe', ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command', script], {
    detached: true,
    stdio: 'ignore',
    windowsHide: true
  }).unref();
}

async function run(selected: UninstallItemId[]): Promise<UninstallResult> {
  const chosen = new Set(selected);
  const failed: string[] = [];
  const remove = async (path: string) => {
    if (!existsSync(path)) return;
    try {
      await rm(path, { recursive: true, force: true, maxRetries: 2 });
    } catch {
      failed.push(path);
    }
  };

  if (chosen.has('profiles') && existsSync(paths.profilesDir)) {
    try {
      await shell.trashItem(paths.profilesDir);
    } catch {
      failed.push(paths.profilesDir);
    }
  }
  if (chosen.has('game')) await remove(paths.gameRoot);
  if (chosen.has('java')) await remove(paths.runtimesDir);
  if (chosen.has('settings')) for (const name of OWN_FILES) await remove(join(paths.root, name));

  if (!app.isPackaged) return { removedApp: false, failed, note: 'Development build: your data was cleaned up; app files were left in place.' };

  const uninstaller = join(dirname(process.execPath), UNINSTALLER);
  if (!existsSync(uninstaller)) return { removedApp: false, failed, note: `Couldn't find ${UNINSTALLER}. Use Windows Settings → Apps instead.` };
  // caches Chromium is still using; the whole folder if nothing is being kept
  if (chosen.has('settings')) {
    const keepsData = !chosen.has('profiles') || !chosen.has('game') || !chosen.has('java');
    cleanUpAfterExit(keepsData ? await settingsEntries() : [paths.root]);
  }
  // the NSIS uninstaller removes the program files, shortcuts and registry entry once we exit
  spawn(uninstaller, ['/S'], { detached: true, stdio: 'ignore' }).unref();
  setTimeout(() => app.quit(), 1500);
  return { removedApp: true, failed };
}

export function openUninstaller(): void {
  ipcMain.handle('uninstall:info', () => info());
  ipcMain.handle('uninstall:run', (_e, selected: UninstallItemId[]) => {
    const valid: UninstallItemId[] = ['profiles', 'game', 'java', 'settings'];
    return run(Array.isArray(selected) ? selected.filter((s): s is UninstallItemId => valid.includes(s)) : []);
  });
  ipcMain.on('uninstall:close', () => app.quit());

  const win = new BrowserWindow({
    width: 560,
    height: 660,
    resizable: false,
    maximizable: false,
    show: false,
    frame: false,
    backgroundColor: '#07060f',
    title: 'Uninstall Astro Launcher',
    webPreferences: {
      preload: join(__dirname, '../preload/uninstall.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  win.once('ready-to-show', () => win.show());
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', (e, url) => {
    if (url !== win.webContents.getURL()) e.preventDefault();
  });
  if (!app.isPackaged && process.env['ELECTRON_RENDERER_URL']) {
    void win.loadURL(`${process.env['ELECTRON_RENDERER_URL']}/uninstall.html`);
  } else {
    void win.loadFile(join(__dirname, '../renderer/uninstall.html'));
  }
}
