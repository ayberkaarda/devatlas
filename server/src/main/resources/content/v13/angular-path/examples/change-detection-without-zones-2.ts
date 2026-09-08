import { ApplicationConfig, ChangeDetectorRef, Component, inject } from '@angular/core';

/**
 * A bootstrap configuration with nothing in it about change detection.
 *
 * There is no zone provider and no `provideZonelessChangeDetection()` call:
 * from Angular v21 onward zoneless is the default, and a v22 application that
 * says nothing is already zoneless. The absence is the configuration.
 */
export const appConfig: ApplicationConfig = {
  providers: [],
};

interface Subscription {
  unsubscribe(): void;
}

abstract class LegacyEventSource {
  abstract subscribe(handler: (message: string) => void): Subscription;
}

/**
 * The escape hatch, and the reason it is rarely needed.
 *
 * `message` is a plain field written from a third-party subscription, so
 * nothing about the write tells Angular that this view is out of date.
 * `markForCheck()` says so explicitly; it is one of the notifications a
 * zoneless application acts on.
 *
 * A signal would remove the call and the risk of forgetting it. This shape is
 * for values that arrive from an API you do not own and cannot wrap.
 */
@Component({
  selector: 'app-legacy-feed',
  template: `<p>{{ message }}</p>`,
})
export class LegacyFeed {
  private readonly source = inject(LegacyEventSource);
  private readonly changeDetector = inject(ChangeDetectorRef);

  protected message = '';

  private readonly subscription = this.source.subscribe((message) => {
    this.message = message;
    this.changeDetector.markForCheck();
  });

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
