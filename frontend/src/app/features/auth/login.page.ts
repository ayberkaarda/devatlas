import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthSession } from '../../core/auth/auth-session';
import { errorKey } from '../../core/platform/error-key';

/**
 * The sign-in screen.
 *
 * It is the only place in the application that handles a password, and it
 * keeps none: the value goes straight to the session layer, which exchanges it
 * for tokens and forgets it. There is no "remember me" and no local copy —
 * the credential a restart needs is the refresh token, and where that can be
 * held safely it is held by the browser rather than by this code.
 */
@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  templateUrl: './login.page.html',
})
export class LoginPage {
  private readonly session = inject(AuthSession);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly router = inject(Router);

  /**
   * Where the visitor was headed when a guard sent them here.
   *
   * A query parameter absent from the URL does not leave `input()`'s own
   * default in place: the router's component-input binding still calls the
   * setter, and it calls it with `undefined`. Without this transform,
   * visiting `/login` with no query at all would make `returnUrl()` read
   * back as `undefined`, and `destination()` below would throw calling
   * `.startsWith` on it instead of falling back to the root route.
   */
  readonly returnUrl = input('/', { transform: (value: string | undefined) => value ?? '/' });

  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly submitting = signal(false);
  protected readonly failure = signal<string | null>(null);

  /**
   * Validation is announced only after an attempt.
   *
   * Marking a field invalid before anyone has typed in it reads as an
   * accusation, and it is the reason empty-form error messages get ignored.
   */
  private readonly attempted = signal(false);

  protected readonly emailMissing = computed(
    () => this.attempted() && this.email().trim().length === 0,
  );
  protected readonly passwordMissing = computed(
    () => this.attempted() && this.password().length === 0,
  );

  protected onEmail(event: Event): void {
    this.email.set((event.target as HTMLInputElement).value);
  }

  protected onPassword(event: Event): void {
    this.password.set((event.target as HTMLInputElement).value);
  }

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    // The submit button is marked unavailable rather than disabled while a
    // sign-in is in flight, so that it keeps the focus instead of dropping it
    // to the document body. A control that is not inert still submits its form
    // on Enter and still reacts to a click, so refusing the second attempt is
    // this handler's job: without it the page would sign in twice.
    if (this.submitting()) {
      return;
    }
    this.attempted.set(true);
    this.failure.set(null);

    if (this.emailMissing() || this.passwordMissing()) {
      // Focus goes to the first field that needs attention. Leaving it on the
      // submit button makes a reader who cannot see the form hunt backwards
      // through it for which of the two the message was about.
      this.focusFirstInvalidField();
      return;
    }

    this.submitting.set(true);
    try {
      await this.session.signIn(this.email().trim(), this.password());
      this.password.set('');
      await this.router.navigateByUrl(this.destination());
    } catch (error) {
      this.failure.set(errorKey(error));
    } finally {
      this.submitting.set(false);
    }
  }

  private focusFirstInvalidField(): void {
    const id = this.emailMissing() ? 'login-email' : 'login-password';
    this.host.nativeElement.querySelector<HTMLInputElement>(`#${id}`)?.focus();
  }

  /**
   * Where to go once signed in.
   *
   * The target arrives as a query parameter, which means anyone can put
   * anything in it. Only a path on this application is followed; an absolute
   * URL, or the protocol-relative form that looks like a path and is not,
   * would turn the sign-in screen into a redirector to someone else's site.
   */
  private destination(): string {
    const target = this.returnUrl();
    return target.startsWith('/') && !target.startsWith('//') ? target : '/';
  }
}
