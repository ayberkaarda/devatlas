import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../testing/fake-platform.service';
import { LocaleService } from '../core/i18n/locale.service';
import { BundledTranslateLoader } from '../core/i18n/translations';
import { PlatformService } from '../core/platform/platform.service';
import { Pagination } from './pagination';

describe('Pagination', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
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

  function render(page: number, totalPages: number) {
    const fixture = TestBed.createComponent(Pagination);
    fixture.componentRef.setInput('page', page);
    fixture.componentRef.setInput('totalPages', totalPages);
    const emitted: number[] = [];
    fixture.componentInstance.pageChange.subscribe((value) => emitted.push(value));
    fixture.detectChanges();
    return { fixture, emitted };
  }

  function button(
    fixture: ReturnType<typeof render>['fixture'],
    which: 'previous' | 'next',
  ): HTMLButtonElement {
    const found = (fixture.nativeElement as HTMLElement).querySelector(
      `[data-testid="pagination-${which}"]`,
    );
    if (found === null) {
      throw new Error(`No ${which} button rendered.`);
    }
    return found as HTMLButtonElement;
  }

  it('pages in both directions from the middle of a list', () => {
    const { fixture, emitted } = render(2, 5);

    button(fixture, 'previous').click();
    button(fixture, 'next').click();

    expect(emitted).toEqual([1, 3]);
  });

  it('reports both bounds as available while there is a page on either side', () => {
    const { fixture } = render(2, 5);

    expect(button(fixture, 'previous').getAttribute('aria-disabled')).toBeNull();
    expect(button(fixture, 'next').getAttribute('aria-disabled')).toBeNull();
  });

  it('refuses to page before the first page, and keeps the focus on the button that refused', () => {
    const { fixture, emitted } = render(0, 5);
    const previous = button(fixture, 'previous');

    expect(previous.getAttribute('aria-disabled')).toBe('true');

    previous.focus();
    previous.click();
    fixture.detectChanges();

    // The bound is enforced by the handler, not by the browser: the control is
    // deliberately not inert, so the click does reach it.
    expect(emitted).toEqual([]);
    // And because it is not inert it never handed the focus to the document
    // body, which is what going `disabled` under someone's finger would do.
    expect(document.activeElement).toBe(previous);
  });

  it('refuses to page past the last page, and keeps the focus on the button that refused', () => {
    const { fixture, emitted } = render(4, 5);
    const next = button(fixture, 'next');

    expect(next.getAttribute('aria-disabled')).toBe('true');

    next.focus();
    next.click();
    fixture.detectChanges();

    expect(emitted).toEqual([]);
    expect(document.activeElement).toBe(next);
  });

  it('treats a single-page list as being at both bounds at once', () => {
    const { fixture, emitted } = render(0, 1);

    expect(button(fixture, 'previous').getAttribute('aria-disabled')).toBe('true');
    expect(button(fixture, 'next').getAttribute('aria-disabled')).toBe('true');

    button(fixture, 'previous').click();
    button(fixture, 'next').click();

    expect(emitted).toEqual([]);
  });

  it('keeps both controls in the tab order while they are unavailable', () => {
    // The whole point of `aria-disabled` over the `disabled` property here:
    // a reader tabbing through the list still reaches these and hears that
    // there is nowhere further to go, rather than finding they have silently
    // vanished from the tab order.
    const { fixture } = render(0, 1);

    for (const which of ['previous', 'next'] as const) {
      const control = button(fixture, which);
      expect(control.disabled).toBe(false);
      expect(control.hasAttribute('tabindex')).toBe(false);
    }
  });
});
