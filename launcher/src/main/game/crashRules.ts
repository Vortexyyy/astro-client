/**
 * Pattern rules that turn a Minecraft log / crash report into a plain-English cause.
 * Pure (no Electron, no fs) so it can be unit-checked on its own. Everything runs locally.
 */

export interface CrashSummary {
  cause: 'memory' | 'java' | 'mods' | 'mixin' | 'loader' | 'gpu' | 'unknown';
  title: string;
  summary: string;
  tips: string[];
  evidence: string[];
}

interface Rule {
  cause: CrashSummary['cause'];
  test: RegExp;
  build(m: RegExpMatchArray, text: string): Omit<CrashSummary, 'cause' | 'evidence'>;
}

const JAVA_FOR_CLASS: Record<number, number> = { 52: 8, 55: 11, 60: 16, 61: 17, 65: 21, 66: 22, 67: 23, 68: 24, 69: 25 };

function javaFor(classVersion: string | undefined): string {
  const major = Math.floor(Number(classVersion));
  const java = JAVA_FOR_CLASS[major];
  return java ? `Java ${java}` : 'a newer Java';
}

const clean = (s: string | undefined) => (s ?? '').replace(/['"`]/g, '').trim();

/** Ordered: the most specific / most common root causes first. */
const RULES: Rule[] = [
  {
    cause: 'memory',
    test: /Could not reserve enough space for (?:\d+\w* )?object heap|Invalid maximum heap size|There is insufficient memory for the Java Runtime/i,
    build: () => ({
      title: 'Java could not get the memory it asked for',
      summary: 'The profile asks for more RAM than Java could reserve, so the game never started.',
      tips: [
        'Lower the memory for this profile (4 GB is plenty for most modpacks).',
        'Close other heavy programs, or switch to the bundled 64-bit Java in Settings.'
      ]
    })
  },
  {
    cause: 'memory',
    test: /java\.lang\.OutOfMemoryError(?::\s*([^\r\n]+))?|GC overhead limit exceeded/i,
    build: (m) => ({
      title: 'The game ran out of memory',
      summary: `Minecraft used up all the RAM it was given${m[1] ? ` (${clean(m[1])})` : ''} and stopped.`,
      tips: ['Give the profile more memory (try 6-8 GB for big modpacks).', 'Lower render distance, or remove very heavy mods and shaders.']
    })
  },
  {
    cause: 'java',
    test: /compiled by a more recent version of the Java Runtime \(class file version ([\d.]+)\)|Unsupported class file major version (\d+)|UnsupportedClassVersionError/i,
    build: (_m, text) => {
      const version = /class file version ([\d.]+)/i.exec(text)?.[1] ?? /Unsupported class file major version (\d+)/i.exec(text)?.[1];
      const needed = version ? javaFor(version) : 'a newer Java';
      return {
        title: 'Wrong Java version',
        summary: `The game or one of its mods needs ${needed}, but an older Java was used.`,
        tips: ['Switch Settings → Java runtime to Bundled, which picks the right Java for each Minecraft version.', `Or point the custom Java path at ${needed}.`]
      };
    }
  },
  {
    cause: 'java',
    test: /(?:requires|needs) (?:at least )?Java (\d+)|Java (\d+) (?:or (?:later|above|newer) )?is required/i,
    build: (m) => ({
      title: 'Wrong Java version',
      summary: `This game version or a mod needs Java ${m[1] ?? m[2]}.`,
      tips: ['Switch Settings → Java runtime to Bundled.', `Or use a custom Java ${m[1] ?? m[2]} executable.`]
    })
  },
  {
    cause: 'mods',
    test: /Mod '?([^'\r\n(]+?)'? \(([\w.-]+)\) [^\r\n]*? requires [^\r\n]*?(?:of|mod) '?([\w .-]+?)'?,? which is missing/i,
    build: (m) => ({
      title: 'A mod is missing a dependency',
      summary: `${clean(m[1])} needs ${clean(m[3])}, which isn't installed.`,
      tips: [`Install ${clean(m[3])} from the Mods page, or remove ${clean(m[1])}.`]
    })
  },
  {
    cause: 'mods',
    test: /requires [^\r\n]*?(?:of|mod) '?([\w .-]+?)'?,? which is missing/i,
    build: (m) => ({
      title: 'A mod is missing a dependency',
      summary: `A mod needs ${clean(m[1])}, which isn't installed.`,
      tips: [`Install ${clean(m[1])} from the Mods page, or remove the mod that needs it.`]
    })
  },
  {
    cause: 'mods',
    test: /Mod '?([^'\r\n(]+?)'? \(([\w.-]+)\) [^\r\n]*? (?:requires|is incompatible with) [^\r\n]*?(?:version [^\r\n]*? of |any version of )'?([\w .-]+?)'?(?:,|$)/im,
    build: (m) => ({
      title: 'Two mods are incompatible',
      summary: `${clean(m[1])} doesn't work with the installed version of ${clean(m[3])}.`,
      tips: [`Update both with "Check for updates" on the Profiles page, or remove ${clean(m[1])}.`]
    })
  },
  {
    cause: 'mods',
    test: /Incompatible mods? (?:set|found)|Mod resolution failed|Duplicate mods? found|found duplicate mod/i,
    build: () => ({
      title: 'Incompatible or duplicate mods',
      summary: 'Fabric refused to start because some mods conflict, are duplicated or need something that is missing.',
      tips: ['Open the log to see which mods Fabric named.', 'Remove duplicates, then run "Check for updates" on the Profiles page.']
    })
  },
  {
    cause: 'mixin',
    test: /Mixin apply(?: for mod ([\w-]+))? failed|Mixin transformation of [\w.$]+ failed|InvalidMixinException|MixinApplyError|Critical injection failure|MixinTransformerError/i,
    build: (m, text) => {
      const mod = m[1] ?? /from mod ([\w-]+)/i.exec(text)?.[1] ?? /mixins? config ([\w.-]+?)\.mixins?\.json/i.exec(text)?.[1];
      return {
        title: 'A mod failed to patch the game (Mixin error)',
        summary: mod
          ? `${mod} tried to change the game's code and failed. It is usually built for a different Minecraft version, or clashes with another mod.`
          : "A mod tried to change the game's code and failed. It is usually built for a different Minecraft version, or clashes with another mod.",
        tips: [mod ? `Update or remove ${mod}.` : 'Update your mods, or remove the most recently added ones.', 'Check that every mod is made for this exact Minecraft version.']
      };
    }
  },
  {
    cause: 'gpu',
    test: /GLFW error (?:65542|65543)|WGL: The driver does not appear to support OpenGL|Pixel format not accelerated|OpenGL (?:3\.2|4\.\d)[^\r\n]*not supported|Failed to create (?:the )?(?:window|OpenGL context)|(?:atio6axx|atioglxx|nvoglv(?:32|64)|ig\d+icd(?:32|64)|igxelpicd64|amdxx64)\.dll/i,
    build: () => ({
      title: 'Graphics driver problem',
      summary: "The game couldn't use your graphics card's OpenGL driver, or the driver crashed.",
      tips: [
        'Update your graphics driver from NVIDIA, AMD or Intel directly (not Windows Update).',
        'On laptops, make sure Java uses the dedicated GPU (Windows Settings → Display → Graphics).',
        'Try removing shader mods to rule them out.'
      ]
    })
  },
  {
    cause: 'loader',
    test: /Could not execute entrypoint stage '(\w+)' due to errors, provided by '([\w-]+)'/i,
    build: (m) => ({
      title: 'A mod crashed while loading',
      summary: `${m[2]} threw an error while the game was starting (${m[1]} stage).`,
      tips: [`Update or remove ${m[2]}.`, 'Check that its dependencies are installed.']
    })
  },
  {
    cause: 'loader',
    test: /net\.fabricmc\.loader\.impl\.FormattedException|FabricLoader[^\r\n]*(?:failed|error)|Failed to (?:load|instantiate) mod/i,
    build: () => ({
      title: 'Fabric Loader error',
      summary: 'Fabric hit an error while loading mods.',
      tips: ['Open the log to see which mod Fabric named.', 'Update your mods, or remove the most recently added ones.']
    })
  },
  {
    cause: 'mods',
    test: /(?:NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|NoSuchFieldError):?\s*([\w/.$]+)?/,
    build: (m) => ({
      title: 'A mod is missing code it needs',
      summary: `A mod referred to game or library code that isn't there${m[1] ? ` (${m[1].replace(/\//g, '.')})` : ''}. Usually a missing dependency or a mod made for another version.`,
      tips: ['Make sure Fabric API and other required libraries are installed.', 'Run "Check for updates" on the Profiles page.']
    })
  },
  {
    cause: 'gpu',
    test: /EXCEPTION_ACCESS_VIOLATION|SIGSEGV/,
    build: () => ({
      title: 'The game crashed in native code',
      summary: 'Java itself crashed, most often because of a graphics driver or an overlay (Discord, RivaTuner, recording tools).',
      tips: ['Update your graphics driver.', 'Turn off overlays and try again.']
    })
  }
];

/** Lines that matched, trimmed for display. */
function evidenceFor(text: string, re: RegExp): string[] {
  const out: string[] = [];
  const flags = re.flags.includes('i') ? 'i' : '';
  const single = new RegExp(re.source, flags);
  for (const line of text.split(/\r?\n/)) {
    if (single.test(line)) out.push(line.trim().slice(0, 300));
    if (out.length >= 3) break;
  }
  return out;
}

export function summarizeCrash(text: string, exitCode: number | null): CrashSummary {
  for (const rule of RULES) {
    const m = text.match(rule.test);
    if (!m) continue;
    const ev = evidenceFor(text, rule.test);
    return { cause: rule.cause, ...rule.build(m, text), evidence: ev.length ? ev : [m[0].slice(0, 300)] };
  }
  const description = /^Description: ([^\r\n]+)/m.exec(text)?.[1];
  return {
    cause: 'unknown',
    title: 'Minecraft closed unexpectedly',
    summary: description
      ? `The crash report says: "${description.trim()}".`
      : `The game exited with code ${exitCode ?? 'unknown'} and no known cause was found in the log.`,
    tips: ['Open the log or crash report for the full details.', 'If it started after adding a mod, remove that mod and try again.'],
    evidence: description ? [`Description: ${description.trim()}`] : []
  };
}

// ── Redaction ────────────────────────────────────────────────────────────

const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

export interface RedactContext {
  home: string;
  username: string;
  /** Minecraft player names */
  players?: string[];
}

/** Hides the home folder, OS username, player names and any tokens in text shown or copied. */
export function makeRedactor(ctx: RedactContext): (text: string) => string {
  const homes = new Set<string>();
  if (ctx.home) {
    homes.add(ctx.home);
    homes.add(ctx.home.replace(/\\/g, '/'));
    homes.add(ctx.home.replace(/\//g, '\\'));
    homes.add(ctx.home.replace(/\\/g, '\\\\')); // JSON-escaped paths in crash reports
  }
  const homeRe = homes.size
    ? new RegExp([...homes].sort((a, b) => b.length - a.length).map(escape).join('|'), 'gi')
    : null;
  const names = [ctx.username, ...(ctx.players ?? [])].filter((n) => n && n.length >= 3);
  const nameRes = names.map((n) => new RegExp(`(?<![\\w])${escape(n)}(?![\\w])`, 'gi'));
  return (text: string) => {
    let out = text
      .replace(/(--(?:accessToken|xuid|clientId|session)\s+)\S+/gi, '$1<redacted>')
      .replace(/("?(?:access_?token|refresh_?token|session_?id)"?\s*[:=]\s*"?)[^"\s,]+/gi, '$1<redacted>')
      .replace(/\beyJ[\w-]{10,}\.[\w-]{10,}\.[\w-]{10,}\b/g, '<redacted>');
    if (homeRe) out = out.replace(homeRe, '~');
    nameRes.forEach((re, i) => {
      out = out.replace(re, i === 0 ? '<user>' : '<player>');
    });
    return out;
  };
}
