import { ComponentFixture, TestBed } from '@angular/core/testing';

import { QueueEntry, QueueGateway, QueuePanel } from './testing-without-a-browser-1';

class FakeQueueGateway extends QueueGateway {
  constructor(private readonly rows: readonly QueueEntry[]) {
    super();
  }

  entries(): Promise<readonly QueueEntry[]> {
    return Promise.resolve(this.rows);
  }
}

/**
 * A component test with no browser and no server.
 *
 * `TestBed.configureTestingModule` builds an injector for the test, so the
 * double is supplied the same way the real service would be. `createComponent`
 * instantiates the component and puts its element in the test runner's DOM.
 *
 * Inputs are set through `componentRef.setInput` rather than by assigning to
 * the property: assigning writes a value nothing notices, while the method
 * "will properly mark for check component using the `OnPush` change detection
 * strategy" — the strategy every component has in Angular 22.
 *
 * `detectChanges` is what turns the current state into DOM. Reading the element
 * before calling it reads the previous render, which in this application's
 * zoneless setup is the difference between an empty list and the expected one.
 */
describe('QueuePanel', () => {
  function render(rows: readonly QueueEntry[]): ComponentFixture<QueuePanel> {
    TestBed.configureTestingModule({
      providers: [{ provide: QueueGateway, useValue: new FakeQueueGateway(rows) }],
    });
    const fixture = TestBed.createComponent(QueuePanel);
    fixture.componentRef.setInput('heading', 'Downloads');
    fixture.detectChanges();
    return fixture;
  }

  function elements(fixture: ComponentFixture<QueuePanel>, testId: string): HTMLElement[] {
    const host = fixture.nativeElement as HTMLElement;
    return Array.from(host.querySelectorAll<HTMLElement>(`[data-testid="${testId}"]`));
  }

  it('lists one row per queue entry', async () => {
    const fixture = render([
      { id: 'lesson-1', state: 'QUEUED' },
      { id: 'lesson-2', state: 'DONE' },
    ]);

    await fixture.componentInstance.load();
    fixture.detectChanges();

    expect(elements(fixture, 'queue-entry')).toHaveLength(2);
  });

  it('says nothing about failures when there are none', async () => {
    const fixture = render([{ id: 'lesson-1', state: 'DONE' }]);

    await fixture.componentInstance.load();
    fixture.detectChanges();

    // Absent rather than empty: a nought in an alert is still an alert.
    expect(elements(fixture, 'queue-failures')).toHaveLength(0);
  });

  it('raises an alert that carries the number of failures', async () => {
    const fixture = render([
      { id: 'lesson-1', state: 'FAILED' },
      { id: 'lesson-2', state: 'FAILED' },
    ]);

    await fixture.componentInstance.load();
    fixture.detectChanges();

    const [alert] = elements(fixture, 'queue-failures');
    expect(alert.getAttribute('role')).toBe('alert');
    expect(alert.textContent?.trim()).toBe('2');
  });
});
