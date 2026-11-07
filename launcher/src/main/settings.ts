import { app, safeStorage } from 'electron';
import { randomUUID } from 'node:crypto';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import type { AccountInfo, JavaMode, PublicSettings, ServerEntry, SettingsPatch } from '@shared/types';
import { paths } from './paths';

interface StoredAccount extends AccountInfo {
  /** base64 of the safeStorage-encrypted Microsoft refresh token */
  refreshToken: string;
}

interface StoredSettings {
  /** bumped when a stored default changes and existing users should get the new value */
  version: number;
  javaMode: JavaMode;
  autoInstallAstro: boolean;
  javaPath: string;
  hideOnLaunch: boolean;
  offlineName: string;
  accounts: StoredAccount[];
  activeAccount: string | null;
  servers: ServerEntry[];
  secrets: { curseforgeApiKey?: string };
}

const SETTINGS_VERSION = 2;

const defaults: StoredSettings = {
  version: SETTINGS_VERSION,
  javaMode: 'bundled',
  autoInstallAstro: true,
  javaPath: '',
  hideOnLaunch: false,
  offlineName: 'AstroPilot',
  accounts: [],
  activeAccount: null,
  servers: [],
  secrets: {}
};

let cache: StoredSettings | null = null;

async function load(): Promise<StoredSettings> {
  if (cache) return cache;
  let raw: string;
  try {
    raw = await readFile(paths.settingsFile, 'utf8');
  } catch (err) {
    if ((err as NodeJS.ErrnoException).code !== 'ENOENT') throw err; // don't cache empty settings over a file we couldn't read
    cache = { ...defaults, secrets: {} };
    return cache;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<StoredSettings>;
    cache = { ...defaults, ...parsed, secrets: { ...parsed.secrets } };
    if ((parsed.version ?? 1) < 2) {
      // v2: the launcher stays open while playing by default
      cache = { ...cache, version: SETTINGS_VERSION, hideOnLaunch: false };
      await save(cache);
    }
  } catch {
    // corrupt file: keep a copy so accounts can be recovered instead of silently overwritten
    await copyFile(paths.settingsFile, `${paths.settingsFile}.corrupt-${Date.now()}`).catch(() => undefined);
    cache = { ...defaults, secrets: {} };
  }
  return cache;
}

async function save(s: StoredSettings): Promise<PublicSettings> {
  cache = s;
  await mkdir(dirname(paths.settingsFile), { recursive: true });
  await writeFile(paths.settingsFile, JSON.stringify(s, null, 2));
  return toPublic(s);
}

function encrypt(value: string): string {
  if (!safeStorage.isEncryptionAvailable()) {
    throw new Error('OS secure storage is unavailable; refusing to store secrets in plain text.');
  }
  return safeStorage.encryptString(value).toString('base64');
}

function decrypt(value: string | undefined): string | undefined {
  if (!value) return undefined;
  try {
    return safeStorage.decryptString(Buffer.from(value, 'base64'));
  } catch {
    return undefined;
  }
}

function toPublic(s: StoredSettings): PublicSettings {
  return {
    hasCurseforgeKey: Boolean(s.secrets.curseforgeApiKey),
    accounts: s.accounts.map(({ uuid, name, skinUrl }) => ({ uuid, name, skinUrl })),
    activeAccount: s.activeAccount,
    javaMode: s.javaMode,
    autoInstallAstro: s.autoInstallAstro,
    javaPath: s.javaPath,
    hideOnLaunch: s.hideOnLaunch,
    servers: s.servers,
    offlineAllowed: !app.isPackaged,
    offlineName: s.offlineName
  };
}

export async function getPublicSettings(): Promise<PublicSettings> {
  return toPublic(await load());
}

export async function getJavaSettings(): Promise<{ mode: JavaMode; path: string }> {
  const s = await load();
  return { mode: s.javaMode, path: s.javaPath };
}

export async function getAutoInstallAstro(): Promise<boolean> {
  return (await load()).autoInstallAstro;
}

/** Everything the backup file carries besides profiles (never tokens or keys). */
export async function getSyncableSettings(): Promise<{ javaMode: JavaMode; hideOnLaunch: boolean; autoInstallAstro: boolean; servers: ServerEntry[]; accounts: AccountInfo[] }> {
  const s = await load();
  return {
    javaMode: s.javaMode,
    hideOnLaunch: s.hideOnLaunch,
    autoInstallAstro: s.autoInstallAstro,
    servers: s.servers,
    accounts: s.accounts.map(({ uuid, name, skinUrl }) => ({ uuid, name, skinUrl }))
  };
}

/** Adds servers from the backup that aren't saved locally (matched by address). */
export async function mergeServers(servers: ServerEntry[]): Promise<PublicSettings> {
  const s = await load();
  const known = new Set(s.servers.map((x) => x.address));
  const add = servers.filter((x) => isValidServerAddress(x.address) && !known.has(x.address)).map((x) => ({ ...x, id: randomUUID() }));
  return save({ ...s, servers: [...s.servers, ...add] });
}

export async function getHideOnLaunch(): Promise<boolean> {
  return (await load()).hideOnLaunch;
}

export async function getOfflineName(): Promise<string> {
  return (await load()).offlineName;
}

export async function getCurseforgeKey(): Promise<string | undefined> {
  return decrypt((await load()).secrets.curseforgeApiKey);
}

export async function updateSettings(patch: SettingsPatch): Promise<PublicSettings> {
  const s = await load();
  const next: StoredSettings = { ...s, secrets: { ...s.secrets } };
  if (patch.javaMode === 'bundled' || patch.javaMode === 'custom') next.javaMode = patch.javaMode;
  if (patch.javaPath !== undefined) next.javaPath = patch.javaPath.trim();
  if (patch.hideOnLaunch !== undefined) next.hideOnLaunch = Boolean(patch.hideOnLaunch);
  if (patch.autoInstallAstro !== undefined) next.autoInstallAstro = Boolean(patch.autoInstallAstro);
  if (patch.offlineName !== undefined) {
    const name = patch.offlineName.trim();
    if (!/^\w{3,16}$/.test(name)) throw new Error('Offline name must be 3-16 letters, digits or _.');
    next.offlineName = name;
  }
  if (patch.activeAccount !== undefined) {
    if (!s.accounts.some((a) => a.uuid === patch.activeAccount)) throw new Error('Unknown account');
    next.activeAccount = patch.activeAccount;
  }
  if (patch.curseforgeApiKey !== undefined) {
    const key = patch.curseforgeApiKey.trim();
    next.secrets.curseforgeApiKey = key ? encrypt(key) : undefined;
  }
  return save(next);
}

// ── Accounts ────────────────────────────────────────────────────────────

/** Adds or refreshes an account (matched by UUID) and makes it the active one. */
export async function upsertAccount(info: AccountInfo, refreshToken: string): Promise<PublicSettings> {
  const s = await load();
  const stored: StoredAccount = { ...info, refreshToken: encrypt(refreshToken) };
  const exists = s.accounts.some((a) => a.uuid === info.uuid);
  return save({
    ...s,
    accounts: exists ? s.accounts.map((a) => (a.uuid === info.uuid ? stored : a)) : [...s.accounts, stored],
    activeAccount: info.uuid
  });
}

export async function removeAccount(uuid: string): Promise<PublicSettings> {
  const s = await load();
  const accounts = s.accounts.filter((a) => a.uuid !== uuid);
  const activeAccount = s.activeAccount === uuid ? (accounts[0]?.uuid ?? null) : s.activeAccount;
  return save({ ...s, accounts, activeAccount });
}

export async function getActiveAccount(): Promise<{ info: AccountInfo; refreshToken: string } | null> {
  const s = await load();
  const account = s.accounts.find((a) => a.uuid === s.activeAccount);
  const token = decrypt(account?.refreshToken);
  if (!account || !token) return null;
  return { info: { uuid: account.uuid, name: account.name, skinUrl: account.skinUrl }, refreshToken: token };
}

// ── Servers ─────────────────────────────────────────────────────────────

const ADDRESS = /^[a-z0-9.-]+(:\d{1,5})?$/i;

export async function addServer(name: string, address: string): Promise<PublicSettings> {
  const s = await load();
  const cleanName = name.trim().slice(0, 32);
  const cleanAddress = address.trim().toLowerCase();
  if (!cleanName) throw new Error('Server name is required.');
  if (!ADDRESS.test(cleanAddress)) throw new Error('Enter a server address like play.example.net or host:25565.');
  return save({ ...s, servers: [...s.servers, { id: randomUUID(), name: cleanName, address: cleanAddress }] });
}

export async function removeServer(id: string): Promise<PublicSettings> {
  const s = await load();
  return save({ ...s, servers: s.servers.filter((x) => x.id !== id) });
}

export function isValidServerAddress(address: string): boolean {
  return ADDRESS.test(address);
}
