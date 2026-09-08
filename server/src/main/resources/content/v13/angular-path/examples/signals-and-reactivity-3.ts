import { Component, inject } from '@angular/core';

import { QueueStore } from './signals-and-reactivity-2';

/**
 * A component that reads the store and holds nothing.
 *
 * The template calls `unfinished()` and `queue()`. Calling a signal is what
 * subscribes this view to it, so there is no lifecycle hook to write, no
 * subscription to remember and nothing to unsubscribe: the dependency is
 * recorded by the read itself and dropped when the view is destroyed.
 *
 * The badge is deliberately absent rather than showing a nought, so the
 * template asks the derived value rather than re-deriving it here.
 */
@Component({
  selector: 'app-queue-summary',
  template: `
    @if (store.unfinished() > 0) {
      <span class="badge">{{ store.unfinished() }}</span>
    }
    <ul>
      @for (entry of store.queue(); track entry.id) {
        <li>{{ entry.id }} — {{ entry.state }}</li>
      }
    </ul>
  `,
})
export class QueueSummary {
  protected readonly store = inject(QueueStore);
}
