import { contextBridge, ipcRenderer, webUtils, type IpcRendererEvent } from 'electron';
import type { AstroApi } from '@shared/types';

function subscribe<T>(channel: string, listener: (payload: T) => void): () => void {
  const handler = (_e: IpcRendererEvent, payload: T) => listener(payload);
  ipcRenderer.on(channel, handler);
  return () => ipcRenderer.removeListener(channel, handler);
}

const api: AstroApi = {
  searchMods: (req) => ipcRenderer.invoke('mods:search', req),
  installMod: (profileId, source, projectId) => ipcRenderer.invoke('mods:install', profileId, source, projectId),
  removeMod: (profileId, projectId) => ipcRenderer.invoke('mods:remove', profileId, projectId),
  addModFiles: (profileId, filePaths) => ipcRenderer.invoke('mods:addFiles', profileId, filePaths),
  // dropped File objects no longer carry .path (Electron 32+); only the preload can resolve it
  pathForFile: (file) => webUtils.getPathForFile(file),

  listProfiles: () => ipcRenderer.invoke('profiles:list'),
  createProfile: (input) => ipcRenderer.invoke('profiles:create', input),
  deleteProfile: (id) => ipcRenderer.invoke('profiles:delete', id),
  openProfileFolder: (id) => ipcRenderer.invoke('profiles:open', id),
  exportProfile: (id) => ipcRenderer.invoke('profiles:export', id),
  importProfile: () => ipcRenderer.invoke('profiles:import'),
  duplicateProfile: (id, options) => ipcRenderer.invoke('profiles:duplicate', id, options),
  copyShareCode: (id) => ipcRenderer.invoke('profiles:shareCode', id),
  importShareCode: (code) => ipcRenderer.invoke('profiles:importCode', code),

  listBundles: () => ipcRenderer.invoke('bundles:list'),
  installBundle: (profileId, bundleId) => ipcRenderer.invoke('bundles:install', profileId, bundleId),
  checkUpdates: (profileId) => ipcRenderer.invoke('updates:check', profileId),
  applyUpdates: (profileId, ids) => ipcRenderer.invoke('updates:apply', profileId, ids),
  autoCheckUpdates: (profileId) => ipcRenderer.invoke('updates:auto', profileId),
  onModUpdates: (listener) => subscribe('updates:status', listener),

  getStats: () => ipcRenderer.invoke('stats:get'),
  onStats: (listener) => subscribe('stats:changed', listener),
  onCrash: (listener) => subscribe('game:crash', listener),
  openCrashReport: (profileId, fileName) => ipcRenderer.invoke('crash:openReport', profileId, fileName),
  openLatestLog: (profileId) => ipcRenderer.invoke('crash:openLog', profileId),
  copyCrashDetails: () => ipcRenderer.invoke('crash:copy'),
  analyzeLog: (text) => ipcRenderer.invoke('logs:analyze', text),
  openLogFile: () => ipcRenderer.invoke('logs:open'),
  readLogFile: (filePath) => ipcRenderer.invoke('logs:read', filePath),
  readClipboardLog: () => ipcRenderer.invoke('logs:clipboard'),

  scanLaunchers: () => ipcRenderer.invoke('import:scan'),
  pickImportFolder: () => ipcRenderer.invoke('import:folder'),
  importInstances: (requests, options) => ipcRenderer.invoke('import:run', requests, options),
  onImportProgress: (listener) => subscribe('import:progress', listener),

  listGameVersions: () => ipcRenderer.invoke('game:versions'),
  launch: (profileId, options) => ipcRenderer.invoke('game:launch', profileId, options),
  killGame: () => ipcRenderer.invoke('game:kill'),
  getGameState: () => ipcRenderer.invoke('game:state'),
  onGameState: (listener) => subscribe('game:state', listener),
  onLaunchEvent: (listener) => subscribe('game:event', listener),

  getConsole: () => ipcRenderer.invoke('console:get'),
  clearConsole: () => ipcRenderer.invoke('console:clear'),
  openLogsFolder: (profileId) => ipcRenderer.invoke('console:openLogs', profileId),
  onConsole: (listener) => subscribe('console:lines', listener),

  getSettings: () => ipcRenderer.invoke('settings:get'),
  appVersion: () => ipcRenderer.invoke('app:version'),
  updateState: () => ipcRenderer.invoke('update:state'),
  checkForUpdates: () => ipcRenderer.invoke('update:check'),
  installUpdate: () => ipcRenderer.invoke('update:install'),
  onUpdateState: (listener) => subscribe('update:state', listener),
  updateSettings: (patch) => ipcRenderer.invoke('settings:update', patch),
  addAccount: () => ipcRenderer.invoke('accounts:add'),
  removeAccount: (uuid) => ipcRenderer.invoke('accounts:remove', uuid),
  addServer: (name, address) => ipcRenderer.invoke('servers:add', name, address),
  removeServer: (id) => ipcRenderer.invoke('servers:remove', id),

  backupStatus: () => ipcRenderer.invoke('backup:status'),
  backupChooseFolder: () => ipcRenderer.invoke('backup:choose'),
  backupNow: () => ipcRenderer.invoke('backup:now'),
  backupStop: () => ipcRenderer.invoke('backup:stop'),
  backupRestore: (ids) => ipcRenderer.invoke('backup:restore', ids),
  onBackupStatus: (listener) => subscribe('backup:status', listener),

  windowControl: (action) => ipcRenderer.send('window:control', action)
};

contextBridge.exposeInMainWorld('astro', api);
