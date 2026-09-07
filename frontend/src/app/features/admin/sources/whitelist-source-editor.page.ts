import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import { AdminApiClient } from '../../../core/admin/admin-api.client';
import type { WhitelistSource } from '../../../core/admin/admin-models';
import { errorKey } from '../../../core/platform/error-key';
import { PlatformError } from '../../../core/platform/errors';

const NAME_MAX_LENGTH = 120;
const URL_MAX_LENGTH = 2000;
const VERSION_PLACEHOLDER = '{version}';

/**
 * Create-or-edit screen for one whitelist source (§5.7, §7.9).
 *
 * The client-side checks on `feedUrl` and `verifyUrlPattern` mirror the
 * server's own rules exactly — absolute `https://`, and for the pattern,
 * exactly one `{version}` placeholder — so a mistake is caught before a
 * round trip, not instead of the server's own validation. The server
 * remains the actual authority: it re-checks the same rules and answers
 * `INSECURE_SOURCE_URL` / `INVALID_VERIFY_URL_PATTERN` regardless of what
 * this form thought.
 */
@Component({
  selector: 'app-whitelist-source-editor-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  templateUrl: './whitelist-source-editor.page.html',
})
export class WhitelistSourceEditorPage {
  private readonly api = inject(AdminApiClient);
  private readonly router = inject(Router);

  /** Absent on the `sources/new` route; present on `sources/:id`. */
  readonly id = input<string>();

  protected readonly isCreateMode = computed(() => this.id() === undefined);

  protected readonly loading = signal(true);
  protected readonly loadFailureKey = signal<string | null>(null);

  private readonly source = signal<WhitelistSource | null>(null);
  private loadedId: string | null = null;

  protected readonly name = signal('');
  protected readonly feedUrl = signal('');
  protected readonly verifyUrlPattern = signal('');
  protected readonly enabled = signal(true);
  protected readonly attempted = signal(false);

  protected readonly saving = signal(false);
  protected readonly saveFailureKey = signal<string | null>(null);

  /**
   * What just went right, for the polite live region in the template.
   *
   * In edit mode a successful save replaces the record with one that looks
   * identical and flips the button label back. Nothing else on the screen
   * moves, so without this the only confirmation is the absence of an error.
   */
  protected readonly outcomeKey = signal<string | null>(null);

  protected readonly nameErrorKey = computed<string | null>(() => {
    const value = this.name().trim();
    if (value.length === 0) {
      return 'admin.sources.editor.error.nameRequired';
    }
    if (value.length > NAME_MAX_LENGTH) {
      return 'admin.sources.editor.error.nameLength';
    }
    return null;
  });

  protected readonly feedUrlErrorKey = computed<string | null>(() => {
    const value = this.feedUrl().trim();
    if (value.length === 0) {
      return 'admin.sources.editor.error.feedUrlRequired';
    }
    if (value.length > URL_MAX_LENGTH) {
      return 'admin.sources.editor.error.feedUrlLength';
    }
    if (!isAbsoluteHttpsUrl(value)) {
      return 'admin.sources.editor.error.feedUrlInsecure';
    }
    return null;
  });

  protected readonly verifyUrlPatternErrorKey = computed<string | null>(() => {
    const value = this.verifyUrlPattern().trim();
    if (value.length === 0) {
      return 'admin.sources.editor.error.verifyUrlPatternRequired';
    }
    if (value.length > URL_MAX_LENGTH) {
      return 'admin.sources.editor.error.verifyUrlPatternLength';
    }
    if (!isAbsoluteHttpsUrl(value)) {
      return 'admin.sources.editor.error.verifyUrlPatternInsecure';
    }
    if (countOccurrences(value, VERSION_PLACEHOLDER) !== 1) {
      return 'admin.sources.editor.error.verifyUrlPatternPlaceholder';
    }
    return null;
  });

  protected readonly formValid = computed(
    () =>
      this.nameErrorKey() === null &&
      this.feedUrlErrorKey() === null &&
      this.verifyUrlPatternErrorKey() === null,
  );

  constructor() {
    effect(() => {
      const id = this.id();
      if (id === undefined) {
        this.loading.set(false);
        return;
      }
      if (id === this.loadedId) {
        return;
      }
      void this.loadExisting(id);
    });
  }

  protected retryLoad(): void {
    const id = this.id();
    if (id !== undefined) {
      void this.loadExisting(id);
    }
  }

  protected onNameInput(event: Event): void {
    this.name.set((event.target as HTMLInputElement).value);
  }

  protected onFeedUrlInput(event: Event): void {
    this.feedUrl.set((event.target as HTMLInputElement).value);
  }

  protected onVerifyUrlPatternInput(event: Event): void {
    this.verifyUrlPattern.set((event.target as HTMLInputElement).value);
  }

  protected onEnabledChange(event: Event): void {
    this.enabled.set((event.target as HTMLInputElement).checked);
  }

  protected async onSave(): Promise<void> {
    // The save button is marked unavailable rather than disabled while a save
    // is in flight, so that it keeps the focus instead of dropping it to the
    // document body. A control that is not inert still submits its form on
    // Enter, so this early return is what stops a second request going out on
    // top of the first.
    if (this.saving()) {
      return;
    }
    this.attempted.set(true);
    if (!this.formValid()) {
      return;
    }
    this.saving.set(true);
    this.saveFailureKey.set(null);
    this.outcomeKey.set(null);
    try {
      if (this.isCreateMode()) {
        const created = await this.api.createSource({
          name: this.name().trim(),
          feedUrl: this.feedUrl().trim(),
          verifyUrlPattern: this.verifyUrlPattern().trim(),
          enabled: this.enabled(),
        });
        this.applySource(created);
        await this.router.navigate(['/admin/sources', created.id]);
      } else {
        const current = this.source();
        if (current === null) {
          return;
        }
        const updated = await this.api.updateSource(current.id, {
          name: this.name().trim(),
          feedUrl: this.feedUrl().trim(),
          verifyUrlPattern: this.verifyUrlPattern().trim(),
          enabled: this.enabled(),
          version: current.version,
        });
        this.applySource(updated);
        this.outcomeKey.set('admin.sources.editor.saved');
      }
    } catch (error) {
      if (error instanceof PlatformError && error.code === 'VERSION_CONFLICT') {
        await this.refreshAfterConflict();
      }
      this.saveFailureKey.set(errorKey(error));
    } finally {
      this.saving.set(false);
    }
  }

  private async refreshAfterConflict(): Promise<void> {
    const current = this.source();
    if (current === null) {
      return;
    }
    try {
      this.source.set(await this.api.getSource(current.id));
    } catch {
      // The save error already surfaced; the stale version number here is
      // preferable to discarding the form's unsaved edits over a second
      // failed request.
    }
  }

  private async loadExisting(id: string): Promise<void> {
    this.loading.set(true);
    this.loadFailureKey.set(null);
    try {
      const loaded = await this.api.getSource(id);
      this.applySource(loaded);
      this.loadedId = id;
    } catch (error) {
      this.loadFailureKey.set(errorKey(error));
    } finally {
      this.loading.set(false);
    }
  }

  private applySource(loaded: WhitelistSource): void {
    this.source.set(loaded);
    this.name.set(loaded.name);
    this.feedUrl.set(loaded.feedUrl);
    this.verifyUrlPattern.set(loaded.verifyUrlPattern);
    this.enabled.set(loaded.enabled);
  }
}

function isAbsoluteHttpsUrl(value: string): boolean {
  try {
    return new URL(value).protocol === 'https:';
  } catch {
    return false;
  }
}

function countOccurrences(value: string, needle: string): number {
  return value.split(needle).length - 1;
}
