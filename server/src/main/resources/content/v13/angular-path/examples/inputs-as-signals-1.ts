import { Component, booleanAttribute, computed, input, numberAttribute } from '@angular/core';

/**
 * The four shapes an input comes in, and the one thing they have in common.
 *
 * `input()` returns a read-only signal. The parent writes it; the component
 * reads it. Attempting to write it from inside is a compile error rather than
 * a runtime surprise, which is the point of the marked line below.
 */
@Component({
  selector: 'app-lesson-card',
  template: `
    <article [class.compact]="compact()">
      <h3>{{ title() }}</h3>
      <p>{{ readingTime() }}</p>
    </article>
  `,
})
export class LessonCard {
  /** Required: the template cannot render anything sensible without it. */
  readonly title = input.required<string>();

  /** Optional with a default, so the value is never `undefined`. */
  readonly minutes = input(0, { transform: numberAttribute });

  /** An attribute written with no value arrives as the empty string. */
  readonly compact = input(false, { transform: booleanAttribute });

  /** The name in a template differs from the name in TypeScript. */
  readonly slug = input.required<string>({ alias: 'lessonSlug' });

  protected readonly readingTime = computed(() =>
    this.minutes() > 0 ? `${this.minutes()} min` : 'unknown length',
  );

  reset(): void {
    // @ts-expect-error Property 'set' does not exist on type 'InputSignal<string>'.
    this.title.set('');
  }
}
