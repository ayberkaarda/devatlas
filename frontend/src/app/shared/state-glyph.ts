import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

/**
 * The states this glyph can draw: a lesson's own reading progress
 * (`completed`) alongside the states a piece of content's local copy can be
 * in — stored (`downloaded`), superseded by a newer version (`update`), or
 * stopped with an error (`failed`). They belong in one union rather than a
 * second icon component, so that every state mark in the application is one
 * shape vocabulary. Adding a member makes every lookup keyed by this type —
 * the colour and label tables below, and the shape switch — fail to compile
 * until the new member is handled there too.
 *
 * Not every state a piece of content can be in gets a member here: a
 * not-yet-downloaded lesson is represented by offering a download button,
 * not by a mark, and a queued one has no screen that draws it either. A
 * kind with nothing that renders it is dead configuration — it should be
 * added back only once a screen actually needs to draw it.
 */
export type StateGlyphKind = 'completed' | 'downloaded' | 'update' | 'failed';

/**
 * Reached only when every member of `StateGlyphKind` has already been
 * matched above it in the caller's `switch`. Before this existed, a kind
 * added to the union without a matching case simply drew nothing — a bug
 * nobody would notice until a user reported a blank icon. Typing the
 * parameter `never` turns that gap into a compile error at the moment the
 * union grows, which is the point at which it is cheapest to fix.
 */
function assertNever(value: never): never {
  throw new Error(`Unhandled state glyph kind: ${String(value)}`);
}

/**
 * The stroke geometry for one kind, relative to a 16x16 viewBox centred on
 * (8, 8). A kind draws a ring, a set of line paths, or both — `completed`
 * and `failed` put a mark inside the same ring, and the rest are paths
 * with no ring at all.
 */
export interface GlyphShape {
  readonly circle?: true;
  readonly paths: readonly string[];
}

/**
 * Exported as well as used by the component below, for the one caller that
 * cannot embed the component: a canvas already drawing its own `<svg>` in its
 * own coordinate system, which needs the geometry without a second viewport
 * and a second 16px sizing around it. Such a caller reading the shape from
 * here keeps one drawing for one fact — the alternative is a hand-copied path
 * string that stops matching this one the first time either is adjusted, in
 * two places that no longer know about each other.
 */
export function shapeFor(kind: StateGlyphKind): GlyphShape {
  switch (kind) {
    case 'completed':
      // A tick inside a closed ring. The ring is what separates this mark
      // from a bare check at a glance and, being a closed shape, it stays
      // legible at the 16px this renders at where a lone stroke would not.
      return { circle: true, paths: ['m5.15 8.2 2 2 3.7-4.4'] };
    case 'downloaded':
      // An arrow coming to rest on a line beneath it. The tray is what
      // distinguishes "this is stored" from just an arrow on its own.
      return { paths: ['M8 2.5v7', 'M4.8 7.2 8 10.4l3.2-3.2', 'M3.5 13.2h9'] };
    case 'update':
      // An open arc closed off with an arrowhead: the familiar refresh
      // shape, read as "a newer copy exists" rather than "under way".
      return { paths: ['M12.3 8a4.3 4.3 0 1 1-1.25-3.05', 'M12.3 2.7v2.75h-2.75'] };
    case 'failed':
      // A cross inside the same ring `completed` uses, so the two read as
      // a pair — one mark for "this worked", the other for "this did not"
      // — rather than as two unrelated drawings.
      return { circle: true, paths: ['M5.8 5.8l4.4 4.4', 'M10.2 5.8l-4.4 4.4'] };
    default:
      return assertNever(kind);
  }
}

/**
 * One `--color-*` text class per kind, matching the family the rest of the
 * interface already uses for the same meaning (success / warning / danger /
 * muted). Written as a `Record` over the whole union, rather than a
 * `switch`, so that a kind added without an entry here is a compile error —
 * "Property is missing in type" — instead of an icon with no colour.
 */
const KIND_COLOR_CLASS: Record<StateGlyphKind, string> = {
  completed: 'text-success',
  downloaded: 'text-success',
  update: 'text-warning',
  failed: 'text-danger',
};

/**
 * The translation key that says the kind in words, for the screen-reader
 * text next to the drawing. Each of these already exists in the catalogue
 * for some other rendering of the same state — this glyph does not need,
 * and this wave does not add, a word of its own.
 */
const KIND_LABEL_KEY: Record<StateGlyphKind, string> = {
  completed: 'progress.completed',
  downloaded: 'download.state.DOWNLOADED',
  update: 'download.state.UPDATE_AVAILABLE',
  failed: 'download.state.FAILED',
};

/**
 * A small state mark: a drawn shape plus the words that say what it means.
 *
 * Two rules shape it.
 *
 * The meaning is never carried by colour alone. Colour distinguishes the
 * states from each other at a glance, but each one also has its own outline
 * — a ring, an arrow, an arc — and, more importantly, its own text: the
 * `<svg>` is `aria-hidden` and a screen-reader-only span next to it carries
 * the state as a word. That span is part of this component rather than
 * something each call site remembers to add, because a mark whose meaning
 * depends on a sibling element loses it the first time somebody copies just
 * the icon.
 *
 * The drawing is inline and painted in `currentColor`. There is no icon
 * package in this project, and a mark that inherits its colour follows the
 * palette without a second definition per theme. The colour comes from the
 * token layer; `--color-success` measures 4.60:1 against the raised
 * surfaces these marks sit on, which clears the 3:1 a non-text graphic
 * needs with room to spare but not enough to be worth spending on a
 * lighter green.
 */
@Component({
  selector: 'app-state-glyph',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  host: { class: 'inline-flex items-center' },
  template: `
    <svg
      class="h-4 w-4 shrink-0"
      [class]="colorClass()"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      stroke-width="1.6"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      [attr.data-testid]="'state-glyph-' + kind()"
    >
      @if (shape().circle) {
        <circle cx="8" cy="8" r="6.25" />
      }
      @for (d of shape().paths; track d) {
        <path [attr.d]="d" />
      }
    </svg>
    <span class="sr-only">{{ labelKey() | translate }}</span>
  `,
})
export class StateGlyph {
  readonly kind = input.required<StateGlyphKind>();

  protected readonly shape = computed(() => shapeFor(this.kind()));
  protected readonly colorClass = computed(() => KIND_COLOR_CLASS[this.kind()]);
  protected readonly labelKey = computed(() => KIND_LABEL_KEY[this.kind()]);
}
