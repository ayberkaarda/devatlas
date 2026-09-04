import type { Config } from 'jest';
import jestPresetAngularPresets from 'jest-preset-angular/presets/index.js';

const { createCjsPreset } = jestPresetAngularPresets;
const presetConfig = createCjsPreset();

const config: Config = {
  ...presetConfig,
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  testPathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/dist/', '<rootDir>/.angular/'],
};

export default config;
