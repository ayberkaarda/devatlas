import { Injectable, computed, signal } from '@angular/core';

interface QueueEntry {
  readonly id: string;
  readonly state: 'QUEUED' | 'DOWNLOADING' | 'DONE' | 'FAILED';
}

/**
 * State that announces its own changes.
 *
 * The writable signal is private and the readable one is handed out through
 * `asReadonly()`, so every write to the queue goes through a method on this
 * class. That is not ceremony: a signal read anywhere in the application
 * subscribes the reader, and a second writer would mean two places to look
 * when the wrong value arrives on screen.
 */
@Injectable({ providedIn: 'root' })
export class QueueStore {
  private readonly entries = signal<readonly QueueEntry[]>([]);

  readonly queue = this.entries.asReadonly();

  /** Derived, not stored. Nothing has to remember to keep this in step. */
  readonly unfinished = computed(
    () => this.entries().filter((entry) => entry.state !== 'DONE').length,
  );

  /** `set` replaces the value outright. */
  replace(entries: readonly QueueEntry[]): void {
    this.entries.set(entries);
  }

  /**
   * `update` derives the next value from the current one. A new array rather
   * than a push: signals compare with `Object.is` by default, so mutating the
   * array in place would leave the reference identical and notify nobody.
   */
  add(entry: QueueEntry): void {
    this.entries.update((current) => [...current, entry]);
  }
}
