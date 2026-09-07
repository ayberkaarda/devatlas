import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, firstValueFrom } from 'rxjs';

import { API_BASE_URL, toPlatformError } from './api';
import { BlogApiClient } from './blog-api.client';
import { PlatformError, UnsupportedOnWebError } from './errors';
import {
  contentAvailability,
  type BatchHandle,
  type BlogListQuery,
  type BlogPost,
  type BlogPostSummary,
  type DeltaSummary,
  type DownloadProgress,
  type Lesson,
  type Locale,
  type MindMap,
  type MindMapNode,
  type Page,
  type PendingProgress,
  type PlatformCapabilities,
  type Preferences,
  type ProgressEntry,
  type QueueEntry,
  type RememberedSession,
  type SyncBookkeeping,
  type ThemePreference,
  type TrackDetail,
  type TrackSummary,
} from './models';
import { PlatformService } from './platform.service';
import { isoNow } from './timestamps';
import {
  translationOf,
  type WireLesson,
  type WireMindMap,
  type WireMindMapNode,
  type WirePage,
  type WireProgressPage,
  type WireTrackDetail,
  type WireTrackSummary,
} from './rest-wire';

/** Where the web build keeps its preferences. Read by the pre-paint script. */
export const PREFERENCES_STORAGE_KEY = 'bytelore.preferences';

/**
 * Where the web build remembers that a browser has signed in before.
 *
 * This row is never a credential. The refresh token travels on the cookie
 * channel and is already out of a script's reach, so there is nothing to
 * store and nothing to hand back for it; what is written here is `userId`
 * alone, and only to answer "has anyone signed in on this browser", which is
 * what decides whether a refresh is worth attempting at all.
 */
export const SESSION_STORAGE_KEY = 'bytelore.session';

/** Where the web build keeps the two sync bookkeeping fields. */
export const SYNC_STATE_STORAGE_KEY = 'bytelore.sync-state';

const DEFAULT_PREFERENCES: Preferences = { locale: 'en', theme: 'SYSTEM' };

const DEFAULT_SYNC_STATE: SyncBookkeeping = { preferencesDirtyAt: null, lastSyncAt: null };

/**
 * A page size large enough that the screens in this build never paginate, and
 * small enough to stay inside the API's own ceiling of 100.
 */
const FULL_PAGE_SIZE = 100;

/**
 * The public web client: every read is a live request, and nothing is stored.
 *
 * Its availability answers are all `REMOTE`, which is the honest value — the
 * alternative, reporting content as downloaded when nothing is on disk, is a
 * lie that some component eventually believes. The library methods throw
 * rather than returning empty successes; in practice they are unreachable,
 * because the controls for a capability this platform does not have are never
 * rendered.
 */
@Injectable()
export class WebPlatformService extends PlatformService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly blog = inject(BlogApiClient);

  readonly capabilities: PlatformCapabilities = {
    canDownload: false,
    hasLocalStore: false,
  };

  async listTracks(): Promise<TrackSummary[]> {
    const params = new HttpParams().set('size', FULL_PAGE_SIZE).set('sort', 'order,asc');
    const page = await this.request<WirePage<WireTrackSummary>>('/tracks', params);
    return page.items.map((item) => ({
      id: item.id,
      slug: item.slug,
      title: item.title,
      description: item.description,
      icon: item.icon,
      contentVersion: item.content_version,
      lessonCount: item.lesson_count,
      // Nothing is stored, so nothing is downloaded. Zero is the fact, not a
      // placeholder for a number this platform cannot compute.
      downloadedLessonCount: 0,
      updateAvailableCount: 0,
      availability: 'REMOTE',
      translation: translationOf(item),
    }));
  }

  async getTrack(trackSlug: string): Promise<TrackDetail> {
    const track = await this.request<WireTrackDetail>(`/tracks/${encodeURIComponent(trackSlug)}`);
    return {
      id: track.id,
      slug: track.slug,
      title: track.title,
      description: track.description,
      icon: track.icon,
      contentVersion: track.content_version,
      // Served live, like everything else here, and with no identifier: the
      // read API addresses a mind map by its track's slug and never exposes
      // one.
      mindMap: track.has_mind_map
        ? { id: null, availability: contentAvailability('REMOTE'), sizeBytes: null }
        : null,
      translation: translationOf(track),
      modules: track.modules.map((module) => ({
        id: module.id,
        title: module.title,
        order: module.order,
        estimatedMinutes: module.estimated_minutes,
        translation: translationOf(module),
        lessons: module.lessons.map((lesson) => ({
          id: lesson.id,
          slug: lesson.slug,
          title: lesson.title,
          difficulty: lesson.difficulty,
          estimatedMinutes: lesson.estimated_minutes,
          order: lesson.order,
          availability: contentAvailability('REMOTE'),
          translation: translationOf(lesson),
        })),
      })),
    };
  }

  async getLesson(lessonSlug: string): Promise<Lesson> {
    const lesson = await this.request<WireLesson>(`/lessons/${encodeURIComponent(lessonSlug)}`);
    return {
      id: lesson.id,
      slug: lesson.slug,
      title: lesson.title,
      bodyMarkdown: lesson.body_markdown,
      difficulty: lesson.difficulty,
      estimatedMinutes: lesson.estimated_minutes,
      contentVersion: lesson.content_version,
      trackSlug: lesson.track?.slug ?? null,
      trackTitle: lesson.track?.title ?? null,
      moduleTitle: lesson.module?.title ?? null,
      // The envelope is absent for a caller with no session and for one who
      // has not finished the lesson, and both mean the same thing to a
      // screen: there is no completion of this reader's to show.
      completedAt: lesson.progress?.completed_at ?? null,
      translation: translationOf(lesson),
      codeExamples: lesson.code_examples.map((example) => ({
        language: example.language,
        code: example.code,
        caption: example.caption,
        order: example.order,
      })),
    };
  }

  async getMindMap(trackSlug: string): Promise<MindMap> {
    const map = await this.request<WireMindMap>(`/tracks/${encodeURIComponent(trackSlug)}/mindmap`);
    return {
      id: map.id,
      trackId: map.track_id,
      contentVersion: map.content_version,
      root: toMindMapNode(map.root),
      translation: translationOf(map),
    };
  }

  refreshLibrary(): Promise<DeltaSummary> {
    return Promise.reject(new UnsupportedOnWebError('refreshLibrary'));
  }

  enqueueDownload(): Promise<BatchHandle> {
    return Promise.reject(new UnsupportedOnWebError('enqueueDownload'));
  }

  pauseDownloads(): Promise<void> {
    return Promise.reject(new UnsupportedOnWebError('pauseDownloads'));
  }

  resumeDownloads(): Promise<void> {
    return Promise.reject(new UnsupportedOnWebError('resumeDownloads'));
  }

  cancelDownload(): Promise<void> {
    return Promise.reject(new UnsupportedOnWebError('cancelDownload'));
  }

  retryDownload(): Promise<void> {
    return Promise.reject(new UnsupportedOnWebError('retryDownload'));
  }

  queueState(): Promise<QueueEntry[]> {
    return Promise.reject(new UnsupportedOnWebError('queueState'));
  }

  deleteLocal(): Promise<void> {
    return Promise.reject(new UnsupportedOnWebError('deleteLocal'));
  }

  /**
   * A stream that never emits rather than one that errors: this is a
   * subscription a shell may open once at startup, and an error would have to
   * be handled by a component whose whole point is not to know that there is
   * nothing to handle. Nothing is downloading, and nothing ever will be.
   */
  downloadProgress(): Observable<DownloadProgress> {
    return EMPTY;
  }

  async markProgress(lessonId: string, completed: boolean): Promise<void> {
    const now = isoNow();
    try {
      await firstValueFrom(
        this.http.post(`${this.baseUrl}/sync/progress`, {
          items: [
            {
              lesson_id: lessonId,
              // Null is the un-completed state, a real user action, rather
              // than the absence of a value.
              completed_at: completed ? now : null,
              client_updated_at: now,
            },
          ],
        }),
      );
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  async listProgress(): Promise<ProgressEntry[]> {
    const params = new HttpParams().set('size', FULL_PAGE_SIZE);
    const page = await this.request<WireProgressPage>('/sync/progress', params);
    return page.items.map((item) => ({
      lessonId: item.lesson_id,
      completedAt: item.completed_at,
      clientUpdatedAt: item.client_updated_at,
    }));
  }

  /**
   * On the web `markProgress` already is the sync — it posts a single-item
   * batch and returns once the server has it, so there is no local queue and
   * "what is pending" has no honest answer. Throwing here rather than
   * returning `[]` is never actually reached: the service that drains a
   * pending queue only runs where `capabilities.hasLocalStore`.
   */
  pendingProgress(): Promise<PendingProgress[]> {
    return Promise.reject(new UnsupportedOnWebError('pendingProgress'));
  }

  applyProgressResults(): Promise<void> {
    return Promise.reject(new UnsupportedOnWebError('applyProgressResults'));
  }

  /**
   * There is no replica to absorb a pulled row into. Accepting the call and
   * discarding the rows would report a success for a write that never
   * happened, which is exactly the silent empty success the platform rules
   * forbid.
   */
  absorbProgress(): Promise<void> {
    return Promise.reject(new UnsupportedOnWebError('absorbProgress'));
  }

  /**
   * Reads whatever this browser remembers about a prior sign-in.
   *
   * A row with no usable `userId` is treated the same as no row at all: it
   * is not a session worth acting on, and returning `null` here rather than
   * a half-formed object keeps that decision in one place instead of at
   * every caller.
   */
  async loadSession(): Promise<RememberedSession | null> {
    try {
      const raw = localStorage.getItem(SESSION_STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as { userId?: unknown };
      if (typeof parsed.userId !== 'string' || parsed.userId.length === 0) {
        return null;
      }
      return {
        userId: parsed.userId,
        accessToken: null,
        refreshToken: null,
        accessTokenExpiresAt: null,
      };
    } catch {
      return null;
    }
  }

  /**
   * Writes `userId` and nothing else. Storing a token here would undo the
   * entire reason the cookie channel exists, so the two token fields on
   * `session` are never read, whatever a caller happens to pass.
   */
  async storeSession(session: RememberedSession): Promise<void> {
    try {
      localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ userId: session.userId }));
    } catch {
      // A session that could not be remembered simply is not: the next load
      // finds nothing and behaves like an anonymous visitor, which is the
      // safe direction to fail in.
    }
  }

  async forgetSession(): Promise<void> {
    try {
      localStorage.removeItem(SESSION_STORAGE_KEY);
    } catch {
      // Nothing to reconcile: storage that cannot be written also cannot be
      // holding a stale session.
    }
  }

  /**
   * Guarded exactly like preferences: a browser that refuses to remember
   * these two timestamps is not a reason to fail sync, only to repeat work
   * it would otherwise have skipped.
   */
  async getSyncState(): Promise<SyncBookkeeping> {
    try {
      const raw = localStorage.getItem(SYNC_STATE_STORAGE_KEY);
      if (!raw) {
        return DEFAULT_SYNC_STATE;
      }
      const parsed = JSON.parse(raw) as Partial<SyncBookkeeping>;
      return {
        preferencesDirtyAt: narrowNullableString(parsed.preferencesDirtyAt),
        lastSyncAt: narrowNullableString(parsed.lastSyncAt),
      };
    } catch {
      return DEFAULT_SYNC_STATE;
    }
  }

  async setSyncState(patch: Partial<SyncBookkeeping>): Promise<void> {
    const current = await this.getSyncState();
    const next: SyncBookkeeping = {
      preferencesDirtyAt:
        patch.preferencesDirtyAt !== undefined
          ? patch.preferencesDirtyAt
          : current.preferencesDirtyAt,
      lastSyncAt: patch.lastSyncAt !== undefined ? patch.lastSyncAt : current.lastSyncAt,
    };
    try {
      localStorage.setItem(SYNC_STATE_STORAGE_KEY, JSON.stringify(next));
    } catch {
      // As with preferences: a value that cannot be persisted is lost for the
      // next load rather than failing the caller that just wanted it applied.
    }
  }

  listBlogPosts(query: BlogListQuery): Promise<Page<BlogPostSummary>> {
    return this.blog.list(query);
  }

  getBlogPost(slug: string): Promise<BlogPost> {
    return this.blog.get(slug);
  }

  /**
   * Preferences live in web storage, which is also where the pre-paint script
   * reads them from — one key, one shape, so the two cannot drift apart.
   *
   * Every access is guarded because storage throws rather than returning null
   * in a browser configured to block site data, and a browser that refuses to
   * remember a theme is not a reason to refuse to start.
   */
  async getPreferences(): Promise<Preferences> {
    try {
      const raw = localStorage.getItem(PREFERENCES_STORAGE_KEY);
      if (!raw) {
        return DEFAULT_PREFERENCES;
      }
      const parsed = JSON.parse(raw) as Partial<Preferences>;
      return {
        locale: narrowLocale(parsed.locale) ?? DEFAULT_PREFERENCES.locale,
        theme: narrowTheme(parsed.theme) ?? DEFAULT_PREFERENCES.theme,
      };
    } catch {
      return DEFAULT_PREFERENCES;
    }
  }

  async setPreferences(patch: Partial<Preferences>): Promise<void> {
    const current = await this.getPreferences();
    const next: Preferences = {
      locale: narrowLocale(patch.locale) ?? current.locale,
      theme: narrowTheme(patch.theme) ?? current.theme,
    };
    try {
      localStorage.setItem(PREFERENCES_STORAGE_KEY, JSON.stringify(next));
    } catch {
      // A preference that could not be remembered is still applied for this
      // session. Failing the call would turn a storage restriction into a
      // broken theme switch.
    }
  }

  /**
   * Already satisfied. The document is visible and the pre-paint script has
   * already put the theme on it, so there is nothing left to reveal.
   */
  async revealApplication(): Promise<void> {
    return;
  }

  private async request<T>(path: string, params?: HttpParams): Promise<T> {
    try {
      return await firstValueFrom(this.http.get<T>(`${this.baseUrl}${path}`, { params }));
    } catch (error) {
      throw toPlatformError(error);
    }
  }
}

function toMindMapNode(node: WireMindMapNode): MindMapNode {
  return {
    id: node.id,
    label: node.label,
    lessonId: node.lesson_id,
    children: node.children.map(toMindMapNode),
  };
}

function narrowNullableString(value: unknown): string | null {
  return typeof value === 'string' ? value : null;
}

function narrowLocale(value: unknown): Locale | null {
  return value === 'en' || value === 'tr' || value === 'fr' || value === 'de' ? value : null;
}

function narrowTheme(value: unknown): ThemePreference | null {
  return value === 'LIGHT' || value === 'DARK' || value === 'SYSTEM' ? value : null;
}

/** Re-exported so callers can narrow an error without importing two modules. */
export { PlatformError, UnsupportedOnWebError };
