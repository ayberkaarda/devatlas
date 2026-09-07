/**
 * Local development environment — what `ng serve` uses.
 *
 * The default web environment points at the deployed API, which is the right
 * value for a build that ships. Serving locally needs the opposite: a server
 * running on this machine, on a port that is not the framework default,
 * because another service already holds 8080 here. Without this file the
 * development server would silently talk to production, which is both wrong
 * and hard to notice, since a failed cross-origin request looks the same as a
 * server that is simply down.
 *
 * The platform stays `web`: serving is a browser activity. The desktop shell
 * loads the built `tauri` output instead and has its own replacement.
 */
import endpoints from '../../../config/api-endpoints.json';
import type { AppEnvironment } from './environment.model';

export const environment: AppEnvironment = {
  platform: 'web',
  production: false,
  apiBaseUrl: endpoints.development,
};
