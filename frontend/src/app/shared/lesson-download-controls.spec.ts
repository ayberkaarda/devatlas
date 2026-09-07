import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../testing/fake-platform.service';
import { LocaleService } from '../core/i18n/locale.service';
import { BundledTranslateLoader } from '../core/i18n/translations';
import { PlatformService } from '../core/platform/platform.service';
import { LessonDownloadControls } from './lesson-download-controls';

describe('LessonDownloadControls', () => {
  let fake: FakePlatformService;

  beforeEach(async () => {
    fake = new FakePlatformService();
    fake.capabilities = { canDownload: true, hasLocalStore: true };

    TestBed.configureTestingModule({
      providers: [
        { provide: PlatformService, useValue: fake },
        provideTranslateService({
          loader: BundledTranslateLoader,
          fallbackLang: 'en',
          lang: 'en',
        }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
  });

  function render() {
    const fixture = TestBed.createComponent(LessonDownloadControls);
    fixture.componentRef.setInput('lessonId', 'lesson-1');
    fixture.componentRef.setInput('title', 'Introduction');
    fixture.componentRef.setInput('baseAvailability', 'DOWNLOADED');
    fixture.detectChanges();
    return fixture;
  }

  function wrapper(fixture: ReturnType<typeof render>): HTMLElement {
    const element = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="lesson-download-controls"]',
    );
    if (element === null) {
      throw new Error('Controls wrapper not found.');
    }
    return element as HTMLElement;
  }

  it('renders the delete button for a downloaded lesson at rest', () => {
    const fixture = render();
    const buttons = wrapper(fixture).querySelectorAll('button');

    expect(buttons.length).toBe(1);
    // A row action, quiet rather than outlined: the ghost tier carries no
    // border of its own, distinguishing it from the confirm/cancel pair a
    // click on it opens.
    expect(buttons[0].className).toContain('text-text-muted');
    expect(buttons[0].className).not.toContain('border-border');
  });

  it('renders only the status word for a downloaded lesson when acquireOnly is set', () => {
    const fixture = render();
    fixture.componentRef.setInput('acquireOnly', true);
    fixture.detectChanges();

    const host = wrapper(fixture);
    expect(host.querySelectorAll('button').length).toBe(0);
    expect(host.querySelector('[data-testid="availability-state"]')?.textContent?.trim()).not.toBe(
      '',
    );
  });

  it('replaces the delete button with a confirm/cancel pair once delete is requested', () => {
    const fixture = render();
    const before = wrapper(fixture).querySelectorAll('button');
    before[0].click();
    fixture.detectChanges();

    const after = Array.from(wrapper(fixture).querySelectorAll('button'));
    expect(after.length).toBe(2);

    const confirm = after.find((button) => button.className.includes('bg-danger'));
    const cancel = after.find((button) => button.className.includes('border-border'));
    expect(confirm).not.toBeUndefined();
    expect(cancel).not.toBeUndefined();

    // The original at-rest delete button — with its `download.hint.delete`
    // aria-label — is gone: what remains is only the confirm/cancel pair.
    expect(after.every((button) => button.getAttribute('aria-label') === null)).toBe(true);
  });
});
