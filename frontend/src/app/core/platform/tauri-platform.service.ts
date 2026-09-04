import { Injectable, inject } from '@angular/core';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { Observable } from 'rxjs';

import { ActiveLocale } from '../i18n/active-locale';
import { BlogApiClient } from './blog-api.client';
import { PlatformError } from './errors';
import {
  contentAvailability,
  type Availability,
  type BatchHandle,
  type BlogListQuery,
  type BlogPost,
  type BlogPostSummary,
  type DeltaSummary,
  type Difficulty,
  type DownloadProgress,
  type DownloadScope,
  type Lesson,
  type Locale,
  type MindMap,
  type MindMapNode,
  type Page,
  type PlatformCapabilities,
  type Preferences,
  type ProgressEntry,
  type QueueEntry,
  type ThemePreference,
  type TrackAvailability,
  type TrackDetail,
  type TrackSummary,
  type TranslationState,
  type Transfer,
} from './models';
import { PlatformService } from './platform.service';

/*
 * This is the only file in the application that touches a desktop API. That is
 * not a convention, it is the thing the whole abstraction exists to enforce:
 * every other module can be compiled, run and reasoned about without knowing a
 * desktop runtime exists, and the web build failing would be the proof that
 * something leaked out of here.
 *
 * Everything below the imports is mapping. The desktop surface speaks
 * camelCase and returns store rows; the view models are the same on both
 * platforms, so the conversion happens here, once per shape.
 */

/** The desktop payloads exactly as the command surface returns them. */
interface IpcTrackSummary {
  trackId: string;
  slug: string;
  title: string;
  description: string | null;
  icon: string | null;
  contentVersion: number;
  lessonCount: number;
  downloadedLessonCount: number;
  totalSizeBytes: number;
  downloadedSizeBytes: number;
  availability: TrackAvailability;
  updateAvailableCount: number;
  withdrawnCount: number;
  locale?: string;
  isFallback?: boolean;
}

interface IpcLessonSummary {
  lessonId: string;
  slug: string;
  title: string;
  difficulty: string | null;
  estimatedMinutes: number | null;
  order: number;
  availability: Availability;
  contentVersion: number | null;
  sizeBytes: number | null;
  locale?: string;
  isFallback?: boolean;
}

interface IpcModuleDetail {
  moduleId: string;
  title: string;
  order: number;
  estimatedMinutes: number | null;
  lessons: IpcLessonSummary[];
  locale?: string;
  isFallback?: boolean;
}

interface IpcMindMapSummary {
  mindMapId: string;
  availability: Availability;
  contentVersion: number | null;
  sizeBytes: number | null;
}

interface IpcTrackDetail {
  trackId: string;
  slug: string;
  title: string;
  description: string | null;
  icon: string | null;
  contentVersion: number;
  modules: IpcModuleDetail[];
  mindMap: IpcMindMapSummary | null;
  locale?: string;
  isFallback?: boolean;
}

interface IpcCodeExample {
  caption: string | null;
  code: string;
  language: string;
  order: number;
}

interface IpcLesson {
  lessonId: string;
  trackId: string;
  moduleId: string;
  slug: string;
  title: string;
  bodyMarkdown: string;
  difficulty: string | null;
  estimatedMinutes: number | null;
  order: number;
  contentVersion: number;
  locale: string;
  isFallback: boolean;
  codeExamples: IpcCodeExample[];
}

/**
 * The node tree as the content package carried it, which is the shape the read
 * API defines and therefore uses `snake_case` even here.
 */
interface IpcMindMapNode {
  id: string;
  label: string;
  lesson_id: string | null;
  children: IpcMindMapNode[];
}

interface IpcMindMap {
  mindMapId: string;
  trackId: string;
  contentVersion: number;
  root: IpcMindMapNode;
}

interface IpcQueueEntry {
  entityId: string;
  entityType: 'LESSON' | 'MIND_MAP';
  title: string | null;
  batchId: string;
  state: 'QUEUED' | 'DOWNLOADING' | 'VERIFYING' | 'DONE' | 'FAILED' | 'PAUSED';
  receivedBytes: number;
  totalBytes: number;
  attempt: number;
  pauseReason: string | null;
  errorCode: string | null;
}

interface IpcProgressEntry {
  lessonId: string;
  completedAt: string | null;
  clientUpdatedAt: string;
  syncState?: string;
}

interface IpcAppSettings {
  locale: string;
  theme: string;
  preferencesDirtyAt: string | null;
  lastSyncAt: string | null;
}

interface IpcCommandError {
  code: string;
  message: string;
  details?: unknown;
}

/**
 * The desktop client, reading a local replica.
 *
 * Content reads answer from the store and never fall back to the network, so a
 * caller always knows which source replied and an offline screen cannot
 * quietly become an online one. The blog is the exception and is not an
 * exception to that rule: blog posts are not replica content at all, and both
 * platforms read them live through the same shared client.
 */
@Injectable()
export class TauriPlatformService extends PlatformService {
  private readonly blog = inject(BlogApiClient);
  private readonly activeLocale = inject(ActiveLocale);

  readonly capabilities: PlatformCapabilities = {
    canDownload: true,
    hasLocalStore: true,
  };

  /**
   * Slug to identifier, learned from reads that have already happened.
   *
   * The store addresses content by identifier while the interface addresses it
   * by slug, and there is no command that translates between the two. Caching
   * what a read already revealed keeps the common navigation — track list,
   * track, lesson — at one command per screen instead of a rescan per hop.
   */
  private readonly trackIds = new Map<string, string>();
  private readonly lessonIds = new Map<string, string>();

  async listTracks(): Promise<TrackSummary[]> {
    const rows = await this.call<IpcTrackSummary[]>('library_list_tracks');
    const requested = this.activeLocale.value();
    return rows.map((row) => {
      this.trackIds.set(row.slug, row.trackId);
      return {
        id: row.trackId,
        slug: row.slug,
        title: row.title,
        description: row.description,
        icon: row.icon,
        contentVersion: row.contentVersion,
        lessonCount: row.lessonCount,
        downloadedLessonCount: row.downloadedLessonCount,
        updateAvailableCount: row.updateAvailableCount,
        availability: row.availability,
        translation: translationOf(row, requested),
      };
    });
  }

  async getTrack(trackSlug: string): Promise<TrackDetail> {
    const trackId = await this.resolveTrackId(trackSlug);
    const [detail, queue] = await Promise.all([
      this.call<IpcTrackDetail>('library_get_track', { trackId }),
      this.queueState(),
    ]);
    const transfers = new Map<string, Transfer>();
    for (const entry of queue) {
      const transfer = toTransfer(entry);
      if (transfer) {
        transfers.set(entry.entityId, transfer);
      }
    }

    const requested = this.activeLocale.value();
    return {
      id: detail.trackId,
      slug: detail.slug,
      title: detail.title,
      description: detail.description,
      icon: detail.icon,
      contentVersion: detail.contentVersion,
      hasMindMap: detail.mindMap !== null,
      translation: translationOf(detail, requested),
      modules: detail.modules.map((module) => ({
        id: module.moduleId,
        title: module.title,
        order: module.order,
        estimatedMinutes: module.estimatedMinutes,
        translation: translationOf(module, requested),
        lessons: module.lessons.map((lesson) => {
          this.lessonIds.set(lesson.slug, lesson.lessonId);
          return {
            id: lesson.lessonId,
            slug: lesson.slug,
            title: lesson.title,
            difficulty: narrowDifficulty(lesson.difficulty),
            estimatedMinutes: lesson.estimatedMinutes,
            order: lesson.order,
            // The two axes are joined here and nowhere else: what is stored
            // comes from the library read, what is in flight comes from the
            // queue, and a lesson can legitimately be both downloaded and
            // downloading a newer version.
            availability: contentAvailability(
              lesson.availability,
              transfers.get(lesson.lessonId) ?? null,
            ),
            translation: translationOf(lesson, requested),
          };
        }),
      })),
    };
  }

  async getLesson(lessonSlug: string): Promise<Lesson> {
    const lessonId = await this.resolveLessonId(lessonSlug);
    const lesson = await this.call<IpcLesson>('library_get_lesson', { lessonId });
    const requested = this.activeLocale.value();
    return {
      id: lesson.lessonId,
      slug: lesson.slug,
      title: lesson.title,
      bodyMarkdown: lesson.bodyMarkdown,
      difficulty: narrowDifficulty(lesson.difficulty),
      estimatedMinutes: lesson.estimatedMinutes,
      contentVersion: lesson.contentVersion,
      trackSlug: null,
      trackTitle: null,
      moduleTitle: null,
      translation: translationOf(lesson, requested),
      codeExamples: lesson.codeExamples.map((example) => ({
        language: example.language,
        code: example.code,
        caption: example.caption,
        order: example.order,
      })),
    };
  }

  async getMindMap(trackSlug: string): Promise<MindMap> {
    const trackId = await this.resolveTrackId(trackSlug);
    const map = await this.call<IpcMindMap>('library_get_mind_map', { trackId });
    return {
      id: map.mindMapId,
      trackId: map.trackId,
      contentVersion: map.contentVersion,
      root: toMindMapNode(map.root),
      // Mind map labels are not translated, so every locale resolves to the
      // canonical one and the same badge appears without a special case.
      translation: {
        locale: 'en',
        requestedLocale: this.activeLocale.value(),
        isFallback: this.activeLocale.value() !== 'en',
      },
    };
  }

  refreshLibrary(trackId?: string): Promise<DeltaSummary> {
    return this.call<DeltaSummary>('library_refresh', { trackId: trackId ?? null });
  }

  enqueueDownload(scope: DownloadScope): Promise<BatchHandle> {
    return this.call<BatchHandle>('download_enqueue', { scope });
  }

  async pauseDownloads(batchId?: string): Promise<void> {
    await this.call<null>('download_pause', { batchId: batchId ?? null });
  }

  async resumeDownloads(batchId?: string): Promise<void> {
    await this.call<null>('download_resume', { batchId: batchId ?? null });
  }

  async cancelDownload(batchId: string): Promise<void> {
    await this.call<unknown>('download_cancel', { batchId });
  }

  async retryDownload(entityId?: string): Promise<void> {
    await this.call<null>('download_retry', { entityId: entityId ?? null });
  }

  async queueState(): Promise<QueueEntry[]> {
    const rows = await this.call<IpcQueueEntry[]>('download_queue_state');
    return rows.map((row) => ({
      entityId: row.entityId,
      entityType: row.entityType,
      title: row.title,
      batchId: row.batchId,
      state: row.state,
      receivedBytes: row.receivedBytes,
      totalBytes: row.totalBytes,
      attempt: row.attempt,
      pauseReason: row.pauseReason,
      errorCode: row.errorCode,
    }));
  }

  async deleteLocal(scope: DownloadScope): Promise<void> {
    await this.call<unknown>('download_delete', { scope });
  }

  /**
   * Events, not polling. The queue emits at most one event per entity per
   * quarter second plus one on every state change, so a fast connection cannot
   * flood the interface; a screen opened mid-download reconciles by reading
   * the queue once and then following this stream, because events describe
   * changes and have no history to replay.
   */
  downloadProgress(): Observable<DownloadProgress> {
    return new Observable<DownloadProgress>((subscriber) => {
      let stop: (() => void) | null = null;
      let closed = false;

      listen<DownloadProgress>('download://progress', (event) => {
        subscriber.next(event.payload);
      })
        .then((unlisten) => {
          if (closed) {
            unlisten();
            return;
          }
          stop = unlisten;
        })
        .catch((error: unknown) => subscriber.error(toPlatformError(error)));

      return () => {
        closed = true;
        stop?.();
      };
    });
  }

  async markProgress(lessonId: string, completed: boolean): Promise<void> {
    await this.call<IpcProgressEntry>('progress_mark', { lessonId, completed });
  }

  async listProgress(): Promise<ProgressEntry[]> {
    const rows = await this.call<IpcProgressEntry[]>('progress_list');
    return rows.map((row) => ({
      lessonId: row.lessonId,
      completedAt: row.completedAt,
      clientUpdatedAt: row.clientUpdatedAt,
    }));
  }

  listBlogPosts(query: BlogListQuery): Promise<Page<BlogPostSummary>> {
    return this.blog.list(query);
  }

  getBlogPost(slug: string): Promise<BlogPost> {
    return this.blog.get(slug);
  }

  async getPreferences(): Promise<Preferences> {
    const settings = await this.call<IpcAppSettings>('settings_get');
    return {
      locale: narrowLocale(settings.locale) ?? 'en',
      theme: narrowTheme(settings.theme) ?? 'SYSTEM',
    };
  }

  async setPreferences(patch: Partial<Preferences>): Promise<void> {
    // A partial patch, so setting one preference does not require restating
    // the other and cannot overwrite it with a stale read.
    const payload: Record<string, unknown> = {};
    if (patch.locale !== undefined) {
      payload['locale'] = patch.locale;
    }
    if (patch.theme !== undefined) {
      payload['theme'] = patch.theme;
    }
    await this.call<IpcAppSettings>('settings_set', { patch: payload });
  }

  /**
   * The window is created hidden so that nothing is painted before the theme
   * is applied; this is the other half of that pair. Removing the hidden start
   * makes this call pointless, and removing this call leaves a window that
   * never appears.
   */
  async revealApplication(): Promise<void> {
    await this.call<null>('window_show');
  }

  private async call<T>(command: string, args?: Record<string, unknown>): Promise<T> {
    try {
      return await invoke<T>(command, args);
    } catch (error) {
      throw toPlatformError(error);
    }
  }

  private async resolveTrackId(trackSlug: string): Promise<string> {
    const known = this.trackIds.get(trackSlug);
    if (known) {
      return known;
    }
    await this.listTracks();
    const resolved = this.trackIds.get(trackSlug);
    if (!resolved) {
      throw new PlatformError(
        'TRACK_NOT_FOUND',
        `No track in the local library has the slug '${trackSlug}'.`,
        { trackSlug },
      );
    }
    return resolved;
  }

  private async resolveLessonId(lessonSlug: string): Promise<string> {
    const known = this.lessonIds.get(lessonSlug);
    if (known) {
      return known;
    }
    // A lesson slug is globally unique but the store is addressed per track,
    // so an unseen slug costs one walk of the library. Reads are local, and
    // every walk fills the cache for the rest of the session.
    const tracks = await this.listTracks();
    for (const track of tracks) {
      await this.getTrack(track.slug);
      const resolved = this.lessonIds.get(lessonSlug);
      if (resolved) {
        return resolved;
      }
    }
    throw new PlatformError(
      'ENTITY_NOT_IN_LIBRARY',
      `No lesson in the local library has the slug '${lessonSlug}'.`,
      { lessonSlug },
    );
  }
}

/**
 * The store resolves content locale before it answers, and only the lesson
 * payload reports how that resolution went. Where a payload says nothing, the
 * text is reported as being in the requested locale: claiming a fallback that
 * may not have happened would put a "not yet translated" badge on translated
 * content, which is a worse error than omitting one.
 */
function translationOf(
  row: { locale?: string; isFallback?: boolean },
  requested: Locale,
): TranslationState {
  return {
    locale: narrowLocale(row.locale) ?? requested,
    requestedLocale: requested,
    isFallback: row.isFallback ?? false,
  };
}

/**
 * The queue's terminal states are not transfers. `DONE` is expressed by the
 * availability axis instead, and a completed entity that lingered in the view
 * model as a finished transfer would be rendered twice.
 */
function toTransfer(entry: QueueEntry): Transfer | null {
  if (entry.state === 'DONE') {
    return null;
  }
  return {
    state: entry.state,
    bytesDone: entry.receivedBytes,
    bytesTotal: entry.totalBytes,
    attempts: entry.attempt,
    lastError: entry.errorCode,
  };
}

function toMindMapNode(node: IpcMindMapNode): MindMapNode {
  return {
    id: node.id,
    label: node.label,
    lessonId: node.lesson_id,
    children: (node.children ?? []).map(toMindMapNode),
  };
}

function narrowDifficulty(value: string | null): Difficulty | null {
  return value === 'BEGINNER' || value === 'INTERMEDIATE' || value === 'ADVANCED' ? value : null;
}

function narrowLocale(value: unknown): Locale | null {
  return value === 'en' || value === 'tr' || value === 'fr' || value === 'de' ? value : null;
}

function narrowTheme(value: unknown): ThemePreference | null {
  return value === 'LIGHT' || value === 'DARK' || value === 'SYSTEM' ? value : null;
}

/**
 * Command failures arrive as the same `{code, message, details}` shape the
 * REST API uses, so the interface has one error path rather than one per
 * transport. Codes the server raised during a download are passed through
 * unchanged.
 */
function toPlatformError(error: unknown): PlatformError {
  if (error instanceof PlatformError) {
    return error;
  }
  if (typeof error === 'object' && error !== null && 'code' in error) {
    const shaped = error as IpcCommandError;
    return new PlatformError(shaped.code, shaped.message ?? shaped.code, shaped.details);
  }
  return new PlatformError(
    'INTERNAL_ERROR',
    error instanceof Error ? error.message : String(error),
  );
}
