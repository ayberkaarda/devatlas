import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

import type { SourceFetchResult } from '../../../core/admin/admin-models';

/**
 * The outcome of one manual fetch (`POST
 * .../whitelist-sources/{id}/fetch`, §5.7): the four counters that always
 * satisfy `fetched = created + duplicates + rejected`, the per-rejection
 * reasons, and a link to each newly created source update for the reviewer
 * to pick up next.
 *
 * A plain presentational component, not a page: `whitelist-source-list.page`
 * owns when a fetch runs and what happens while it is in flight, and hands
 * this component the finished `SourceFetchResult` to lay out.
 */
@Component({
  selector: 'app-fetch-result',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TranslatePipe],
  templateUrl: './fetch-result.html',
})
export class FetchResult {
  readonly result = input.required<SourceFetchResult>();

  protected readonly durationSeconds = computed(() => (this.result().durationMs / 1000).toFixed(1));
}
