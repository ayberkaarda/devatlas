/**
 * Web build environment — the default, and what the `web` configuration uses.
 * The `tauri` configuration replaces this file with environment.tauri.ts.
 */
import type { AppEnvironment } from './environment.model';

export const environment: AppEnvironment = {
  platform: 'web',
  production: true,
};
