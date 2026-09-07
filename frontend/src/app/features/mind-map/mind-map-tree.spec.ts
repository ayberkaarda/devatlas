import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import type { LessonSummary, MindMapNode } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { MindMapTree } from './mind-map-tree';

function node(id: string, children: readonly MindMapNode[] = []): MindMapNode {
  return { id, label: id, lessonId: null, children };
}

describe('MindMapTree', () => {
  beforeEach(async () => {
    const fake = new FakePlatformService();
    fake.capabilities = { canDownload: true, hasLocalStore: true };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
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

  async function render() {
    const fixture = TestBed.createComponent(MindMapTree);
    fixture.componentRef.setInput('root', node('root', [node('a'), node('b')]));
    fixture.componentRef.setInput('lessonIndex', new Map<string, LessonSummary>());
    fixture.componentRef.setInput('trackTitle', 'Signals');
    fixture.componentRef.setInput('trackSlug', 'signals');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('does not take focus away from the reader when the layout first arrives', async () => {
    const outside = document.createElement('button');
    document.body.appendChild(outside);
    outside.focus();

    await render();

    // The tree is rendered inside a deferred block on the screen that uses it,
    // so its layout arrives well after the reader has settled somewhere. Its
    // appearing is not a reason to move anybody's focus.
    expect(document.activeElement).toBe(outside);
    outside.remove();
  });

  it('follows the keyboard once the reader asks it to move', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    const root = element.querySelector<HTMLElement>('[data-node-id="root"]')!;
    root.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(document.activeElement).toBe(element.querySelector('[data-node-id="a"]'));
  });

  it('states where each node sits in the hierarchy the arrow keys traverse', async () => {
    const element = (await render()).nativeElement as HTMLElement;

    const root = element.querySelector('[data-node-id="root"]')!;
    const first = element.querySelector('[data-node-id="a"]')!;

    // Without these, assistive technology can describe the nodes but not the
    // shape they are arranged in, which is the whole content of a mind map.
    expect(root.getAttribute('aria-level')).toBe('1');
    expect(first.getAttribute('aria-level')).toBe('2');
    expect(first.getAttribute('aria-setsize')).toBe('2');
    expect(first.getAttribute('aria-posinset')).toBe('1');
    expect(element.querySelector('[data-node-id="b"]')!.getAttribute('aria-posinset')).toBe('2');
  });
});
