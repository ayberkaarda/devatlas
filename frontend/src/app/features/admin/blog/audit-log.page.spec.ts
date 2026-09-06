import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { FakeAdminApiClient } from '../../../../testing/fake-admin-api.client';
import { FakePlatformService } from '../../../../testing/fake-platform.service';
import type { AuditLogItem, Page } from '../../../core/admin/admin-models';
import { AdminApiClient } from '../../../core/admin/admin-api.client';
import { LocaleService } from '../../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../../core/i18n/translations';
import { PlatformService } from '../../../core/platform/platform.service';
import { AuditLogPage } from './audit-log.page';

function item(overrides: Partial<AuditLogItem>): AuditLogItem {
  return {
    id: 'log-1',
    step: 'FETCH',
    actorUserId: null,
    fromStatus: null,
    toStatus: null,
    reason: null,
    occurredAt: '2026-08-20T11:58:42.310Z',
    ...overrides,
  };
}

function pageOf(items: readonly AuditLogItem[]): Page<AuditLogItem> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

function setPendingTranslations(): void {
  TestBed.inject(TranslateService).setTranslation(
    'en',
    {
      admin: {
        audit: {
          heading: 'Audit log',
          backToPost: 'Back to post',
          empty: 'No history yet.',
          machineStep: 'Pipeline',
          humanStep: 'Human decision',
          column: {
            step: 'Step',
            type: 'Type',
            from: 'From',
            to: 'To',
            reason: 'Reason',
            occurredAt: 'When',
          },
        },
      },
    },
    true,
  );
}

describe('AuditLogPage', () => {
  let api: FakeAdminApiClient;

  beforeEach(async () => {
    api = new FakeAdminApiClient();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: {} },
        { provide: AdminApiClient, useValue: api },
        { provide: PlatformService, useValue: new FakePlatformService() },
        provideTranslateService({
          loader: BundledTranslateLoader,
          fallbackLang: 'en',
          lang: 'en',
        }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
    setPendingTranslations();
    jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  async function render(id: string) {
    const fixture = TestBed.createComponent(AuditLogPage);
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('labels a step with a null actor as pipeline, and one with an actor as a human decision', async () => {
    api.listAuditLogCalls.mockResolvedValue(
      pageOf([
        item({ id: 'a', step: 'FETCH', actorUserId: null }),
        item({
          id: 'b',
          step: 'APPROVE',
          actorUserId: 'user-1',
          fromStatus: 'PENDING_REVIEW',
          toStatus: 'PUBLISHED',
          reason: 'Looks correct.',
        }),
      ]),
    );
    const fixture = await render('post-1');
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');

    expect(rows[0].textContent).toContain('Pipeline');
    expect(rows[1].textContent).toContain('Human decision');
  });

  it('requests the audit log for the post id it was given', async () => {
    api.listAuditLogCalls.mockResolvedValue(pageOf([item({})]));
    await render('post-42');

    expect(api.listAuditLogCalls.lastArgs).toEqual(['post-42', { page: 0, size: 20 }]);
  });

  it('navigates to the next page through the shared pagination control', async () => {
    api.listAuditLogCalls.mockResolvedValue({
      items: [item({})],
      page: 0,
      size: 20,
      totalElements: 40,
      totalPages: 2,
    });
    const fixture = await render('post-1');
    const navigateSpy = TestBed.inject(Router).navigate as jest.Mock;

    const nextButton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('nav button'),
    ).find((button) => button.textContent?.includes('Next')) as HTMLButtonElement;
    nextButton.click();

    expect(navigateSpy).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { page: '1' } }),
    );
  });
});
