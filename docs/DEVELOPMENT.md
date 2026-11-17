# Astro Client: developer guide

A Minecraft client in two parts:

| Part | Stack | Folder |
| --- | --- | --- |
| Desktop launcher | Electron 45 + React 19 + TypeScript (electron-vite) | [`launcher/`](../launcher) |
| In-game client mod | Fabric Loader + Fabric API, Minecraft **1.21.11, 1.21.10, 1.21.1**, Java 21 | [`mod/`](../mod) |

---

## 1. What it does

**Launcher**

- **Backup & sync** (Settings), no account or server: pick a folder inside OneDrive, Google
  Drive or Dropbox and Astro keeps `astro-backup.json` there, updated automatically after every
  change. On another PC, pick the same folder and *Restore* rebuilds the profiles and
  re-downloads their mods. Several PCs can share the folder; profiles are merged, and deletions
  carry over. Microsoft tokens, passwords, API keys and worlds are never written to it.
  To give a profile to a friend, use Profiles → *Export* and send them the `.astropack`.
- **Astro Client installed for you**: launching a Fabric/Quilt profile on a supported version
  copies the matching Astro mod in, plus Fabric API and the performance mods (Sodium, Lithium,
  FerriteCore, ImmediatelyFast, Entity Culling). New profiles start with VSync off and FPS
  uncapped. Turn this off in Settings.
- **Every Minecraft version** in the version picker (releases, optional snapshots); versions with
  an Astro build are listed first and marked ★.
- **Microsoft accounts.** "Add Microsoft account" opens Microsoft's own sign-in page; Astro only
  stores the resulting refresh token (encrypted with the OS keychain). Several accounts can be
  added and switched from the title bar; the active account's skin is shown as its avatar.
  Every launch refreshes the session and passes a real Minecraft access token to the game, so
  online-mode servers work. Packaged builds require an account; development builds can also
  launch offline for testing.
- **Bundled Java.** Each Minecraft version declares the Java runtime it needs; Astro downloads
  that exact runtime from Mojang's runtime manifest (the same one the official launcher uses),
  SHA-1 checks all of its files, and caches it under `<userData>/runtimes`. A custom Java path
  can be set instead.
- **Launch screen** with a large Launch button, profile picker, live progress, and a
  *Quick join* row of saved servers that launch straight into multiplayer.
- **Console**: the game's and launcher's log, kept in the main process so it survives page
  changes. Filter by text and level (Info/Warn/Error/Launcher/Debug), copy, clear, open the
  logs folder, force-stop the game.
- **Import from other launchers** (Profiles → *Import from another launcher*). Astro finds
  installs automatically and shows every instance with its version, loader, mod and world count:

  | Launcher | What it reads |
  | --- | --- |
  | Lunar Client | `profiles/main` game folder, `profiles/*/mods/fabric-<ver>`, and each settings profile's `mods.json` (FPS, Ping, Coordinates, Keystrokes, Zoom are carried over to Astro's HUD) |
  | Modrinth App | `app.db` (read-only; instance name/version/loader columns only) or legacy `profile.json` |
  | Prism Launcher / PolyMC / MultiMC | `instance.cfg` + `mmc-pack.json`, custom `InstanceDir` |
  | CurseForge app | `minecraftinstance.json` |
  | Official Minecraft Launcher | `launcher_profiles.json` (incl. custom game dirs) |
  | ATLauncher, GDLauncher (legacy + Carbon) | `instance.json` / `config.json` |
  | Any folder | an instance, a launcher root, or a plain `.minecraft`; the loader and version are detected from the mod jars |

  You choose what to bring: mods, mod configs, resource packs, shader packs, options & server
  list, worlds. Copied mods are matched on Modrinth by SHA-1, so they get update checks; jars
  Modrinth doesn't know are kept as **LOCAL**. Forge/NeoForge instances come over as vanilla
  (worlds, packs, settings) with a warning. Source launchers are never modified, and the UI only
  passes opaque keys, so it cannot make the importer read or copy arbitrary paths.
- **Bundles**: pick curated one-click sets when creating a profile (Essentials, Performance,
  Shaders, Quality of life); mods missing for that Minecraft version are skipped, not fatal.
- **Mod updates**: *Check for updates* asks Modrinth for every tracked mod in one request (by
  file hash) and CurseForge per mod; *Update all* swaps in the new files.
- **Loaders**: Fabric, Quilt (runs Fabric mods too) and vanilla profiles.
- **Mods**: Modrinth + CurseForge search and install, with required dependencies resolved.
- **Profiles**: separate mods/config/worlds per profile; `.astropack` export, `.astropack` /
  `.mrpack` (Fabric/Quilt) import.
- Frameless window with a custom title bar; the launcher stays open while you play (hiding is optional).

**Mod**

- Branded loading screen. **Main menu** with the menu on the left and your account's 3D skin on
  the right (drag to rotate).
- **Custom Singleplayer and Multiplayer screens**: world cards (icon, last played, mode,
  version; *Advanced…* opens vanilla's list for edit/delete/backup) and server cards (favicon,
  MOTD, live ping, players, edit/delete, direct connect). Same saves and servers.dat as vanilla.
- **Customize** (main menu): colour themes (Nebula, Aurora, Solar, Crimson, Frost, Mono), your own
  PNG background pictures per screen (drop them in `config/astroclient/backgrounds`), picture
  dim, panel opacity, button style, player model on/off, and a switch back to vanilla menus.
- **Right Shift mod menu**: centred panel with category tabs, search-as-you-type and a grid of
  module cards (click to toggle, ⚙ or right-click for settings). Settings persist to
  `config/astroclient.json`.
- Modules: Freelook (hold **Left Alt**), Zoom (hold **C**), Fullbright, custom Crosshair (style,
  colour, size, gap, thickness, outline), Toggle Sprint; HUD: FPS, CPS, Coordinates, Ping,
  Keystrokes, Armor (durability), Potions (time left).
- **HUD editor** (mod menu → *✎ Edit HUD*): drag any HUD element anywhere; snaps to screen edges,
  centre lines and other elements; right-click resets one; positions are saved per element and
  scale with the window.
- Light on FPS: the whole HUD is drawn as one batch (on 1.21.1 each shape used to be its own
  GPU draw call).

## 2. Architecture

```
┌────────────────────────────── Astro Launcher (Electron) ───────────────────────────────┐
│ Renderer (React, sandboxed)      Preload            Main process (Node)                │
│ ┌─────────────────────────┐   ┌────────────┐   ┌─────────────────────────────────────┐ │
│ │ TitleBar + AccountMenu  │   │window.astro│   │ ipc.ts                              │ │
│ │ Home · Mods · Profiles  │◄─►│ typed IPC  │◄─►│  game/auth.ts     Microsoft (msmc)  │ │
│ │ Console · Settings      │   │  bridge    │   │  game/java.ts     Mojang Java       │ │
│ │ (no Node, no network)   │   └────────────┘   │  game/launch.ts   Fabric + MCLC     │ │
│ └─────────────────────────┘                    │  game/console.ts  log ring buffer   │ │
│                                                │  providers/       Modrinth, CF      │ │
│                                                │  profiles/        store, packs      │ │
│                                                └──────────────────┬──────────────────┘ │
└───────────────────────────────────────────────────────────────────┼────────────────────┘
                                         runtimes/java-runtime-delta │ spawns javaw + token
                                                                    ▼
                    ┌────── Minecraft 1.21.11 / 1.21.10 / 1.21.1 + Fabric ───────┐
                    │ astroclient.jar: SplashOverlayMixin · MinecraftClientMixin │
                    │ (title screen) · GameRendererMixin (zoom) · ModMenuScreen  │
                    └────────────────────────────────────────────────────────────┘
```

Design rules the code follows:

- **The renderer never touches the network, disk or secrets.** Tokens and the CurseForge key
  stay in the main process, encrypted with Electron `safeStorage`.
- **Every download is allow-listed and hash-checked**: mods only from `cdn.modrinth.com` /
  `*.forgecdn.net`, Java only from Mojang's hosts. Imported packs cannot write outside their
  profile folder.
- **One shared game root, one folder per profile**: `<userData>/minecraft` holds assets,
  libraries and versions; `profiles/<id>/instance` holds that profile's mods, config and saves.

## 3. Folder structure

```
astro-client/
├── launcher/src/
│   ├── shared/types.ts                 types shared by main, preload and renderer
│   ├── main/
│   │   ├── index.ts                    frameless BrowserWindow, security defaults
│   │   ├── ipc.ts                      every IPC handler; hides/shows window around the game
│   │   ├── settings.ts                 settings.json, accounts, servers, encrypted secrets
│   │   ├── net/http.ts                 JSON fetch, allow-listed + hashed downloads
│   │   ├── util/fs.ts                  path-traversal guard
│   │   ├── providers/                  modrinth.ts, curseforge.ts
│   │   ├── profiles/                   store.ts, packs.ts, bundles.ts, updates.ts
│   │   ├── importers/                  index.ts (registry + copy pipeline), sources.ts
│   │   │                               (one scanner per launcher), inspect.ts, lunar.ts
│   │   ├── backup.ts                   backup & sync folder (merge, restore, deletions)
│   │   └── game/                       auth.ts, java.ts, loaders.ts, launch.ts, console.ts,
│   │                                   astroMod.ts (bundled mod + perf mods + FPS defaults)
│   ├── preload/index.ts
│   └── renderer/src/
│       ├── App.tsx  styles.css  api.ts
│       ├── components/                 TitleBar, SkinHead, ImportWizard, VersionSelect,
│       │                               BackupCard, …
│       └── pages/                      Home, Browse, Profiles, Console, Settings
└── mod/
    ├── versions/<mc>/gradle.properties one Gradle subproject per Minecraft version
    └── src/
        ├── common/                     all features, compiled once per version
        │   └── java/dev/astroclient/   AstroClient, module/, gui/{menu,title,worlds,servers,
        │                               custom,theme,render,splash}/, mixin/ (splash, menus)
        ├── legacy/                     shims for 1.21.1 (compat/Gfx, Keys, AstroScreen,
        │                               Platform; GameRendererMixin)
        ├── modern/                     the same shims for 1.21.9+ (new input/GUI APIs)
        └── mc1_21_10/  mc1_21_11/      tiny per-version differences (button base, server ping)
```

Shared code only calls Minecraft APIs that are identical across versions; anything that
changed (input events, GUI matrices, textures, key categories, FOV type, skin supplier, a few
constructors) goes through `dev.astroclient.compat`, which exists once per version folder.

## 4. Running it

```bash
cd launcher
npm install
npm run dev
```

```bash
cd mod
./gradlew copyToLauncher         # builds every version, copies jars to launcher/resources/astro-mod
./gradlew :mc1_21_11:runClient   # dev game (also :mc1_21_10, :mc1_21_1)
```

After `copyToLauncher`, just launch any Fabric/Quilt profile on 1.21.11, 1.21.10 or 1.21.1 and
the launcher installs Astro Client (and Fabric API + performance mods) automatically.

### Windows installer

```bash
cd mod && ./gradlew copyToLauncher   # bundle the latest mod jars first
cd ../launcher && npm run dist       # → dist/Astro-Setup-<version>.exe
```

The installer lets you choose the install folder and adds Start menu and desktop shortcuts.
An extra setup page (`build/installer.nsh`) offers an **Uninstall Astro Launcher** shortcut: it
opens Astro's own uninstall window (`Astro Launcher.exe --uninstall`, `src/main/uninstall.ts`),
where you choose what to remove (profiles & worlds go to the Recycle Bin; game files, Java,
settings) before it runs the regular uninstaller. Windows Settings → Apps still works too.
Bundled libraries are kept patched via `overrides` in `package.json` (`npm audit --omit=dev`).
It isn't code-signed, so Windows SmartScreen shows "Windows protected your PC" →
*More info* → *Run anyway*. The installed app requires a Microsoft account (offline play is
only available when running from source).

If the build fails with `!include: could not open file`, the project path is longer than NSIS can
handle (260 characters). Build from a short path, e.g. `subst X: <path to launcher>`, run
`npm run dist` from `X:\`, then `subst X: /d`.

## 5. Before you distribute it

- **Register your own Microsoft app.** Sign-in currently uses msmc's default public client,
  which is fine for development. A public launcher should register an Azure app, apply for
  Minecraft API access at <https://aka.ms/mce-reviewappid>, and run with
  `ASTRO_MS_CLIENT_ID=<your client id>` (see `launcher/src/main/game/auth.ts`).
- For a wider release, buy a code-signing certificate so Windows trusts the installer
  (electron-builder signs automatically once `CSC_LINK` / `CSC_KEY_PASSWORD` are set).
- Keep branding your own: Astro's name, logo and palette are original; don't reuse other
  clients' assets.

## 6. Supported versions

The mod builds for 1.21.11, 1.21.10 and 1.21.1. Adding another 1.21.x version is usually a new
`versions/<mc>/gradle.properties` plus, if an API changed, a shim in its folder. 26.x ships
unobfuscated with Mojang names (no Yarn), so it needs its own mappings setup. The launcher can
run any version; Astro's mod is simply skipped where no build exists.

## Updates and releasing

Source lives in the private `Vortexyyy/astro-client` repo. Installers are published to the public
`Vortexyyy/astro-releases` repo, which holds builds only, so the app needs no token to update.
Installed launchers check it on startup and every 4 hours, download new versions in the background,
and install them when Astro closes (or straight away from **Settings → Launcher updates**).

To ship a new version, bump `version` in `launcher/package.json`, then from `launcher/`:

```bash
bash scripts/release.sh
```
