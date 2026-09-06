import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { FakeAdminApiClient } from '../../../../testing/fake-admin-api.client';
import { FakePlatformService } from '../../../../testing/fake-platform.service';
import { AdminApiClient } from '../../../core/admin/admin-api.client';
import type { SourceUpdateDetail } from '../../../core/admin/admin-models';
import { LocaleService } from '../../../core/i18n/locale.service';
import { BundledTranslateLoader } from '../../../core/i18n/translations';
import { PlatformService } from '../../../core/platform/platform.service';
import { SourceUpdatePage } from './source-update.page';

const FULL_HASH = '9f2c1b7d5a3e08c4b6d1f0a29e7c48b53d61fa0c8e29d7b4a51c30f6e8d92b17';

function detail(overrides: Partial<SourceUpdateDetail> = {}): SourceUpdateDetail {
  return {
    id: 'update-1',
    whitelistSource: {
      id: 'source-1',
      name: 'Spring Blog',
      feedUrl: 'https://spring.io/blog.atom',
    },
    versionString: '4.1.1',
    contentHash: FULL_HASH,
    fetchedAt: '2026-08-20T11:58:42.310Z',
    verifyStatus: 'VERIFIED',
    verifyChecks: [
      { check: 'SOURCE_WHITELISTED', passed: true, detail: null },
      { check: 'VERSION_CONFIRMED', passed: true, detail: null },
      { check: 'HASH_NOT_SEEN', passed: true, detail: null },
      { check: 'CONTENT_SANITY', passed: true, detail: null },
    ],
    rawContent: 'Spring Boot 4.1.1 has been released and is available from Maven Central.',
    ...overrides,
  };
}

describe('SourceUpdatePage', () => {
  let api: FakeAdminApiClient;

  beforeEach(async () => {
    api = new FakeAdminApiClient();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AdminApiClient, useValue: api },
        { provide: PlatformService, useValue: new FakePlatformService() },
        provideTranslateService({ loader: BundledTranslateLoader, fallbackLang: 'en', lang: 'en' }),
      ],
    });
    await TestBed.inject(LocaleService).initialize('en');
  });

  async function render(value: SourceUpdateDetail) {
    api.getSourceUpdateCalls.mockResolvedValue(value);
    const fixture = TestBed.createComponent(SourceUpdatePage);
    fixture.componentRef.setInput('id', value.id);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('shows a truncated content hash with the full value in the title attribute', async () => {
    const fixture = await render(detail());
    const element = fixture.nativeElement as HTMLElement;

    const hashNode = Array.from(element.querySelectorAll('dd')).find(
      (dd) => dd.getAttribute('title') === FULL_HASH,
    );
    expect(hashNode).toBeDefined();
    expect(hashNode?.textContent?.trim().length).toBeLessThan(FULL_HASH.length);
    expect(hashNode?.textContent).toContain(FULL_HASH.slice(0, 12));
  });

  it('renders the source name, version, verification chain and raw content', async () => {
    const fixture = await render(detail());
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain('Spring Blog');
    expect(element.textContent).toContain('4.1.1');
    expect(element.textContent).toContain('Verified');
    expect(element.querySelector('app-verify-checks')).not.toBeNull();
    expect(element.textContent).toContain(
      'Spring Boot 4.1.1 has been released and is available from Maven Central.',
    );
  });
});
