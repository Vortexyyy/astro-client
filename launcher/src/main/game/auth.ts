import { app } from 'electron';
import { Authenticator, type IUser } from 'minecraft-launcher-core';
import { Auth, type Minecraft } from 'msmc';
import type { AccountInfo, PublicSettings } from '@shared/types';
import { getActiveAccount, getOfflineName, upsertAccount } from '../settings';

/**
 * Microsoft app used for sign-in. Unset, msmc's default public client is used, which is fine for
 * development. Before distributing Astro, register your own Azure app, request Minecraft API access
 * from Mojang (https://aka.ms/mce-reviewappid) and set ASTRO_MS_CLIENT_ID at build/run time.
 */
const CLIENT_ID = process.env['ASTRO_MS_CLIENT_ID'];

function newAuth(prompt: 'select_account' | 'none'): Auth {
  return CLIENT_ID ? new Auth({ client_id: CLIENT_ID, redirect: 'http://localhost', prompt }) : new Auth(prompt);
}

function toAccountInfo(mc: Minecraft): AccountInfo {
  const profile = mc.profile;
  if (!profile) throw new Error('This Microsoft account does not own Minecraft: Java Edition.');
  const skin = profile.skins?.find((s) => s.state === 'ACTIVE') ?? profile.skins?.[0];
  return {
    uuid: profile.id,
    name: profile.name,
    // Mojang serves skins over http; the same URL works over https.
    skinUrl: skin?.url.replace(/^http:/, 'https:')
  };
}

/**
 * Opens Microsoft's own sign-in page in an Electron window (msmc). The password is typed into
 * Microsoft's page, never into the launcher; only the resulting refresh token is stored (encrypted).
 */
export async function addMicrosoftAccount(): Promise<PublicSettings> {
  const xbox = await newAuth('select_account').launch('electron', {
    width: 500,
    height: 650,
    resizable: false,
    title: 'Sign in with Microsoft',
    backgroundColor: '#07060f'
  });
  const mc = await xbox.getMinecraft();
  return upsertAccount(toAccountInfo(mc), xbox.save());
}

export interface LaunchIdentity {
  user: IUser;
  label: string;
}

/** Credentials for one game launch: a freshly refreshed session for the active account. */
export async function getLaunchIdentity(): Promise<LaunchIdentity> {
  const active = await getActiveAccount();
  if (active) {
    let mc: Minecraft;
    let refreshToken: string;
    try {
      const xbox = await newAuth('none').refresh(active.refreshToken);
      mc = await xbox.getMinecraft();
      refreshToken = xbox.save();
    } catch {
      throw new Error(`The session for ${active.info.name} expired. Remove the account and sign in again.`);
    }
    const info = toAccountInfo(mc);
    await upsertAccount(info, refreshToken); // keeps name/skin current and rotates the token
    return { user: mc.mclc() as unknown as IUser, label: `${info.name} (Microsoft)` };
  }
  if (!app.isPackaged) {
    const name = await getOfflineName();
    return { user: await Authenticator.getAuth(name), label: `${name} (offline, dev build only)` };
  }
  throw new Error('Sign in with a Microsoft account that owns Minecraft: Java Edition to play.');
}
