import type { Config } from 'jest';
import jestPresetAngularPresets from 'jest-preset-angular/presets/index.js';

const { createCjsPreset } = jestPresetAngularPresets;
const presetConfig = createCjsPreset();

const config: Config = {
  ...presetConfig,
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  moduleNameMapper: {
    /*
     * The desktop bridge is published as an ES module with no runtime outside a
     * desktop window, so it cannot be loaded here. Resolving it to a double
     * lets the real desktop implementation be tested unchanged — the mapping
     * and error translation it performs are exactly what needs covering, and
     * they are the parts a hand-written stand-in for the whole service would
     * skip.
     */
    '^@tauri-apps/api/(core|event)$': '<rootDir>/src/testing/desktop-ipc-double.ts',
    /*
     * The markdown renderer publishes only an ES module and a UMD bundle. The
     * bundler picks the module; this runner cannot load it, so it is pointed at
     * the UMD build of the same version instead. The library under test is the
     * same code either way.
     */
    '^marked$': '<rootDir>/node_modules/marked/lib/marked.umd.js',
  },
  /*
   * The tree layout library ships as an ES module only, with no CommonJS build
   * to fall back to and no UMD bundle to point at, so it has to be transformed
   * rather than mapped elsewhere.
   *
   * This list EXTENDS the preset's rather than replacing it. The preset already
   * transforms `.mjs` files and Angular's locale data, and dropping those makes
   * every suite fail on Angular's own ES modules — the failure appears in files
   * that have nothing to do with the library being added, which makes it easy
   * to misread.
   */
  transformIgnorePatterns: [
    'node_modules/(?!(.*\\.mjs$|@angular/common/locales/.*\\.js$|d3-hierarchy/.*\\.js$))',
  ],
  testPathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/dist/', '<rootDir>/.angular/'],
};

export default config;
