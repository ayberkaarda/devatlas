import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import type { TranslationState } from '../core/platform/models';

/**
 * Marks text that is being shown in English because the requested locale has
 * no translation for it yet.
 *
 * It reads the resolution result the payload carries rather than comparing
 * locale strings itself. One response can mix translated and untranslated
 * entities — a translated track containing an untranslated lesson — so the
 * answer is per object, and a component that derived it from the active locale
 * would badge the whole page or none of it.
 */
@Component({
  selector: 'app-fallback-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `
    @if (state().isFallback) {
      <span
        class="inline-flex items-center rounded-sm bg-badge-bg px-2 py-0.5 text-xs font-medium text-badge-text"
        [title]="'content.fallbackBadgeHint' | translate"
      >
        {{ 'content.fallbackBadge' | translate }}
      </span>
    }
  `,
})
export class FallbackBadge {
  readonly state = input.required<TranslationState>();
}
