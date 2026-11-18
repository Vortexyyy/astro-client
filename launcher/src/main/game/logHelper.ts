import { clipboard, dialog, type BrowserWindow } from 'electron';
import { open, stat } from 'node:fs/promises';
import { basename, extname } from 'node:path';
import type { LogAnalysis, LogFile } from '@shared/types';
import { redactor } from './crash';
import { analyzeLog, MAX_INPUT_CHARS } from './logAnalyzer';

/**
 * Log helper I/O. Text pasted or opened here is analysed in memory only: it is never written
 * to disk, never sent to the console log and never uploaded.
 */
const MAX_FILE_BYTES = 5 * 1024 * 1024;
const LOG_EXT = new Set(['.log', '.txt']);

export async function analyzePastedLog(text: unknown): Promise<LogAnalysis> {
  return analyzeLog(typeof text === 'string' ? text : '', await redactor());
}

async function readLog(file: string): Promise<LogFile> {
  if (!LOG_EXT.has(extname(file).toLowerCase())) throw new Error('Choose a .log or .txt file.');
  const s = await stat(file).catch(() => null);
  if (!s?.isFile()) throw new Error("That file couldn't be read.");
  // big logs: read the end, where the crash is
  const length = Math.min(s.size, MAX_FILE_BYTES);
  const handle = await open(file, 'r');
  let text: string;
  try {
    const buf = Buffer.alloc(length);
    await handle.read(buf, 0, length, s.size - length);
    text = buf.toString('utf8');
  } finally {
    await handle.close();
  }
  if (s.size > length) text = text.slice(text.indexOf('\n') + 1); // drop the cut first line
  const redact = await redactor();
  return {
    name: redact(basename(file)),
    text: redact(text),
    note: s.size > length ? `The file is ${(s.size / 1024 / 1024).toFixed(1)} MB; only the last 5 MB was loaded.` : null
  };
}

export async function openLogFile(win: BrowserWindow): Promise<LogFile | null> {
  const { canceled, filePaths } = await dialog.showOpenDialog(win, {
    title: 'Open a game log or crash report',
    properties: ['openFile'],
    filters: [{ name: 'Logs and crash reports', extensions: ['log', 'txt'] }]
  });
  if (canceled || !filePaths[0]) return null;
  return readLog(filePaths[0]);
}

export async function readDroppedLog(filePath: unknown): Promise<LogFile> {
  if (typeof filePath !== 'string' || !filePath) throw new Error('Drop a .log or .txt file.');
  return readLog(filePath);
}

export async function readClipboardLog(): Promise<LogFile> {
  let text = await clipboard.readText();
  let note: string | null = null;
  if (text.length > MAX_INPUT_CHARS) {
    text = text.slice(-MAX_INPUT_CHARS);
    note = 'The clipboard text was very long; only the last 5 MB was pasted.';
  }
  return { name: 'clipboard', text: (await redactor())(text), note };
}
