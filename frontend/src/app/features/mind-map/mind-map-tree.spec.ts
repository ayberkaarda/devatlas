import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakePlatformService } from '../../../testing/fake-platform.service';
import { LocaleService } from '../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../core/i18n/translations';
import type { Availability, LessonSummary, MindMapNode } from '../../core/platform/models';
import { contentAvailability } from '../../core/platform/models';
import { PlatformService } from '../../core/platform/platform.service';
import { MindMapTree } from './mind-map-tree';

function node(id: string, children: readonly MindMapNode[] = []): MindMapNode {
  return { id, label: id, lessonId: null, children };
}

function lessonNode(id: string, lessonId: string, children: readonly MindMapNode[] = []) {
  return { id, label: id, lessonId, children };
}

function lessonSummary(id: string, availability: Availability): LessonSummary {
  return {
    id,
    slug: id,
    title: id,
    difficulty: null,
    estimatedMinutes: null,
    order: 0,
    availability: contentAvailability(availability),
    translation: { locale: 'en', requestedLocale: 'en', isFallback: false },
  };
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
    fixture.componentRef.setInput('completedLessonIds', new Set<string>());
    fixture.componentRef.setInput('trackTitle', 'Signals');
    fixture.componentRef.setInput('trackSlug', 'signals');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  /**
   * One map carrying every state a node can be drawn in at once, because the
   * point of these assertions is that the states are told apart — which is a
   * claim about them side by side, not one at a time.
   *
   * The shape mirrors the one the corpus derives: a track root, a module
   * heading beneath it, lessons beneath that, and a concept hanging off a
   * lesson with nothing under it and no lesson of its own.
   */
  async function renderStates() {
    const fixture = TestBed.createComponent(MindMapTree);
    fixture.componentRef.setInput(
      'root',
      node('root', [
        node('module', [
          lessonNode('done', 'lesson-done', [node('concept')]),
          lessonNode('stored', 'lesson-stored'),
          lessonNode('absent', 'lesson-absent'),
        ]),
      ]),
    );
    fixture.componentRef.setInput(
      'lessonIndex',
      new Map<string, LessonSummary>([
        ['lesson-done', lessonSummary('lesson-done', 'DOWNLOADED')],
        ['lesson-stored', lessonSummary('lesson-stored', 'DOWNLOADED')],
        ['lesson-absent', lessonSummary('lesson-absent', 'NOT_DOWNLOADED')],
      ]),
    );
    fixture.componentRef.setInput('completedLessonIds', new Set<string>(['lesson-done']));
    fixture.componentRef.setInput('trackTitle', 'Signals');
    fixture.componentRef.setInput('trackSlug', 'signals');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    return (id: string) => element.querySelector<SVGGElement>(`[data-node-id="${id}"]`)!;
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

  it('marks a finished lesson with the reading-state mark, and says so in words', async () => {
    const nodeFor = await renderStates();
    const done = nodeFor('done');

    const mark = done.querySelector('[data-testid="mind-map-completed"]')!;
    expect(mark).not.toBeNull();
    // The shape and the colour are the ones the track detail page draws beside
    // a finished lesson. A second tick, or a second green, would say the same
    // thing in a way the reader has to learn twice.
    expect(mark.getAttribute('class')).toContain('stroke-success');
    expect(mark.querySelector('path')!.getAttribute('d')).toBe('m5.15 8.2 2 2 3.7-4.4');
    // The ring lands where every other node's disc edge is: the glyph is
    // authored around a radius of 6.25 and scaled up to the node radius of 8.
    expect(mark.getAttribute('transform')).toContain('scale(1.28)');

    // The mark is drawn, not spoken, so the accessible name carries the fact
    // too — and the disc it replaced is gone.
    expect(done.getAttribute('aria-label')).toBe('Open the lesson done, Completed');
    expect(done.querySelector('circle.fill-accent')).toBeNull();
  });

  it('leaves a stored but unread lesson looking exactly as it did', async () => {
    const nodeFor = await renderStates();
    const stored = nodeFor('stored');

    const disc = stored.querySelector('circle')!;
    expect(disc.getAttribute('class')).toContain('fill-accent');
    expect(disc.getAttribute('r')).toBe('8');
    expect(disc.getAttribute('stroke-dasharray')).toBeNull();
    // Downloading a lesson is not reading it: the finished mark must not
    // appear here, or the new state says nothing the old one did not.
    expect(stored.querySelector('[data-testid="mind-map-completed"]')).toBeNull();
    expect(stored.getAttribute('aria-label')).toBe('Open the lesson stored');
  });

  it('leaves a lesson that is neither stored nor read looking exactly as it did', async () => {
    const nodeFor = await renderStates();
    const absent = nodeFor('absent');

    const disc = absent.querySelector('circle')!;
    expect(disc.getAttribute('class')).toContain('fill-surface-raised');
    expect(disc.getAttribute('class')).toContain('stroke-border');
    expect(disc.getAttribute('stroke-dasharray')).toBe('3,2');
    expect(absent.querySelector('[data-testid="mind-map-completed"]')).toBeNull();
    expect(absent.textContent).toContain('Not downloaded');
  });

  it('draws a concept leaf quieter than any lesson node, whatever state that lesson is in', async () => {
    const nodeFor = await renderStates();
    const concept = nodeFor('concept');

    const dot = concept.querySelector('[data-testid="mind-map-concept"]')!;
    const conceptRadius = Number(dot.getAttribute('r'));

    // Smaller than every lesson mark on the canvas, not just the one it hangs
    // under: a concept is not a destination, and the three lesson states are
    // all destinations regardless of how each one is filled.
    for (const id of ['stored', 'absent']) {
      expect(conceptRadius).toBeLessThan(
        Number(nodeFor(id).querySelector('circle')!.getAttribute('r')),
      );
    }
    expect(conceptRadius).toBeLessThan(
      Number(
        nodeFor('done')
          .querySelector('[data-testid="mind-map-completed"] circle')!
          .getAttribute('r'),
      ),
    );

    // Quieter as well as smaller: no outline, a muted fill, and a label in the
    // supporting-text style rather than the one lessons are titled in.
    expect(dot.getAttribute('class')).toBe('fill-text-muted');
    expect(dot.getAttribute('class')).not.toContain('stroke');
    expect(concept.querySelector('text')!.getAttribute('class')).toBe('fill-text-muted text-xs');
    // Nothing to click through to, so nothing that offers to be clicked.
    expect(concept.classList.contains('cursor-pointer')).toBe(false);
    expect(nodeFor('stored').classList.contains('cursor-pointer')).toBe(true);
  });

  it('gives a module heading more weight than the lessons listed under it', async () => {
    const nodeFor = await renderStates();

    // A module disc differs from an unread lesson's by a dash pattern on a
    // 1.5px stroke and nothing else, which is not enough to sort a column by
    // at a glance.
    expect(nodeFor('module').querySelector('text')!.getAttribute('class')).toContain('font-medium');
    expect(nodeFor('stored').querySelector('text')!.getAttribute('class')).not.toContain(
      'font-medium',
    );
  });
});
