import { Component, signal } from '@angular/core';

/**
 * The same tick, recorded twice: once in a plain field and once in a signal.
 *
 * The timer callback is outside every binding Angular knows about. In a
 * zoneless application — the default since Angular v21 — nothing intercepts
 * that callback, so the plain field changes and no view is scheduled for
 * refresh. The signal write is a notification, and the template that read it is
 * what gets refreshed.
 *
 * Both numbers advance in memory. Only one of them advances on screen, and the
 * pair is the whole lesson.
 */
@Component({
  selector: 'app-tick-counter',
  template: `
    <p>plain field: {{ plainTicks }}</p>
    <p>signal: {{ ticks() }}</p>
  `,
})
export class TickCounter {
  protected plainTicks = 0;

  protected readonly ticks = signal(0);

  private readonly timer = setInterval(() => {
    this.plainTicks++;
    this.ticks.update((value) => value + 1);
  }, 1000);

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }
}
