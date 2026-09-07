import { ComponentRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';

import { BundledTranslateLoader } from '../core/i18n/translations';
import { StateGlyph, type StateGlyphKind } from './state-glyph';

describe('StateGlyph', () => {
  let translate: TranslateService;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideTranslateService({
          loader: BundledTranslateLoader,
          fallbackLang: 'en',
          lang: 'en',
        }),
      ],
    });
    translate = TestBed.inject(TranslateService);
    await firstValueFrom(translate.use('en'));
  });

  async function render(kind: StateGlyphKind) {
    const fixture = TestBed.createComponent(StateGlyph);
    (fixture.componentRef as ComponentRef<StateGlyph>).setInput('kind', kind);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('draws the completed state', async () => {
    const element = (await render('completed')).nativeElement as HTMLElement;
    expect(element.querySelector('[data-testid="state-glyph-completed"]')).not.toBeNull();
  });

  it('says what it means in words as well as in colour', async () => {
    const element = (await render('completed')).nativeElement as HTMLElement;

    // The drawing carries no meaning for a screen reader and none for a
    // reader who cannot separate the green from the surface; the word does.
    const label = element.querySelector('.sr-only');
    expect(label?.textContent?.trim()).toBe(translate.instant('progress.completed'));
    expect(label?.textContent?.trim()).not.toBe('progress.completed');
  });

  it('hides the drawing from assistive technology so the state is announced once', async () => {
    const element = (await render('completed')).nativeElement as HTMLElement;

    const svg = element.querySelector('[data-testid="state-glyph-completed"]')!;
    expect(svg.getAttribute('aria-hidden')).toBe('true');
    // Announced through the sibling text instead, so nothing here needs a
    // name of its own and nothing gets announced twice.
    expect(svg.getAttribute('aria-label')).toBeNull();
    expect(svg.getAttribute('role')).toBeNull();
  });

  it('takes its colour from the surrounding text colour rather than a literal', async () => {
    const element = (await render('completed')).nativeElement as HTMLElement;

    // Painted in currentColor and coloured by a token class, which is what
    // lets one drawing follow both themes without a second definition.
    const svg = element.querySelector<SVGElement>('[data-testid="state-glyph-completed"]')!;
    expect(svg.getAttribute('stroke')).toBe('currentColor');
    expect(svg.getAttribute('class')).toContain('text-success');
  });

  it('renders in the active interface language', async () => {
    const fixture = await render('completed');
    await firstValueFrom(translate.use('de'));
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.sr-only')?.textContent?.trim()).toBe(
      translate.instant('progress.completed'),
    );
  });

  /**
   * The availability states added alongside `completed`, each checked for
   * the three things that matter for a state mark: it draws (a testid),
   * it names itself in words (the sr-only span, not the translation key
   * verbatim), and its colour class matches the family the rest of the
   * interface uses for the same meaning.
   */
  const availabilityKinds: readonly {
    readonly kind: StateGlyphKind;
    readonly labelKey: string;
    readonly colorClass: string;
  }[] = [
    { kind: 'downloaded', labelKey: 'download.state.DOWNLOADED', colorClass: 'text-success' },
    {
      kind: 'not-downloaded',
      labelKey: 'mindMap.notInLibrary',
      colorClass: 'text-text-muted',
    },
    { kind: 'queued', labelKey: 'download.state.QUEUED', colorClass: 'text-text-muted' },
    { kind: 'update', labelKey: 'download.state.UPDATE_AVAILABLE', colorClass: 'text-warning' },
    { kind: 'failed', labelKey: 'download.state.FAILED', colorClass: 'text-danger' },
  ];

  for (const { kind, labelKey, colorClass } of availabilityKinds) {
    it(`draws the ${kind} state in its own shape, colour and words`, async () => {
      const element = (await render(kind)).nativeElement as HTMLElement;

      const svg = element.querySelector<SVGElement>(`[data-testid="state-glyph-${kind}"]`);
      expect(svg).not.toBeNull();
      expect(svg?.getAttribute('aria-hidden')).toBe('true');
      expect(svg?.getAttribute('stroke')).toBe('currentColor');
      expect(svg?.getAttribute('class')).toContain(colorClass);

      const label = element.querySelector('.sr-only');
      expect(label?.textContent?.trim()).toBe(translate.instant(labelKey));
      expect(label?.textContent?.trim()).not.toBe(labelKey);
    });
  }

  it('draws every kind with its own outline rather than reusing a shape', async () => {
    const allKinds: StateGlyphKind[] = [
      'completed',
      'downloaded',
      'not-downloaded',
      'queued',
      'update',
      'failed',
    ];
    const signatures = new Set<string>();

    for (const kind of allKinds) {
      const element = (await render(kind)).nativeElement as HTMLElement;
      const svg = element.querySelector(`[data-testid="state-glyph-${kind}"]`)!;
      const signature = Array.from(svg.querySelectorAll('circle, path'))
        .map((node) => node.outerHTML)
        .join('|');
      signatures.add(signature);
    }

    expect(signatures.size).toBe(allKinds.length);
  });
});
