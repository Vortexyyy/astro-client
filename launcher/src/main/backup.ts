import { dialog, type BrowserWindow } from 'electron';
import { readFile, rename, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import type { AccountInfo, BackupProfileSummary, BackupStatus, Profile, RestoreResult, ServerEntry } from '@shared/types';
import { paths } from './paths';
import { addModFile, createProfile, listProfiles, saveProfile } from './profiles/store';
import { getSyncableSettings, mergeServers } from './settings';

/**
 * Backup & sync without a server: one JSON file in a folder the player picks — ideally inside
 * OneDrive, Google Drive or Dropbox, which then copies it to their other PCs. Only manifests are
 * stored (profiles and their mod lists, servers, preferences, Minecraft account names); mod files
 * are re-downloaded on restore and no tokens, keys or passwords are ever written.
 */
const FILE_NAME = 'astro-backup.json';

interface BackupDoc {
  format: 'astro-backup';
  version: 1;
  updatedAt: number;
  profiles: Profile[];
  /** ids of profiles deleted on some PC, so the others don't bring them back */
  deleted: string[];
  servers: ServerEntry[];
  minecraftAccounts: AccountInfo[];
  preferences: { javaMode: string; hideOnLaunch: boolean; autoInstallAstro: boolean };
}

interface LocalState {
  folder: string | null;
  lastBackupAt: number | null;
  deleted: string[];
}

const stateFile = () => join(paths.root, 'backup-state.json');
let busy = false;
let lastError: string | undefined;
let lastDoc: BackupDoc | null = null;
let timer: NodeJS.Timeout | null = null;
const listeners = new Set<(s: BackupStatus) => void>();

async function loadState(): Promise<LocalState> {
  try {
    return { folder: null, lastBackupAt: null, deleted: [], ...JSON.parse(await readFile(stateFile(), 'utf8')) };
  } catch {
    return { folder: null, lastBackupAt: null, deleted: [] };
  }
}

async function saveState(state: LocalState): Promise<void> {
  await writeFile(stateFile(), JSON.stringify(state));
}

async function readDoc(folder: string): Promise<BackupDoc | null> {
  try {
    const doc = JSON.parse(await readFile(join(folder, FILE_NAME), 'utf8')) as BackupDoc;
    return doc.format === 'astro-backup' && Array.isArray(doc.profiles) ? doc : null;
  } catch {
    return null;
  }
}

export function onBackupStatus(listener: (s: BackupStatus) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export async function backupStatus(): Promise<BackupStatus> {
  const [state, local] = await Promise.all([loadState(), listProfiles()]);
  if (state.folder && !lastDoc) lastDoc = await readDoc(state.folder);
  const localIds = new Set(local.map((p) => p.id));
  const profiles: BackupProfileSummary[] = (state.folder && lastDoc ? lastDoc.profiles : []).map((p) => ({
    id: p.id,
    name: p.name,
    mcVersion: p.mcVersion,
    loader: p.loader,
    modCount: p.mods.length,
    onThisDevice: localIds.has(p.id)
  }));
  return { folder: state.folder, lastBackupAt: state.lastBackupAt, busy, error: lastError, profiles };
}

async function emit(): Promise<BackupStatus> {
  const status = await backupStatus();
  for (const l of listeners) l(status);
  return status;
}

/** Merges this PC's data with the backup file (keeping other PCs' profiles) and writes it back. */
export async function backupNow(): Promise<BackupStatus> {
  const state = await loadState();
  if (!state.folder || busy) return backupStatus();
  busy = true;
  lastError = undefined;
  await emit();
  try {
    const existing = await readDoc(state.folder);
    const deleted = new Set([...(existing?.deleted ?? []), ...state.deleted]);
    if (existing?.servers?.length) await mergeServers(existing.servers);

    const local = await listProfiles();
    const localIds = new Set(local.map((p) => p.id));
    const profiles = [...local, ...(existing?.profiles ?? []).filter((p) => !localIds.has(p.id))].filter((p) => !deleted.has(p.id));
    const settings = await getSyncableSettings();
    const accounts = new Map((existing?.minecraftAccounts ?? []).map((a) => [a.uuid, a]));
    for (const a of settings.accounts) accounts.set(a.uuid, a);

    const doc: BackupDoc = {
      format: 'astro-backup',
      version: 1,
      updatedAt: Date.now(),
      profiles,
      deleted: [...deleted].slice(-500),
      servers: settings.servers,
      minecraftAccounts: [...accounts.values()],
      preferences: { javaMode: settings.javaMode, hideOnLaunch: settings.hideOnLaunch, autoInstallAstro: settings.autoInstallAstro }
    };
    // write-then-rename so a cloud-drive client never uploads a half-written file
    const file = join(state.folder, FILE_NAME);
    await writeFile(`${file}.tmp`, JSON.stringify(doc, null, 2));
    await rename(`${file}.tmp`, file);
    lastDoc = doc;
    await saveState({ ...state, lastBackupAt: Date.now(), deleted: [] });
  } catch (err) {
    lastError = (err as Error).message;
  } finally {
    busy = false;
  }
  return emit();
}

/** Debounced backup after local changes (profiles, mods, servers, settings). */
export function scheduleBackup(): void {
  if (timer) clearTimeout(timer);
  timer = setTimeout(() => {
    timer = null;
    void loadState().then((s) => (s.folder ? backupNow() : undefined));
  }, 3000);
}

export async function noteProfileDeleted(id: string): Promise<void> {
  const state = await loadState();
  if (!state.folder) return;
  if (!state.deleted.includes(id)) state.deleted.push(id);
  await saveState(state);
  scheduleBackup();
}

export async function chooseBackupFolder(win: BrowserWindow): Promise<BackupStatus> {
  const { canceled, filePaths } = await dialog.showOpenDialog(win, {
    title: 'Choose a folder for your Astro backup (e.g. inside OneDrive, Google Drive or Dropbox)',
    properties: ['openDirectory', 'createDirectory']
  });
  if (canceled || !filePaths[0]) return backupStatus();
  const state = await loadState();
  lastDoc = null;
  await saveState({ ...state, folder: filePaths[0], lastBackupAt: null });
  return backupNow();
}

export async function stopBackup(): Promise<BackupStatus> {
  const state = await loadState();
  lastDoc = null;
  lastError = undefined;
  await saveState({ ...state, folder: null, deleted: [] });
  return emit();
}

/** Recreates backed-up profiles on this PC and re-downloads their mods (hash-checked). */
export async function restoreProfiles(ids: string[]): Promise<RestoreResult> {
  const state = await loadState();
  const doc = state.folder ? await readDoc(state.folder) : null;
  const restored: Profile[] = [];
  const skipped: string[] = [];
  for (const id of ids) {
    const source = doc?.profiles.find((p) => p.id === id);
    if (!source) continue;
    try {
      const profile = await createProfile({
        id: source.id,
        name: source.name,
        mcVersion: source.mcVersion,
        loader: source.loader,
        loaderVersion: source.loaderVersion || undefined,
        memoryMb: source.memoryMb
      });
      for (const mod of source.mods) {
        if (mod.source === 'local') {
          skipped.push(`${source.name}: ${mod.fileName} was a local file and isn't in the backup`);
          continue;
        }
        try {
          await addModFile(profile, mod);
        } catch (err) {
          skipped.push(`${source.name}: ${mod.title} (${(err as Error).message})`);
        }
      }
      restored.push(await saveProfile(profile));
    } catch (err) {
      skipped.push(`${source.name}: ${(err as Error).message}`);
    }
  }
  await emit();
  return { restored, skipped };
}
