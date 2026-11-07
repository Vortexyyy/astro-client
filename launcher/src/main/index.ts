import { app, BrowserWindow, shell } from 'electron';
import { join } from 'node:path';
import { registerIpc } from './ipc';
import { openUninstaller } from './uninstall';
import { startUpdater } from './updater';

let mainWindow: BrowserWindow | null = null;

function createWindow(): void {
  const win = new BrowserWindow({
    width: 1280,
    height: 780,
    minWidth: 1040,
    minHeight: 640,
    show: false,
    frame: false, // custom title bar drawn by the renderer
    backgroundColor: '#07060f',
    title: 'Astro Launcher',
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  mainWindow = win;
  win.on('closed', () => {
    if (mainWindow === win) mainWindow = null;
  });

  win.once('ready-to-show', () => win.show());

  // External links (mod pages etc.) open in the user's browser, never inside the app.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://')) void shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e, url) => {
    if (url !== win.webContents.getURL()) e.preventDefault();
  });

  if (!app.isPackaged && process.env['ELECTRON_RENDERER_URL']) {
    void win.loadURL(process.env['ELECTRON_RENDERER_URL']);
  } else {
    void win.loadFile(join(__dirname, '../renderer/index.html'));
  }
}

// "Uninstall Astro Launcher" shortcut: same app, started in uninstaller mode.
const uninstallMode = process.argv.includes('--uninstall');

// Two launchers on the same data folder would race on settings.json; a second start focuses the first.
if (!uninstallMode && !app.requestSingleInstanceLock()) app.quit();
app.on('second-instance', () => {
  if (!mainWindow) return;
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.focus();
});

app.whenReady().then(() => {
  if (uninstallMode) {
    openUninstaller();
    return;
  }
  registerIpc(() => mainWindow);
  startUpdater(() => mainWindow);
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
