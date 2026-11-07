import { app } from 'electron';
import { join } from 'node:path';

/**
 * <userData>/
 *   settings.json
 *   minecraft/                 shared assets, libraries, versions (incl. Fabric profiles)
 *   runtimes/<component>/      Java runtimes downloaded from Mojang (e.g. java-runtime-delta = Java 21)
 *   profiles/<id>/profile.json
 *   profiles/<id>/instance/    per-profile game dir: mods, config, saves
 */
export const paths = {
  get root() {
    return app.getPath('userData');
  },
  get settingsFile() {
    return join(this.root, 'settings.json');
  },
  get gameRoot() {
    return join(this.root, 'minecraft');
  },
  get runtimesDir() {
    return join(this.root, 'runtimes');
  },
  get profilesDir() {
    return join(this.root, 'profiles');
  },
  profileDir(id: string) {
    return join(this.profilesDir, id);
  },
  profileFile(id: string) {
    return join(this.profileDir(id), 'profile.json');
  },
  instanceDir(id: string) {
    return join(this.profileDir(id), 'instance');
  },
  modsDir(id: string) {
    return join(this.instanceDir(id), 'mods');
  }
};
