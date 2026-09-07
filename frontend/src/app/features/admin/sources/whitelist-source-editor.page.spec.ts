import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakeAdminApiClient } from '../../../../testing/fake-admin-api.client';
import { FakePlatformService } from '../../../../testing/fake-platform.service';
import { AdminApiClient } from '../../../core/admin/admin-api.client';
import type { WhitelistSource } from '../../../core/admin/admin-models';
import { LocaleService } from '../../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../../core/i18n/translations';
import { PlatformService } from '../../../core/platform/platform.service';
import { WhitelistSourceEditorPage } from './whitelist-source-editor.page';

function source(overrides: Partial<WhitelistSource> = {}): WhitelistSource {
  return {
    id: 'source-1',
    name: 'Spring Blog',
    feedUrl: 'https://spring.io/blog.atom',
    verifyUrlPattern: 'https://spring.io/blog/{version}',
    enabled: true,
    lastFetchedAt: null,
    createdAt: '2026-08-01T00:00:00.000Z',
    updatedAt: '2026-08-01T00:00:00.000Z',
    version: 0,
    ...overrides,
  };
}

describe('WhitelistSourceEditorPage', () => {
  let api: FakeAdminApiClient;

  beforeEach(async () => {
    api = new FakeAdminApiClient();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AdminApiClient, useValue: api },
        { provide: PlatformService, useValue: new FakePlatformService() },
        provideTranslateService({ loader: BundledTranslateLoader, fallbackLang: 'en', lang: 'en' }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
  });

  async function renderCreateForm() {
    const fixture = TestBed.createComponent(WhitelistSourceEditorPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function setValue(element: HTMLElement, id: string, value: string): void {
    const input = element.querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submit(element: HTMLElement): void {
    (element.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
  }

  it('rejects a feed URL that is not an absolute https:// address', async () => {
    const fixture = await renderCreateForm();
    const element = fixture.nativeElement as HTMLElement;

    setValue(element, 'source-editor-name', 'Spring Blog');
    setValue(element, 'source-editor-feed-url', 'http://spring.io/blog.atom');
    setValue(element, 'source-editor-verify-url-pattern', 'https://spring.io/blog/{version}');
    submit(element);
    fixture.detectChanges();

    expect(element.textContent).toContain('must be an absolute https:// address');
    expect(api.createSourceCalls.calls.length).toBe(0);
  });

  it('rejects a verify URL pattern with no {version} placeholder', async () => {
    const fixture = await renderCreateForm();
    const element = fixture.nativeElement as HTMLElement;

    setValue(element, 'source-editor-name', 'Spring Blog');
    setValue(element, 'source-editor-feed-url', 'https://spring.io/blog.atom');
    setValue(element, 'source-editor-verify-url-pattern', 'https://spring.io/blog/latest');
    submit(element);
    fixture.detectChanges();

    expect(element.textContent).toContain('exactly one {version} placeholder');
    expect(api.createSourceCalls.calls.length).toBe(0);
  });

  it('rejects a verify URL pattern with two {version} placeholders', async () => {
    const fixture = await renderCreateForm();
    const element = fixture.nativeElement as HTMLElement;

    setValue(element, 'source-editor-name', 'Spring Blog');
    setValue(element, 'source-editor-feed-url', 'https://spring.io/blog.atom');
    setValue(
      element,
      'source-editor-verify-url-pattern',
      'https://spring.io/{version}/blog/{version}',
    );
    submit(element);
    fixture.detectChanges();

    expect(element.textContent).toContain('exactly one {version} placeholder');
    expect(api.createSourceCalls.calls.length).toBe(0);
  });

  it('keeps the save button focused while a save is in flight, and refuses a second one', async () => {
    const fixture = await renderCreateForm();
    const element = fixture.nativeElement as HTMLElement;
    // A create that never settles, so the in-flight state can be inspected.
    const create = jest
      .spyOn(api, 'createSource')
      .mockReturnValue(new Promise<never>(() => undefined));

    setValue(element, 'source-editor-name', 'Spring Blog');
    setValue(element, 'source-editor-feed-url', 'https://spring.io/blog.atom');
    setValue(element, 'source-editor-verify-url-pattern', 'https://spring.io/blog/{version}');

    const save = element.querySelector('[data-testid="source-editor-save"]') as HTMLButtonElement;
    save.focus();
    submit(element);
    fixture.detectChanges();

    expect(save.getAttribute('aria-disabled')).toBe('true');
    // Unavailable, not inert: the focus is still on the button that was
    // pressed rather than back on the document body.
    expect(document.activeElement).toBe(save);

    // `aria-disabled` leaves the form submittable by Enter, so the handler
    // has to refuse the second attempt itself.
    submit(element);
    fixture.detectChanges();

    expect(create).toHaveBeenCalledTimes(1);
    expect(document.activeElement).toBe(save);
  });

  it('accepts a valid form and creates the source', async () => {
    const fixture = await renderCreateForm();
    const element = fixture.nativeElement as HTMLElement;

    setValue(element, 'source-editor-name', 'Spring Blog');
    setValue(element, 'source-editor-feed-url', 'https://spring.io/blog.atom');
    setValue(element, 'source-editor-verify-url-pattern', 'https://spring.io/blog/{version}');
    api.createSourceCalls.mockResolvedValueOnce(source());
    submit(element);
    await fixture.whenStable();

    expect(api.createSourceCalls.lastArgs).toEqual([
      {
        name: 'Spring Blog',
        feedUrl: 'https://spring.io/blog.atom',
        verifyUrlPattern: 'https://spring.io/blog/{version}',
        enabled: true,
      },
    ]);
  });
});
