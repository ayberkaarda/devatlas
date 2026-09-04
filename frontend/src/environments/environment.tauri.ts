/**
 * Desktop build environment — swapped in for environment.ts by the `tauri`
 * build configuration through angular.json fileReplacements.
 */
import type { AppEnvironment } from './environment.model';

export const environment: AppEnvironment = {
  platform: 'tauri',
  production: true,
  apiBaseUrl: 'https://api.devatlas.dev/api/v1',
};
