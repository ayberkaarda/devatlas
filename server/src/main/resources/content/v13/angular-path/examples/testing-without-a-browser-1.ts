import { Component, computed, inject, input, signal } from '@angular/core';

export interface QueueEntry {
  readonly id: string;
  readonly state: 'QUEUED' | 'DONE' | 'FAILED';
}

/** The collaborator the component asks for its data. */
export abstract class QueueGateway {
  abstract entries(): Promise<readonly QueueEntry[]>;
}

/**
 * The component under test.
 *
 * Two things make it testable, and neither is a testing concern. It takes its
 * collaborator by injection, so a test can supply a double without patching
 * anything global; and every element a test needs to find carries a
 * `data-testid`, so an assertion can name an element rather than describing
 * where it happens to sit.
 */
@Component({
  selector: 'app-queue-panel',
  template: `
    <h2 data-testid="queue-heading">{{ heading() }}</h2>
    @if (failedCount() > 0) {
      <p data-testid="queue-failures" role="alert">{{ failedCount() }}</p>
    }
    <ul data-testid="queue-list">
      @for (entry of entries(); track entry.id) {
        <li data-testid="queue-entry">{{ entry.id }}</li>
      }
    </ul>
  `,
})
export class QueuePanel {
  private readonly gateway = inject(QueueGateway);

  readonly heading = input.required<string>();

  protected readonly entries = signal<readonly QueueEntry[]>([]);

  protected readonly failedCount = computed(
    () => this.entries().filter((entry) => entry.state === 'FAILED').length,
  );

  async load(): Promise<void> {
    this.entries.set(await this.gateway.entries());
  }
}
