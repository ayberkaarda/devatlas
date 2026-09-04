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
  testPathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/dist/', '<rootDir>/.angular/'],
};

export default config;
