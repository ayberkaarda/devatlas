/**
 * Desktop build environment for a local `tauri dev` run — what the
 * `tauri-development` build configuration swaps in for environment.ts.
 *
 * The desktop shell's production environment (environment.tauri.ts) points
 * at the deployed API, which is wrong for a dev-loop run: there is no
 * deployed server involved, only one running on this machine on a
 * non-default port because the framework default is already held by another
 * service here. Without this file, `tauri dev` would either fail to resolve
 * the production host or silently point at the wrong local port.
 *
 * The platform stays `tauri`: this is still the desktop shell, just pointed
 * at a local API instead of the deployed one.
 */
import endpoints from '../../../config/api-endpoints.json';
import type { AppEnvironment } from './environment.model';

export const environment: AppEnvironment = {
  platform: 'tauri',
  production: false,
  apiBaseUrl: endpoints.development,
};
