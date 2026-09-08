import { Injectable, computed, effect, signal } from '@angular/core';

interface QueueEntry {
  readonly id: string;
  readonly state: 'QUEUED' | 'DONE';
}

/**
 * The wrong shape, kept for comparison.
 *
 * `unfinished` is worked out from `entries`, and an effect is used to copy it
 * across. The value is now stored twice, the copy is a run behind the original
 * for as long as the effect has not run — effects "always execute
 * asynchronously, during the change detection process" — and the class has a
 * write path that nothing in the class controls.
 */
@Injectable({ providedIn: 'root' })
export class MirroredStore {
  readonly entries = signal<readonly QueueEntry[]>([]);
  readonly unfinished = signal(0);

  constructor() {
    effect(() => {
      this.unfinished.set(this.entries().filter((entry) => entry.state !== 'DONE').length);
    });
  }
}

/**
 * The same fact, declared instead of propagated.
 *
 * One stored signal, one derivation, no schedule to reason about and no second
 * copy to go stale. This is what the guidance "avoid using effects for
 * propagation of state changes" asks for.
 */
@Injectable({ providedIn: 'root' })
export class DerivedStore {
  private readonly items = signal<readonly QueueEntry[]>([]);

  readonly entries = this.items.asReadonly();

  readonly unfinished = computed(
    () => this.items().filter((entry) => entry.state !== 'DONE').length,
  );

  replace(entries: readonly QueueEntry[]): void {
    this.items.set(entries);
  }
}
