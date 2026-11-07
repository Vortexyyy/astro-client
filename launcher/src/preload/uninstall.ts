import { contextBridge, ipcRenderer } from 'electron';
import type { UninstallApi } from '@shared/types';

const api: UninstallApi = {
  info: () => ipcRenderer.invoke('uninstall:info'),
  run: (selected) => ipcRenderer.invoke('uninstall:run', selected),
  close: () => ipcRenderer.send('uninstall:close')
};

contextBridge.exposeInMainWorld('astroUninstall', api);
