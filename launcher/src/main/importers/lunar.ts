import { readJson } from './inspect';

/**
 * Lunar's per-profile settings (settings/game/<profile>/mods.json) for the HUD mods Astro also has.
 * Only explicit values are carried over; anything Lunar leaves at its default keeps Astro's default.
 */
interface LunarMods {
  FPS?: { enabled?: boolean; options?: { background?: boolean } };
  PING?: { enabled?: boolean; PING_HUD?: { options?: { background?: boolean } } };
  COORDINATES?: { enabled?: boolean; options?: { background?: boolean } };
  KEYSTROKES?: { enabled?: boolean };
  ZOOM?: { enabled?: boolean };
}

interface AstroModuleConfig {
  enabled?: boolean;
  settings?: Record<string, unknown>;
}

export async function lunarModuleSettings(file: string): Promise<{ config: Record<string, AstroModuleConfig>; summary: string[] }> {
  const lunar = await readJson<LunarMods>(file);
  const config: Record<string, AstroModuleConfig> = {};
  const summary: string[] = [];
  if (!lunar) return { config, summary };

  const map = (astroName: string, enabled: boolean | undefined, background?: boolean) => {
    if (enabled === undefined && background === undefined) return;
    const entry: AstroModuleConfig = {};
    if (enabled !== undefined) entry.enabled = enabled;
    if (background !== undefined) entry.settings = { Background: background };
    config[astroName] = entry;
    summary.push(`${astroName} ${enabled === false ? 'off' : 'on'}`);
  };

  map('FPS', lunar.FPS?.enabled, lunar.FPS?.options?.background);
  map('Ping', lunar.PING?.enabled, lunar.PING?.PING_HUD?.options?.background);
  map('Coordinates', lunar.COORDINATES?.enabled, lunar.COORDINATES?.options?.background);
  map('Keystrokes', lunar.KEYSTROKES?.enabled);
  map('Zoom', lunar.ZOOM?.enabled);
  return { config, summary };
}
