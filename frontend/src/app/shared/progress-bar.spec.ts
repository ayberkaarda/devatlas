import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../testing/fake-platform.service';
import { LocaleService } from '../core/i18n/locale.service';
import { BundledTranslateLoader } from '../core/i18n/translations';
import { PlatformService } from '../core/platform/platform.service';
import { ProgressBar } from './progress-bar';

describe('ProgressBar', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        // The bar itself needs no platform; the locale service that feeds its
        // translated accessible name does.
        { provide: PlatformService, useValue: new FakePlatformService() },
        provideTranslateService({
          loader: BundledTranslateLoader,
          fallbackLang: 'en',
          lang: 'en',
        }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
  });

  function render(done: number, total: number, labelKey?: string) {
    const fixture = TestBed.createComponent(ProgressBar);
    fixture.componentRef.setInput('done', done);
    fixture.componentRef.setInput('total', total);
    if (labelKey !== undefined) {
      fixture.componentRef.setInput('labelKey', labelKey);
    }
    fixture.detectChanges();
    const bar = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="progress-bar"]',
    );
    if (bar === null) {
      throw new Error('Progress bar not found.');
    }
    return { fixture, bar: bar as HTMLElement };
  }

  it('reports a normal transfer as a percentage against a nought-to-hundred scale', () => {
    const { bar } = render(20480, 41233);

    expect(bar.getAttribute('role')).toBe('progressbar');
    expect(bar.getAttribute('aria-valuemin')).toBe('0');
    expect(bar.getAttribute('aria-valuemax')).toBe('100');
    expect(bar.getAttribute('aria-valuenow')).toBe('50');
    expect(bar.getAttribute('aria-valuetext')).toBe('50%');
  });

  it('carries a translated accessible name, not a raw key', () => {
    const { bar } = render(1, 2);
    expect(bar.getAttribute('aria-label')).toBe('Download progress');
  });

  it('uses the accessible name the caller asks for', () => {
    const { bar } = render(1, 2, 'download.batchProgressLabel');
    expect(bar.getAttribute('aria-label')).toBe('Total download progress');
  });

  it('is indeterminate and empty while the total is still unknown', () => {
    const { bar } = render(0, 0);

    // No value at all rather than a number derived from a zero total: a queued
    // entity has no size yet, and both 0% and 100% would be inventions.
    expect(bar.getAttribute('aria-valuenow')).toBeNull();
    expect(bar.getAttribute('aria-valuetext')).toBeNull();
    expect(bar.getAttribute('role')).toBe('progressbar');

    const fill = bar.firstElementChild as HTMLElement;
    expect(fill.style.transform).toBe('scaleX(0)');
  });

  it('fills completely at the end of a transfer', () => {
    const { bar } = render(41233, 41233);
    expect(bar.getAttribute('aria-valuenow')).toBe('100');
    expect((bar.firstElementChild as HTMLElement).style.transform).toBe('scaleX(1)');
  });

  it('never overfills when a resumed transfer reports more bytes than its total', () => {
    const { bar } = render(500, 400);
    expect(bar.getAttribute('aria-valuenow')).toBe('100');
  });
});
