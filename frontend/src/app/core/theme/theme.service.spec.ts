import { TestBed } from '@angular/core/testing';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { PlatformService } from '../platform/platform.service';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let platform: FakePlatformService;
  let theme: ThemeService;

  beforeEach(() => {
    platform = new FakePlatformService();
    TestBed.configureTestingModule({
      providers: [{ provide: PlatformService, useValue: platform }],
    });
    theme = TestBed.inject(ThemeService);
    document.documentElement.removeAttribute('data-theme');
  });

  it('puts the resolved theme on the document, not the preference', () => {
    theme.initialize('DARK');
    expect(theme.preference()).toBe('DARK');
    expect(theme.resolved()).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('resolves the deferred preference against the system setting', () => {
    theme.initialize('SYSTEM');
    // The stored preference stays a deferral rather than collapsing into the
    // value it happened to resolve to, so a later system change still applies.
    expect(theme.preference()).toBe('SYSTEM');
    expect(['light', 'dark']).toContain(theme.resolved());
    expect(document.documentElement.getAttribute('data-theme')).toBe(theme.resolved());
  });

  it('persists a choice through the platform service', async () => {
    theme.initialize('LIGHT');
    await theme.set('DARK');

    expect(platform.preferenceWrites).toEqual([{ theme: 'DARK' }]);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('applies the change before it is stored, so a storage failure cannot undo it', async () => {
    theme.initialize('LIGHT');
    let attributeWhenWritten: string | null = null;
    platform.setPreferences = async () => {
      attributeWhenWritten = document.documentElement.getAttribute('data-theme');
      throw new Error('storage unavailable');
    };

    await expect(theme.set('DARK')).rejects.toThrow('storage unavailable');
    expect(attributeWhenWritten).toBe('dark');
    expect(theme.resolved()).toBe('dark');
  });

  it('toggles between the two explicit values, starting from what is on screen', async () => {
    theme.initialize('SYSTEM');
    const before = theme.resolved();

    await theme.toggle();
    expect(theme.resolved()).toBe(before === 'dark' ? 'light' : 'dark');
    // A two-state control never leaves the preference on the deferral, or one
    // press in three would appear to do nothing.
    expect(theme.preference()).toBe(before === 'dark' ? 'LIGHT' : 'DARK');

    await theme.toggle();
    expect(theme.resolved()).toBe(before);
  });
});
