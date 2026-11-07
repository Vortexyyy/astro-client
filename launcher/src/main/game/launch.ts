import type { ChildProcess } from 'node:child_process';
import { Client } from 'minecraft-launcher-core';
import { mkdir } from 'node:fs/promises';
import type { GameState, LaunchEvent, LaunchOptions, Profile } from '@shared/types';
import { paths } from '../paths';
import { getAutoInstallAstro, getJavaSettings, isValidServerAddress } from '../settings';
import { ensureFpsDefaults, prepareAstro } from './astroMod';
import { getLaunchIdentity } from './auth';
import * as gameConsole from './console';
import { ensureJavaRuntime } from './java';
import { ensureLoaderProfile, loaderLabel } from './loaders';

// ── Game process (one at a time, like most client launchers) ────────────

let state: GameState = { running: false, profileId: null, profileName: null, startedAt: null };
let child: ChildProcess | null = null;
let launching = false;
const stateListeners = new Set<(s: GameState) => void>();

function setState(next: GameState) {
  state = next;
  for (const l of stateListeners) l(state);
}

export function gameState(): GameState {
  return state;
}

export function onGameState(listener: (s: GameState) => void): () => void {
  stateListeners.add(listener);
  return () => stateListeners.delete(listener);
}

export function killGame(): void {
  if (child && !child.killed) {
    gameConsole.log('Stopping the game at your request…', 'launcher');
    child.kill();
  }
}

export async function launchProfile(profileArg: Profile, options: LaunchOptions, emit: (e: LaunchEvent) => void): Promise<void> {
  let profile = profileArg;
  if (state.running || launching) throw new Error('The game is already running.');
  if (options.server && !isValidServerAddress(options.server)) throw new Error('Invalid server address.');
  launching = true;
  const status = (message: string) => {
    emit({ type: 'status', message });
    gameConsole.log(message, 'launcher');
  };

  try {
    const loaderText = profile.loader === 'vanilla' ? 'vanilla' : `${loaderLabel(profile.loader)} ${profile.loaderVersion}`;
    gameConsole.log(`──── Launching ${profile.name} (Minecraft ${profile.mcVersion}, ${loaderText}) ────`, 'launcher');

    status('Signing in…');
    const identity = await getLaunchIdentity();
    gameConsole.log(`Account: ${identity.label}`, 'launcher');

    const java = await getJavaSettings();
    let javaPath: string;
    if (java.mode === 'custom' && java.path) {
      javaPath = java.path;
      status(`Using custom Java: ${javaPath}`);
    } else {
      status('Preparing Java runtime…');
      const runtime = await ensureJavaRuntime(profile.mcVersion, (current, total) =>
        emit({ type: 'progress', task: 'Java runtime', current, total })
      );
      javaPath = runtime.executable;
      gameConsole.log(`Java: Mojang ${runtime.component} ${runtime.version}`, 'launcher');
    }

    if (profile.loader !== 'vanilla') status(`Installing ${loaderLabel(profile.loader)}…`);
    const custom = await ensureLoaderProfile(profile.loader, profile.mcVersion, profile.loaderVersion);
    if (await getAutoInstallAstro()) profile = await prepareAstro(profile, status);
    await ensureFpsDefaults(profile);
    await mkdir(paths.instanceDir(profile.id), { recursive: true });

    const client = new Client();
    client.on('progress', (p: { type: string; task: number; total: number }) =>
      emit({ type: 'progress', task: p.type, current: p.task, total: p.total })
    );
    client.on('debug', (line: string) => gameConsole.log(String(line), 'debug'));
    client.on('data', (chunk: string) => gameConsole.log(String(chunk)));

    status('Checking game files…');
    const proc = await client.launch({
      root: paths.gameRoot,
      version: { number: profile.mcVersion, type: 'release', ...(custom ? { custom } : {}) },
      memory: { max: `${profile.memoryMb}M`, min: '1024M' },
      javaPath,
      authorization: identity.user,
      overrides: { gameDirectory: paths.instanceDir(profile.id) },
      ...(options.server ? { quickPlay: { type: 'multiplayer' as const, identifier: options.server } } : {})
    });
    if (!proc) throw new Error('Minecraft failed to start. Check the Console for details.');

    child = proc;
    proc.once('exit', (code) => {
      child = null;
      gameConsole.log(`──── Game exited with code ${code ?? 'unknown'} ────`, code ? 'error' : 'launcher');
      setState({ running: false, profileId: null, profileName: null, startedAt: null });
    });
    setState({ running: true, profileId: profile.id, profileName: profile.name, startedAt: Date.now() });
    status(options.server ? `Game running, joining ${options.server}` : 'Game running');
  } catch (err) {
    gameConsole.log((err as Error).message, 'error');
    throw err;
  } finally {
    launching = false;
  }
}
