import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { environment } from '../environments/environment';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
    }).compileComponents();
  });

  it('creates the root component', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the application title inside an h1', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const heading = fixture.nativeElement.querySelector('h1') as HTMLElement | null;
    expect(heading?.textContent).toContain('DevAtlas');
  });

  it('exposes the build-time platform, which under Jest is the web target', () => {
    // Jest resolves environment.ts directly; the tauri variant is only ever
    // substituted by the build, so this asserts the default rather than the
    // replacement. It guards against the platform flag being dropped or
    // hard-coded to something other than the environment value.
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance as unknown as { platform: () => string };
    expect(app.platform()).toBe(environment.platform);
    expect(app.platform()).toBe('web');
  });
});
