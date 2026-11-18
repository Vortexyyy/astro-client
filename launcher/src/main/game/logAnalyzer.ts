/**
 * Log helper: turns pasted game / launcher logs or a crash report into findings.
 * Pure (text in, analysis out; no Electron, no fs) so it can be unit-checked on its own.
 * The caller passes the redactor; nothing here stores or sends the text anywhere.
 */
import type { LogAnalysis, LogErrorLine, LogFinding, LogLineKind } from '@shared/types';
import { javaFor, matchAllRules } from './crashRules';

/** Inputs above this are cut (the end of a log is where the crash usually is). */
export const MAX_INPUT_CHARS = 5 * 1024 * 1024;
const HEAD_LINES = 5_000;
const TAIL_LINES = 25_000;
const MAX_LINE_CHARS = 2_000;
const MAX_ERRORS = 300;
const MAX_MODS = 12;

const LINE_RULES: Array<[LogLineKind, RegExp]> = [
  ['caused-by', /^\s*Caused by: /],
  ['thread', /Exception in thread "/],
  ['description', /^Description: \S/],
  ['fatal', /\[[^\]\r\n]*\/FATAL\]|^\s*(?:\[[^\]]*\]\s*)*\[?FATAL\]?[:\s]|^#\s+A fatal error has been detected/],
  ['error', /\[[^\]\r\n]*\/ERROR\]|^\s*(?:\[[^\]]*\]\s*)*\[?ERROR\]?[:\s]|GLFW error \d+/],
  ['exception', /^\s*(?:[a-zA-Z_$][\w$]*\.)+[A-Z][\w$]*(?:Exception|Error|Throwable)\b(?::|\s*$)/]
];

const LOG_SHAPE =
  /\[\d{1,2}:\d{2}:\d{2}(?:\.\d+)?\]|^\s+at [\w$.<>/]+\(|---- Minecraft Crash Report ----|\[[\w ./-]+\/(?:INFO|WARN|ERROR|FATAL|DEBUG)\]|Exception|Fabric ?Loader|minecraft|\bjava\b|LWJGL|\bforge\b/im;

const NOT_A_MOD = new Set(['minecraft', 'java', 'fabricloader', 'fabric-loader', 'mixin', 'mixins']);

function classify(line: string): LogLineKind | null {
  for (const [kind, re] of LINE_RULES) if (re.test(line)) return kind;
  return null;
}

function cutLine(line: string): string {
  return line.length > MAX_LINE_CHARS ? `${line.slice(0, MAX_LINE_CHARS)} …` : line;
}

/** Keeps the start and the end of very long logs, remembering the original line numbers. */
function keepLines(raw: string[]): { kept: Array<{ n: number; text: string }>; truncated: string | null } {
  const toKept = (text: string, i: number) => ({ n: i + 1, text: cutLine(text) });
  if (raw.length <= HEAD_LINES + TAIL_LINES) return { kept: raw.map(toKept), truncated: null };
  const tailStart = raw.length - TAIL_LINES;
  const kept = [...raw.slice(0, HEAD_LINES).map(toKept), ...raw.slice(tailStart).map((t, i) => toKept(t, tailStart + i))];
  const skipped = tailStart - HEAD_LINES;
  return {
    kept,
    truncated: `This log has ${raw.length.toLocaleString('en-US')} lines, so lines ${(HEAD_LINES + 1).toLocaleString('en-US')}–${tailStart.toLocaleString('en-US')} (${skipped.toLocaleString('en-US')} lines) were skipped. The start and the last ${TAIL_LINES.toLocaleString('en-US')} lines were checked.`
  };
}

function uniquePush(list: string[], value: string | undefined, max: number): void {
  const v = (value ?? '').trim();
  if (!v || list.length >= max) return;
  if (NOT_A_MOD.has(v.toLowerCase())) return;
  if (!list.some((x) => x.toLowerCase() === v.toLowerCase())) list.push(v);
}

function extractMods(text: string): string[] {
  const mods: string[] = [];
  const add = (re: RegExp, pick: (m: RegExpExecArray) => string | undefined) => {
    for (const m of text.matchAll(re)) uniquePush(mods, pick(m as RegExpExecArray), MAX_MODS);
  };
  add(/Mod '([^'\r\n]{1,80})' \(([\w.-]{1,64})\)/g, (m) => `${m[1]} (${m[2]})`);
  add(/Mixin apply for mod ([\w-]{1,64}) failed/g, (m) => m[1]);
  add(/\bfrom mod ([\w-]{1,64})/g, (m) => m[1]);
  add(/provided by '([\w-]{1,64})'/g, (m) => m[1]);
  // mod ids named inside the "Caused by:" chain, e.g. [sodium.mixins.json:...]
  for (const line of text.split('\n')) {
    if (!/^\s*Caused by: /.test(line)) continue;
    for (const m of line.matchAll(/\b([a-z][\w-]{1,63})\.mixins?\.json/g)) uniquePush(mods, m[1], MAX_MODS);
  }
  add(/mixins? config ([\w-]{1,64})(?:\.[\w-]+)*\.mixins?\.json/g, (m) => m[1]);
  // an "Mod 'Name' (id)" entry already names the id; drop a bare duplicate of it
  return mods.filter((m) => !mods.some((o) => o !== m && o.toLowerCase().endsWith(`(${m.toLowerCase()})`)));
}

function extractJava(text: string): { javaVersion: string | null; requiredJava: string | null } {
  const reported =
    /Java Version: ([\d][\d._]*)/i.exec(text)?.[1] ??
    /java\.version[=:\s]+"?([\d][\d._]*)/i.exec(text)?.[1] ??
    /(?:Running|Launching)[^\r\n]*? (?:with|on) Java ([\d][\d._]*)/i.exec(text)?.[1];
  const recognisesUpTo = /only recognizes class file versions up to ([\d.]+)/i.exec(text)?.[1];
  const javaVersion = reported ? `Java ${reported}` : recognisesUpTo ? javaFor(recognisesUpTo) : null;
  const classVersion = /class file version ([\d.]+)/i.exec(text)?.[1] ?? /Unsupported class file major version (\d+)/i.exec(text)?.[1];
  const needs = /(?:requires|needs) (?:at least )?Java (\d+)|Java (\d+) (?:or (?:later|above|newer) )?is required/i.exec(text);
  const requiredJava = classVersion ? javaFor(classVersion) : needs ? `Java ${needs[1] ?? needs[2]}` : null;
  return { javaVersion, requiredJava: requiredJava === 'a newer Java' ? null : requiredJava };
}

function extractGpu(text: string): string | null {
  const dll = /\b(atio6axx|atioglxx|nvoglv(?:32|64)|ig\d+icd(?:32|64)|igxelpicd64|amdxx64)\.dll/i.exec(text)?.[1];
  if (dll) return `${dll}.dll (graphics driver)`;
  const glfw = /GLFW error (\d+): ([^\r\n]{1,160})/i.exec(text);
  if (glfw) return `GLFW error ${glfw[1]}: ${glfw[2].trim()}`;
  const card = /Graphics card #\d+ name: ([^\r\n]{1,120})/i.exec(text)?.[1] ?? /GL_RENDERER[=:\s]+([^\r\n]{1,120})/i.exec(text)?.[1];
  if (card) return card.trim();
  const glInfo = /^\s*(?:GL info|Backend API): ([^\r\n]{1,160})/im.exec(text)?.[1];
  return glInfo ? glInfo.trim() : null;
}

/** First frames under the root cause (the last "Caused by"), else under the first exception. */
function topFrames(lines: Array<{ n: number; text: string }>, errors: LogErrorLine[]): string[] {
  const anchor =
    [...errors].reverse().find((e) => e.kind === 'caused-by') ?? errors.find((e) => e.kind === 'exception' || e.kind === 'thread');
  if (!anchor) return [];
  const start = lines.findIndex((l) => l.n === anchor.line);
  const out: string[] = [];
  for (let i = start + 1; i < lines.length && out.length < 5; i++) {
    const t = lines[i].text.trim();
    if (!t.startsWith('at ')) break;
    out.push(t.slice(0, 240));
  }
  return out;
}

function fallbackFinding(errors: LogErrorLine[]): LogFinding | null {
  const description = errors.find((e) => e.kind === 'description');
  const root = [...errors].reverse().find((e) => e.kind === 'caused-by') ?? errors.find((e) => e.kind === 'exception' || e.kind === 'thread');
  const first = description ?? root ?? errors.find((e) => e.kind === 'fatal') ?? errors[0];
  if (!first) return null;
  const quote = first.text.replace(/^Description: /, '').replace(/^\s*Caused by: /, '').trim().slice(0, 220);
  return {
    cause: 'unknown',
    title: description ? 'Minecraft crashed' : 'The log contains errors',
    summary: `No known cause matched. The ${description ? 'crash report says' : root ? 'deepest error is' : 'first error is'}: "${quote}".`,
    tips: [
      'If it started after adding or updating a mod, remove that mod and try again.',
      'Search the error line together with your Minecraft version, or share the summary with the mod author.'
    ],
    evidence: [first.text.slice(0, 300)],
    confidence: 'low'
  };
}

function buildCopyText(a: Omit<LogAnalysis, 'copyText'>): string {
  const head = a.findings[0];
  const out = ['Astro Launcher log summary'];
  if (head) {
    out.push(`Problem: ${head.title} (${head.confidence} confidence)`, `Cause: ${head.summary}`);
    if (head.tips.length) out.push('Fix:', ...head.tips.map((t) => `- ${t}`));
    for (const f of a.findings.slice(1)) out.push(`Also found: ${f.title}`);
  } else {
    out.push(a.looksLikeLog ? 'No known problem found in this log.' : "This didn't look like a Minecraft log.");
  }
  const inv = a.involved;
  const involved = [
    inv.mods.length ? `Mods: ${inv.mods.join(', ')}` : '',
    inv.javaVersion ? `Java used: ${inv.javaVersion}` : '',
    inv.requiredJava ? `Java needed: ${inv.requiredJava}` : '',
    inv.gpu ? `Graphics: ${inv.gpu}` : ''
  ].filter(Boolean);
  if (involved.length) out.push('', ...involved);
  if (a.errors.length) {
    out.push('', 'Key error lines:');
    for (const e of a.errors.slice(0, 8)) out.push(`L${e.line}: ${e.text.trim().slice(0, 240)}`);
  }
  if (a.topFrames.length) out.push('', 'Top of the stack trace:', ...a.topFrames.map((f) => `  ${f}`));
  if (a.truncated) out.push('', `Note: ${a.truncated}`);
  out.push('', 'Worked out locally by Astro Launcher. Paths and tokens are hidden.');
  return out.join('\n');
}

/** text -> findings. `redact` is applied to the text before anything is read out of it. */
export function analyzeLog(input: unknown, redact: (text: string) => string = (t) => t): LogAnalysis {
  let text = typeof input === 'string' ? input : '';
  let cutChars = false;
  if (text.length > MAX_INPUT_CHARS) {
    text = text.slice(-MAX_INPUT_CHARS);
    cutChars = true;
  }
  // control characters (other than tab) are noise from binary pastes; drop them so nothing odd renders
  text = text.replace(/\r\n?/g, '\n').replace(/[\u0000-\u0008\u000b-\u001f\u007f]/g, '');

  const raw = text.split('\n');
  while (raw.length && !raw[raw.length - 1].trim()) raw.pop();
  const { kept, truncated: lineNote } = keepLines(raw);
  const redactedLines = redact(kept.map((l) => l.text).join('\n')).split('\n');
  const lines = kept.map((l, i) => ({ n: l.n, text: redactedLines[i] ?? '' }));
  const body = lines.map((l) => l.text).join('\n');

  const truncated =
    [cutChars ? 'Only the last 5 MB of the text was checked.' : '', lineNote ?? ''].filter(Boolean).join(' ') || null;

  if (!body.trim()) {
    const empty = { empty: true, looksLikeLog: false, findings: [], errors: [], involved: { mods: [], javaVersion: null, requiredJava: null, gpu: null }, topFrames: [], lines: [], totalLines: 0, truncated: null };
    return { ...empty, copyText: '' };
  }

  const errors: LogErrorLine[] = [];
  const seen = new Set<string>();
  for (const l of lines) {
    const kind = classify(l.text);
    if (!kind) continue;
    const key = l.text.trim();
    if (seen.has(key)) continue;
    seen.add(key);
    errors.push({ line: l.n, kind, text: l.text });
    if (errors.length >= MAX_ERRORS) break;
  }

  const java = extractJava(body);
  const findings: LogFinding[] = matchAllRules(body).map((m, i) => {
    let summary = m.summary;
    if (m.cause === 'java' && java.javaVersion && !summary.includes(java.javaVersion)) summary += ` This log shows ${java.javaVersion}.`;
    return {
      cause: m.cause,
      title: m.title,
      summary,
      tips: m.tips,
      evidence: m.evidence,
      confidence: m.generic ? (i === 0 ? 'medium' : 'low') : i === 0 ? 'high' : 'medium'
    };
  });

  // rule evidence that the line patterns didn't catch still belongs in the error list
  const evidence = new Set(findings.flatMap((f) => f.evidence));
  if (evidence.size) {
    for (const l of lines) {
      const t = l.text.trim().slice(0, 300);
      if (evidence.has(t) && !t.startsWith('at ') && !seen.has(l.text.trim()) && errors.length < MAX_ERRORS) {
        seen.add(l.text.trim());
        errors.push({ line: l.n, kind: 'error', text: l.text });
      }
    }
    errors.sort((a, b) => a.line - b.line);
  }

  if (!findings.length) {
    const fb = fallbackFinding(errors);
    if (fb) findings.push(fb);
  }

  const partial: Omit<LogAnalysis, 'copyText'> = {
    empty: false,
    looksLikeLog: LOG_SHAPE.test(body),
    findings,
    errors,
    involved: { mods: extractMods(body), ...java, gpu: extractGpu(body) },
    topFrames: topFrames(lines, errors),
    lines,
    totalLines: raw.length,
    truncated
  };
  return { ...partial, copyText: buildCopyText(partial) };
}
