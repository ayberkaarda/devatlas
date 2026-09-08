import { Component, signal } from '@angular/core';

/**
 * A leaf component: a selector, a template, and a class the template reads.
 *
 * Nothing here touches the DOM. `dismiss()` changes a value the class owns and
 * the template is re-evaluated from that value; there is no element lookup and
 * no attribute assignment anywhere in the file.
 */
@Component({
  selector: 'app-notice',
  template: `
    @if (visible()) {
      <p class="notice">{{ message }}</p>
      <button type="button" (click)="dismiss()">Dismiss</button>
    }
  `,
})
export class Notice {
  /**
   * Read by the template, so it may not be `private`: Angular's template type
   * checker resolves names against the class, and a private member is not
   * visible to the compiled template code.
   */
  protected readonly message = 'Your progress is saved on this device.';

  private readonly shown = signal(true);

  protected readonly visible = this.shown.asReadonly();

  protected dismiss(): void {
    this.shown.set(false);
  }
}

/**
 * A component that uses another one. `imports` is what makes `<app-notice>`
 * resolvable inside this template: a standalone component declares its own
 * template's dependencies, so the file that renders a screen is the file that
 * says what the screen is built from.
 */
@Component({
  selector: 'app-settings-panel',
  imports: [Notice],
  template: `
    <h2>Settings</h2>
    <app-notice />
  `,
})
export class SettingsPanel {}
