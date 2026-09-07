import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import { PlatformService } from '../../core/platform/platform.service';
import { BlogOffline } from './blog-offline';

@Component({
  imports: [BlogOffline],
  template: `<app-blog-offline (retry)="retries = retries + 1" />`,
})
class Host {
  retries = 0;
}

async function renderWith(hasLocalStore: boolean) {
  const platform = new FakePlatformService();
  platform.capabilities = { canDownload: hasLocalStore, hasLocalStore };

  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: PlatformService, useValue: platform },
      provideTranslateService({ loader: BundledTranslateLoader, fallbackLang: 'en', lang: 'en' }),
    ],
  });
  await TestBed.inject(LocaleService).initialize('en');

  const fixture = TestBed.createComponent(Host);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

describe('BlogOffline', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('says only what is true on a client that stores nothing', async () => {
    const element = (await renderWith(false)).nativeElement as HTMLElement;

    // One explanation and no reassurance about downloaded content: there is
    // none to be reassuring about, and a false comfort is worse than silence.
    expect(element.querySelectorAll('[data-testid="blog-offline"] p').length).toBe(1);
  });

  it('adds the reassurance where a local store makes it true', async () => {
    const element = (await renderWith(true)).nativeElement as HTMLElement;

    expect(element.querySelectorAll('[data-testid="blog-offline"] p').length).toBe(2);
  });

  it('asks its host to try again rather than retrying on its own', async () => {
    const fixture = await renderWith(false);
    const element = fixture.nativeElement as HTMLElement;

    (element.querySelector('[data-testid="blog-offline-retry"]') as HTMLButtonElement).click();

    expect(fixture.componentInstance.retries).toBe(1);
  });
});
