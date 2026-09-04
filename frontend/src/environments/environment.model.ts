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
}
