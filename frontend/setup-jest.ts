// Jest test-environment bootstrap.
// The workspace is zoneless (Angular 22 default: no zone.js dependency,
// no provideZoneChangeDetection() call in app.config.ts), so the test
// bed must be initialized with jest-preset-angular's zoneless setup
// rather than the zone.js-based one.
import { setupZonelessTestEnv } from 'jest-preset-angular/setup-env/zoneless';

setupZonelessTestEnv();
