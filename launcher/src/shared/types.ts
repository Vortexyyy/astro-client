export type ModSource = 'modrinth' | 'curseforge';

export interface ModSearchHit {
  source: ModSource;
  id: string;
  slug?: string;
  title: string;
  description: string;
  author: string;
  iconUrl?: string;
  downloads: number;
}

export interface SearchRequest {
  profileId: string;
  source: ModSource;
  query: string;
  mcVersion: string;
  offset?: number;
}

export type LoaderId = 'fabric' | 'quilt' | 'vanilla';

export interface InstalledMod {
  /** 'local' = a jar imported from elsewhere that Modrinth could not identify */
  source: ModSource | 'local';
  projectId: string;
  versionId: string;
  title: string;
  fileName: string;
  url: string;
  hashes: { sha1?: string; sha512?: string };
}

export interface Profile {
  id: string;
  name: string;
  mcVersion: string;
  loader: LoaderId;
  /** empty for vanilla */
  loaderVersion: string;
  memoryMb: number;
  mods: InstalledMod[];
  createdAt: number;
  lastPlayed?: number;
}

export interface NewProfile {
  name: string;
  mcVersion: string;
  loader?: LoaderId;
  memoryMb?: number;
}

export interface GameVersionInfo {
  id: string;
  type: 'release' | 'snapshot';
  /** an Astro Client mod build ships with the launcher for this version */
  astro: boolean;
}

export interface Bundle {
  id: string;
  name: string;
  description: string;
  /** Modrinth project slugs */
  mods: string[];
}

export interface ModUpdate {
  projectId: string;
  source: ModSource;
  title: string;
  currentFile: string;
  newVersion: string;
}

export interface BatchResult {
  profile: Profile;
  installed: string[];
  skipped: string[];
}

// ── Importing from other launchers ───────────────────────────────────────

export type ImportSourceId =
  | 'vanilla'
  | 'prism'
  | 'polymc'
  | 'multimc'
  | 'curseforge'
  | 'modrinth'
  | 'atlauncher'
  | 'gdlauncher'
  | 'lunar'
  | 'folder';

export interface ImportCandidate {
  /** opaque handle; the main process keeps the real paths */
  key: string;
  source: ImportSourceId;
  sourceLabel: string;
  name: string;
  mcVersion: string | null;
  loader: LoaderId | 'forge' | 'neoforge' | 'unknown';
  loaderVersion: string | null;
  gameDir: string;
  modCount: number;
  worldCount: number;
  /** e.g. Lunar's per-profile HUD settings that map onto Astro modules */
  extras: string[];
  warning?: string;
}

export interface ImportOptions {
  mods: boolean;
  configs: boolean;
  resourcePacks: boolean;
  shaderPacks: boolean;
  gameOptions: boolean;
  worlds: boolean;
}

export interface ImportRequest {
  key: string;
  name: string;
  mcVersion: string;
}

export interface ImportReport {
  candidateName: string;
  profile: Profile | null;
  copied: string[];
  identifiedMods: number;
  localMods: number;
  warnings: string[];
  error?: string;
}

export interface ImportProgress {
  name: string;
  step: string;
  index: number;
  total: number;
}

/** A signed-in Microsoft account. Tokens never leave the main process. */
export interface AccountInfo {
  uuid: string;
  name: string;
  skinUrl?: string;
}

export interface ServerEntry {
  id: string;
  name: string;
  address: string;
}

export type JavaMode = 'bundled' | 'custom';

/** What the renderer is allowed to see. Secrets are reduced to booleans. */
export interface PublicSettings {
  hasCurseforgeKey: boolean;
  accounts: AccountInfo[];
  activeAccount: string | null;
  javaMode: JavaMode;
  javaPath: string;
  hideOnLaunch: boolean;
  /** copy the matching Astro Client mod (and Fabric API) into Fabric/Quilt profiles at launch */
  autoInstallAstro: boolean;
  servers: ServerEntry[];
  /** Development builds may launch offline without an account; packaged builds may not. */
  offlineAllowed: boolean;
  offlineName: string;
}

export interface SettingsPatch {
  curseforgeApiKey?: string;
  javaMode?: JavaMode;
  javaPath?: string;
  hideOnLaunch?: boolean;
  autoInstallAstro?: boolean;
  offlineName?: string;
  activeAccount?: string;
}

export type LogLevel = 'info' | 'warn' | 'error' | 'debug' | 'launcher';

export interface ConsoleLine {
  id: number;
  time: number;
  level: LogLevel;
  text: string;
}

export interface GameState {
  running: boolean;
  profileId: string | null;
  profileName: string | null;
  startedAt: number | null;
}

export type LaunchEvent =
  | { type: 'status'; message: string }
  | { type: 'progress'; task: string; current: number; total: number }
  | { type: 'error'; message: string };

export interface LaunchOptions {
  /** host[:port] to join directly once the game starts */
  server?: string;
}

// ── Backup & sync folder ─────────────────────────────────────────────────

export interface BackupProfileSummary {
  id: string;
  name: string;
  mcVersion: string;
  loader: LoaderId;
  modCount: number;
  onThisDevice: boolean;
}

export interface BackupStatus {
  /** folder the backup file lives in (e.g. inside OneDrive/Google Drive), or null when off */
  folder: string | null;
  lastBackupAt: number | null;
  busy: boolean;
  error?: string;
  /** profiles in the backup file */
  profiles: BackupProfileSummary[];
}

export interface RestoreResult {
  restored: Profile[];
  skipped: string[];
}

// ── Custom uninstaller ───────────────────────────────────────────────────

export type UninstallItemId = 'profiles' | 'game' | 'java' | 'settings';

export interface UninstallItem {
  id: UninstallItemId;
  label: string;
  description: string;
  bytes: number;
  defaultOn: boolean;
}

export interface UninstallInfo {
  /** false when running from source: data can be removed but there's no installed app to remove */
  packaged: boolean;
  installDir: string;
  items: UninstallItem[];
}

export interface UninstallResult {
  removedApp: boolean;
  failed: string[];
  note?: string;
}

export interface UninstallApi {
  info(): Promise<UninstallInfo>;
  run(selected: UninstallItemId[]): Promise<UninstallResult>;
  close(): void;
}

export interface ImportResult {
  profile: Profile;
  skipped: string[];
}

// ── Launcher self-update ─────────────────────────────────────────────────

export interface UpdateState {
  status: 'idle' | 'disabled' | 'checking' | 'none' | 'downloading' | 'ready' | 'error';
  /** the new version, while downloading or once ready */
  version?: string;
  percent?: number;
  error?: string;
}

export interface AstroApi {
  searchMods(req: SearchRequest): Promise<ModSearchHit[]>;
  installMod(profileId: string, source: ModSource, projectId: string): Promise<Profile>;
  removeMod(profileId: string, projectId: string): Promise<Profile>;
  /** copies dropped .jar files into the profile's mods folder */
  addModFiles(profileId: string, filePaths: string[]): Promise<BatchResult>;
  /** absolute path of a file dropped onto the window ('' if it has none) */
  pathForFile(file: File): string;

  listProfiles(): Promise<Profile[]>;
  createProfile(input: NewProfile): Promise<Profile>;
  deleteProfile(id: string): Promise<void>;
  openProfileFolder(id: string): Promise<void>;
  exportProfile(id: string): Promise<string | null>;
  importProfile(): Promise<ImportResult | null>;

  listBundles(): Promise<Bundle[]>;
  installBundle(profileId: string, bundleId: string): Promise<BatchResult>;
  checkUpdates(profileId: string): Promise<ModUpdate[]>;
  applyUpdates(profileId: string, projectIds: string[]): Promise<BatchResult>;

  scanLaunchers(): Promise<ImportCandidate[]>;
  pickImportFolder(): Promise<ImportCandidate[]>;
  importInstances(requests: ImportRequest[], options: ImportOptions): Promise<ImportReport[]>;
  onImportProgress(listener: (progress: ImportProgress) => void): () => void;

  listGameVersions(): Promise<GameVersionInfo[]>;
  launch(profileId: string, options?: LaunchOptions): Promise<void>;
  killGame(): Promise<void>;
  getGameState(): Promise<GameState>;
  onGameState(listener: (state: GameState) => void): () => void;
  onLaunchEvent(listener: (event: LaunchEvent) => void): () => void;

  getConsole(): Promise<ConsoleLine[]>;
  clearConsole(): Promise<void>;
  openLogsFolder(profileId: string): Promise<void>;
  onConsole(listener: (lines: ConsoleLine[]) => void): () => void;

  getSettings(): Promise<PublicSettings>;
  appVersion(): Promise<string>;
  updateState(): Promise<UpdateState>;
  checkForUpdates(): Promise<UpdateState>;
  installUpdate(): Promise<void>;
  onUpdateState(listener: (state: UpdateState) => void): () => void;
  updateSettings(patch: SettingsPatch): Promise<PublicSettings>;
  addAccount(): Promise<PublicSettings>;
  removeAccount(uuid: string): Promise<PublicSettings>;
  addServer(name: string, address: string): Promise<PublicSettings>;
  removeServer(id: string): Promise<PublicSettings>;

  backupStatus(): Promise<BackupStatus>;
  backupChooseFolder(): Promise<BackupStatus>;
  backupNow(): Promise<BackupStatus>;
  backupStop(): Promise<BackupStatus>;
  backupRestore(profileIds: string[]): Promise<RestoreResult>;
  onBackupStatus(listener: (status: BackupStatus) => void): () => void;

  windowControl(action: 'minimize' | 'maximize' | 'close'): void;
}
