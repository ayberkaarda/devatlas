/**
 * Shape shared by every build target's environment file.
 *
 * This lives in its own module on purpose. The `tauri` build swaps
 * environment.ts for environment.tauri.ts through angular.json
 * fileReplacements, so a type imported from './environment' would resolve to
 * the replacement file itself during that build and the module would import
 * its own declaration. Keeping the contract in a file that is never replaced
 * leaves both targets pointing at the same definition.
 */
export interface AppEnvironment {
  /**
   * Which client this bundle was built for. Feature code should not branch on
   * this directly; it selects the platform service implementation at startup,
   * and components stay unaware of which one they got.
   */
  readonly platform: 'web' | 'tauri';
  readonly production: boolean;
  /**
   * Absolute base for every REST call, including the `/api/v1` prefix and no
   * trailing slash.
   *
   * It is absolute rather than same-origin because neither client is served
   * from the API's origin: the web build is deployed to its own host and the
   * desktop build runs from a webview origin. Both are therefore cross-origin
   * callers and are subject to the API's CORS allow-list.
   */
  readonly apiBaseUrl: string;
}
