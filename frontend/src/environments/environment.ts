/**
 * Web build environment — the default, and what the `web` configuration uses.
 * The `tauri` configuration replaces this file with environment.tauri.ts.
 */
import endpoints from '../../../config/api-endpoints.json';
import type { AppEnvironment } from './environment.model';

export const environment: AppEnvironment = {
  platform: 'web',
  production: true,
  apiBaseUrl: endpoints.production,
};
