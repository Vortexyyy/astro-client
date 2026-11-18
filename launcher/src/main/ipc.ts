import { app, BrowserWindow, ipcMain, shell, type IpcMainInvokeEvent } from 'electron';
import { mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import type { DuplicateOptions, ImportOptions, ImportRequest, LaunchEvent, LaunchOptions, ModSource, NewProfile, SearchRequest, SettingsPatch } from '@shared/types';
import { backupNow, backupStatus, chooseBackupFolder, noteProfileDeleted, onBackupStatus, restoreProfiles, scheduleBackup, stopBackup } from './backup';
import { addMicrosoftAccount } from './game/auth';
import * as gameConsole from './game/console';
import { analyzeExit, copyCrashDetails, openCrashReport, openLatestLog } from './game/crash';
import { gameState, killGame, launchProfile, onGameExit, onGameState } from './game/launch';
import { listGameVersions } from './game/loaders';
import { importMany, pickFolder, scanAll } from './importers';
import { BUNDLES, installBundle } from './profiles/bundles';
import { applyUpdates, autoCheckUpdates, checkUpdates } from './profiles/updates';
import { copyShareCode, duplicateProfile, importShareCode } from './profiles/sharing';
import { forgetProfile, getStats, onStats, recordLaunch, recordSession } from './stats';
import { paths } from './paths';
import { exportProfile, importProfile } from './profiles/packs';
import {
  addLocalMods,
  assertProfileId,
  createProfile,
  deleteProfile,
  getProfile,
  installMod,
  listProfiles,
  modLoaders,
  openProfileFolder,
  removeMod,
  saveProfile
} from './profiles/store';
import { provider } from './providers/provider';
import { checkForUpdates, installUpdate, updateState } from './updater';
import { addServer, getHideOnLaunch, getPublicSettings, removeAccount, removeServer, updateSettings } from './settings';

const SOURCES: ModSource[] = ['modrinth', 'curseforge'];

function assertSource(source: unknown): asserts source is ModSource {
  if (!SOURCES.includes(source as ModSource)) throw new Error('Unknown mod source');
}

function windowOf(e: IpcMainInvokeEvent): BrowserWindow {
  const win = BrowserWindow.fromWebContents(e.sender);
  if (!win) throw new Error('No window');
  return win;
}

/** Registers a handler whose success changes data worth writing to the backup folder. */
function mutating<A extends unknown[], R>(channel: string, handler: (e: IpcMainInvokeEvent, ...args: A) => Promise<R> | R): void {
  ipcMain.handle(channel, async (e, ...args) => {
    const result = await handler(e, ...(args as A));
    scheduleBackup();
    return result;
  });
}

function broadcast(channel: string, payload: unknown) {
  for (const win of BrowserWindow.getAllWindows()) {
    if (!win.webContents.isDestroyed()) win.webContents.send(channel, payload);
  }
}

export function registerIpc(mainWindow: () => BrowserWindow | null): void {
  gameConsole.onLines((batch) => broadcast('console:lines', batch));
  onBackupStatus((status) => broadcast('backup:status', status));
  onStats((stats) => broadcast('stats:changed', stats));

  // Playtime + crash helper, both driven by the game process ending.
  onGameExit((exit) => {
    void recordSession(exit.profileId, exit.startedAt, exit.endedAt);
    void analyzeExit(exit).then(
      (crash) => crash && broadcast('game:crash', crash),
      (err) => gameConsole.log(`Crash helper failed: ${(err as Error).message}`, 'launcher')
    );
  });

  // Hide the launcher while playing (if enabled) and bring it back when the game closes.
  onGameState(async (state) => {
    broadcast('game:state', state);
    const win = mainWindow();
    if (!win) return;
    if (state.running && (await getHideOnLaunch())) win.hide();
    if (!state.running && !win.isVisible()) win.show();
  });

  ipcMain.handle('mods:search', async (_e, req: SearchRequest) => {
    assertSource(req.source);
    const loaders = modLoaders(await getProfile(req.profileId));
    return provider(req.source).search(req.query ?? '', req.mcVersion, loaders, Math.max(0, req.offset ?? 0));
  });
  mutating('mods:install', (_e, profileId: string, source: ModSource, projectId: string) => {
    assertSource(source);
    return installMod(profileId, source, String(projectId));
  });
  mutating('mods:addFiles', (_e, profileId: string, filePaths: unknown) =>
    addLocalMods(profileId, Array.isArray(filePaths) ? filePaths.map(String) : [])
  );
  mutating('mods:remove', (_e, profileId: string, projectId: string) => removeMod(profileId, String(projectId)));

  ipcMain.handle('profiles:list', () => listProfiles());
  mutating('profiles:create', (_e, input: NewProfile) => createProfile(input));
  ipcMain.handle('profiles:delete', async (_e, id: string) => {
    await deleteProfile(id);
    await noteProfileDeleted(id);
    await forgetProfile(id);
  });
  mutating('profiles:duplicate', (_e, id: string, options: DuplicateOptions) =>
    duplicateProfile(String(id), { name: String(options?.name ?? '').slice(0, 64), copyConfig: Boolean(options?.copyConfig) })
  );
  ipcMain.handle('profiles:shareCode', (_e, id: string) => copyShareCode(String(id)));
  mutating('profiles:importCode', (_e, code: unknown) => importShareCode(code));
  ipcMain.handle('profiles:open', (_e, id: string) => openProfileFolder(id));
  ipcMain.handle('profiles:export', (e, id: string) => exportProfile(windowOf(e), id));
  mutating('profiles:import', (e) => importProfile(windowOf(e)));

  ipcMain.handle('bundles:list', () => BUNDLES);
  mutating('bundles:install', (_e, profileId: string, bundleId: string) => installBundle(profileId, String(bundleId)));
  ipcMain.handle('updates:check', (_e, profileId: string) => checkUpdates(profileId));
  ipcMain.handle('updates:auto', async (_e, profileId: string) => {
    const status = await autoCheckUpdates(String(profileId));
    if (status?.applied?.installed.length) scheduleBackup();
    return status;
  });
  mutating('updates:apply', (_e, profileId: string, ids: string[]) =>
    applyUpdates(profileId, Array.isArray(ids) ? ids.map(String) : [])
  );

  ipcMain.handle('import:scan', () => scanAll());
  ipcMain.handle('import:folder', (e) => pickFolder(windowOf(e)));
  mutating('import:run', (e, requests: ImportRequest[], options: ImportOptions) => {
    if (!Array.isArray(requests)) throw new Error('Nothing to import');
    const clean = requests.map((r) => ({ key: String(r.key), name: String(r.name ?? ''), mcVersion: String(r.mcVersion ?? '') }));
    const opts: ImportOptions = {
      mods: Boolean(options?.mods),
      configs: Boolean(options?.configs),
      resourcePacks: Boolean(options?.resourcePacks),
      shaderPacks: Boolean(options?.shaderPacks),
      gameOptions: Boolean(options?.gameOptions),
      worlds: Boolean(options?.worlds)
    };
    return importMany(clean, opts, (p) => {
      if (!e.sender.isDestroyed()) e.sender.send('import:progress', p);
    });
  });

  ipcMain.handle('game:versions', () => listGameVersions());
  ipcMain.handle('game:state', () => gameState());
  ipcMain.handle('game:kill', () => killGame());
  ipcMain.handle('game:launch', async (e, id: string, options: LaunchOptions = {}) => {
    assertProfileId(id);
    const send = (event: LaunchEvent) => {
      if (!e.sender.isDestroyed()) e.sender.send('game:event', event);
    };
    try {
      if (!gameState().running) {
        send({ type: 'status', message: 'Checking mod updates…' });
        const status = await autoCheckUpdates(id).catch(() => null);
        if (status) broadcast('updates:status', status);
      }
      const profile = await getProfile(id);
      await launchProfile(profile, { server: options.server ? String(options.server) : undefined }, send);
      // re-read: launching may have installed Astro / performance mods into the profile
      const startedAt = gameState().startedAt ?? Date.now();
      await saveProfile({ ...(await getProfile(id)), lastPlayed: startedAt });
      await recordLaunch(id, startedAt);
      scheduleBackup();
    } catch (err) {
      send({ type: 'error', message: (err as Error).message });
      throw err;
    }
  });

  ipcMain.handle('console:get', () => gameConsole.getLines());
  ipcMain.handle('console:clear', () => gameConsole.clear());
  ipcMain.handle('console:openLogs', async (_e, id: string) => {
    assertProfileId(id);
    const dir = join(paths.instanceDir(id), 'logs');
    await mkdir(dir, { recursive: true });
    await shell.openPath(dir);
  });

  ipcMain.handle('stats:get', () => getStats());
  ipcMain.handle('crash:openReport', (_e, id: string, fileName: string) => openCrashReport(String(id), String(fileName)));
  ipcMain.handle('crash:openLog', (_e, id: string) => openLatestLog(String(id)));
  ipcMain.handle('crash:copy', () => copyCrashDetails());

  ipcMain.handle('settings:get', () => getPublicSettings());
  ipcMain.handle('app:version', () => app.getVersion());
  ipcMain.handle('update:state', () => updateState());
  ipcMain.handle('update:check', () => checkForUpdates());
  ipcMain.handle('update:install', () => installUpdate());
  mutating('settings:update', (_e, patch: SettingsPatch) => updateSettings(patch));
  mutating('accounts:add', () => addMicrosoftAccount());
  mutating('accounts:remove', (_e, uuid: string) => removeAccount(String(uuid)));
  mutating('servers:add', (_e, name: string, address: string) => addServer(String(name), String(address)));
  mutating('servers:remove', (_e, id: string) => removeServer(String(id)));

  ipcMain.handle('backup:status', () => backupStatus());
  ipcMain.handle('backup:choose', (e) => chooseBackupFolder(windowOf(e)));
  ipcMain.handle('backup:now', () => backupNow());
  ipcMain.handle('backup:stop', () => stopBackup());
  ipcMain.handle('backup:restore', (_e, ids: string[]) => restoreProfiles(Array.isArray(ids) ? ids.map(String) : []));

  ipcMain.on('window:control', (e, action: string) => {
    const win = BrowserWindow.fromWebContents(e.sender);
    if (!win) return;
    if (action === 'minimize') win.minimize();
    else if (action === 'maximize') (win.isMaximized() ? win.unmaximize() : win.maximize());
    else if (action === 'close') win.close();
  });
}
