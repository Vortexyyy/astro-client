import { clipboard, shell } from 'electron';
import { readdir, readFile, stat } from 'node:fs/promises';
import { homedir, userInfo } from 'node:os';
import { join } from 'node:path';
import type { CrashInfo } from '@shared/types';
import { paths } from '../paths';
import { assertProfileId } from '../profiles/store';
import { getPlayerNames } from '../settings';
import { within } from '../util/fs';
import * as gameConsole from './console';
import { makeRedactor, summarizeCrash } from './crashRules';
import type { GameExit } from './launch';

/** Crash summaries are built entirely on this PC; nothing is uploaded. */
const MAX_REPORT_BYTES = 2 * 1024 * 1024;
const CRASH_FILE = /^crash-[\w.-]+\.txt$/;

let lastCopyText = '';

export async function redactor(): Promise<(text: string) => string> {
  let username = '';
  try {
    username = userInfo().username;
  } catch {
    // no OS user info (rare); the home path still gets redacted
  }
  return makeRedactor({ home: homedir(), username, players: await getPlayerNames().catch(() => []) });
}

async function newestCrashReport(profileId: string, since: number): Promise<{ name: string; text: string } | null> {
  const dir = join(paths.instanceDir(profileId), 'crash-reports');
  let names: string[];
  try {
    names = (await readdir(dir)).filter((n) => CRASH_FILE.test(n));
  } catch {
    return null;
  }
  let best: { name: string; mtime: number; size: number } | null = null;
  for (const name of names) {
    const s = await stat(join(dir, name)).catch(() => null);
    if (!s || s.mtimeMs < since - 5000) continue;
    if (!best || s.mtimeMs > best.mtime) best = { name, mtime: s.mtimeMs, size: s.size };
  }
  if (!best) return null;
  const buf = await readFile(join(dir, best.name));
  return { name: best.name, text: buf.subarray(0, MAX_REPORT_BYTES).toString('utf8') };
}

/** Returns a crash summary when the session ended abnormally, or null for a normal exit / Force stop. */
export async function analyzeExit(exit: GameExit): Promise<CrashInfo | null> {
  if (exit.stoppedByUser) return null;
  const report = await newestCrashReport(exit.profileId, exit.startedAt);
  const abnormal = (exit.code !== null && exit.code !== 0) || (exit.code === null && exit.signal !== null);
  if (!abnormal && !report) return null;

  const sessionLog = gameConsole
    .getLines()
    .filter((l) => l.time >= exit.startedAt - 60_000 && l.level !== 'debug')
    .slice(-1500)
    .map((l) => l.text)
    .join('\n');
  const text = `${report?.text ?? ''}\n${sessionLog}`;
  const s = summarizeCrash(text, exit.code);
  const redact = await redactor();

  const info: CrashInfo = {
    profileId: exit.profileId,
    profileName: redact(exit.profileName),
    exitCode: exit.code,
    cause: s.cause,
    title: s.title,
    summary: redact(s.summary),
    tips: s.tips.map(redact),
    evidence: s.evidence.map(redact),
    crashReport: report?.name ?? null,
    at: exit.endedAt
  };
  lastCopyText = [
    `Astro Launcher crash summary`,
    `Profile: ${info.profileName}`,
    `Exit code: ${info.exitCode ?? 'unknown'}`,
    `Likely cause: ${info.title}`,
    info.summary,
    '',
    ...info.tips.map((t) => `- ${t}`),
    ...(info.evidence.length ? ['', 'From the log:', ...info.evidence] : []),
    ...(report ? ['', `Crash report: ${report.name}`, '', redact(report.text.slice(0, 20_000))] : [])
  ].join('\n');
  gameConsole.log(`Crash helper: ${info.title}. ${info.summary}`, 'launcher');
  return info;
}

export function copyCrashDetails(): void {
  if (lastCopyText) clipboard.writeText(lastCopyText);
}

async function open(file: string): Promise<void> {
  const error = await shell.openPath(file);
  if (error) throw new Error(`Couldn't open the file: ${error}`);
}

export async function openCrashReport(profileId: string, fileName: string): Promise<void> {
  assertProfileId(profileId);
  if (!CRASH_FILE.test(fileName)) throw new Error('Invalid crash report name');
  await open(within(join(paths.instanceDir(profileId), 'crash-reports'), fileName));
}

export async function openLatestLog(profileId: string): Promise<void> {
  assertProfileId(profileId);
  const file = join(paths.instanceDir(profileId), 'logs', 'latest.log');
  if (!(await stat(file).catch(() => null))) throw new Error('No log file was written for this session.');
  await open(file);
}
