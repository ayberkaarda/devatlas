/**
 * The view models every screen is written against.
 *
 * There is exactly one set of them. Both platform implementations map their
 * own source shape — local store rows on the desktop, REST payloads on the web
 * — into these types, so a component can never tell which one answered. Where
 * a field can only be populated by one of the two, it is either derived or
 * given an honest value rather than a plausible-looking lie.
 */

/** The four interface locales. Content locales are the same set. */
export type Locale = 'en' | 'tr' | 'fr' | 'de';

export const LOCALES: readonly Locale[] = ['en', 'tr', 'fr', 'de'] as const;

/** Stored theme preference. `SYSTEM` defers to the operating system. */
export type ThemePreference = 'LIGHT' | 'DARK' | 'SYSTEM';

/** What a theme preference resolves to once the system setting is read. */
export type ResolvedTheme = 'light' | 'dark';

export interface Preferences {
  readonly locale: Locale;
  readonly theme: ThemePreference;
}

/**
 * Two flags, and the bar for a third is high: a capability must have a
 * different value in the two implementations. A flag that is true everywhere
 * is dead configuration, and it invites a component to branch on something
 * that never varies — which is how platform knowledge leaks back in.
 */
export interface PlatformCapabilities {
  readonly canDownload: boolean;
  readonly hasLocalStore: boolean;
}

/**
 * How a translatable object resolved, carried per object because one payload
 * can mix translated and untranslated entities. The badge is rendered from
 * `isFallback`; a component never compares locale strings itself.
 */
export interface TranslationState {
  readonly locale: Locale;
  readonly requestedLocale: Locale;
  readonly isFallback: boolean;
}

/**
 * What is stored. Deliberately separate from what the queue is doing: a lesson
 * can be held locally *and* have a newer version *and* be downloading that
 * version right now, and a single enum forces a choice between three true
 * statements.
 */
export type Availability =
  'REMOTE' | 'NOT_DOWNLOADED' | 'DOWNLOADED' | 'UPDATE_AVAILABLE' | 'WITHDRAWN' | 'LOCAL_AHEAD';

/**
 * What the queue is doing. Mirrors the download queue's state machine exactly,
 * minus `DONE`: completion is expressed by `Availability`, not by a finished
 * transfer that lingers in the view model.
 */
export interface Transfer {
  readonly state: 'QUEUED' | 'DOWNLOADING' | 'VERIFYING' | 'PAUSED' | 'FAILED';
  readonly bytesDone: number;
  readonly bytesTotal: number;
  readonly attempts: number;
  readonly lastError: string | null;
}

export interface ContentAvailability {
  readonly availability: Availability;
  /** Null whenever nothing is in flight, and always null on the web. */
  readonly transfer: Transfer | null;
  /** Derived: `availability !== 'NOT_DOWNLOADED'`. */
  readonly readable: boolean;
}

/**
 * A track is a container, not a downloadable entity, so it gets its own set.
 * "Some of it" is a normal state for a track and means nothing for a lesson,
 * which is one package and is either held or not.
 */
export type TrackAvailability =
  'REMOTE' | 'NOT_DOWNLOADED' | 'PARTIALLY_DOWNLOADED' | 'DOWNLOADED' | 'UPDATE_AVAILABLE';

export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

export interface TrackSummary {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly description: string | null;
  readonly icon: string | null;
  readonly contentVersion: number;
  readonly lessonCount: number;
  /** Zero on the web, which stores nothing — an honest count, not a stand-in. */
  readonly downloadedLessonCount: number;
  readonly updateAvailableCount: number;
  readonly availability: TrackAvailability;
  readonly translation: TranslationState;
}

export interface LessonSummary {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly difficulty: Difficulty | null;
  readonly estimatedMinutes: number | null;
  readonly order: number;
  readonly availability: ContentAvailability;
  readonly translation: TranslationState;
}

export interface ModuleDetail {
  readonly id: string;
  readonly title: string;
  readonly order: number;
  readonly estimatedMinutes: number | null;
  readonly lessons: readonly LessonSummary[];
  readonly translation: TranslationState;
}

export interface TrackDetail {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly description: string | null;
  readonly icon: string | null;
  readonly contentVersion: number;
  readonly hasMindMap: boolean;
  readonly modules: readonly ModuleDetail[];
  readonly translation: TranslationState;
}

export interface CodeExample {
  readonly language: string;
  /**
   * Opaque source text, not markdown and not HTML-sanitized on the server. It
   * is rendered by the highlighter, which escapes it; it is never handed to
   * the DOM as markup.
   */
  readonly code: string;
  readonly caption: string | null;
  readonly order: number;
}

export interface Lesson {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly bodyMarkdown: string;
  readonly difficulty: Difficulty | null;
  readonly estimatedMinutes: number | null;
  readonly contentVersion: number;
  readonly codeExamples: readonly CodeExample[];
  readonly trackSlug: string | null;
  readonly trackTitle: string | null;
  readonly moduleTitle: string | null;
  readonly translation: TranslationState;
}

export interface MindMapNode {
  readonly id: string;
  readonly label: string;
  readonly lessonId: string | null;
  readonly children: readonly MindMapNode[];
}

export interface MindMap {
  readonly id: string;
  readonly trackId: string;
  readonly contentVersion: number;
  readonly root: MindMapNode;
  readonly translation: TranslationState;
}

export type EntityType = 'LESSON' | 'MIND_MAP';

export interface DeltaAnomaly {
  readonly entityId: string;
  readonly entityType: EntityType;
  readonly localVersion: number;
  readonly manifestVersion: number;
}

export interface DeltaSummary {
  readonly checkedTracks: number;
  readonly updatedEntities: number;
  readonly withdrawnEntities: number;
  /**
   * Counted apart from `updatedEntities` because updating a library must not
   * silently grow it: the badge reads "3 lessons updated", not "15 changes".
   */
  readonly newEntitiesAvailable: number;
  readonly anomalies: readonly DeltaAnomaly[];
}

export interface DownloadScope {
  readonly kind: 'LESSON' | 'MODULE' | 'TRACK';
  readonly id: string;
}

export interface BatchHandle {
  readonly batchId: string;
  /**
   * Only what actually entered the queue, so the denominator matches what the
   * user will watch happen.
   */
  readonly queuedEntities: number;
  readonly skippedEntities: number;
  readonly totalBytes: number;
}

export type QueueState = 'QUEUED' | 'DOWNLOADING' | 'VERIFYING' | 'DONE' | 'FAILED' | 'PAUSED';

export interface QueueEntry {
  readonly entityId: string;
  readonly entityType: EntityType;
  readonly title: string | null;
  readonly batchId: string;
  readonly state: QueueState;
  readonly receivedBytes: number;
  readonly totalBytes: number;
  readonly attempt: number;
  readonly pauseReason: string | null;
  readonly errorCode: string | null;
}

export interface BatchProgress {
  readonly batchId: string;
  readonly completedEntities: number;
  readonly totalEntities: number;
  readonly receivedBytes: number;
  readonly totalBytes: number;
}

export interface DownloadProgress {
  readonly entityId: string;
  readonly entityType: EntityType;
  readonly state: QueueState;
  readonly receivedBytes: number;
  readonly totalBytes: number;
  readonly attempt: number;
  readonly batch: BatchProgress;
  readonly errorCode: string | null;
}

export interface ProgressEntry {
  readonly lessonId: string;
  /**
   * Null is a value, not an absence: it records "explicitly marked
   * incomplete", which is a real user action and has to survive a sync.
   */
  readonly completedAt: string | null;
  readonly clientUpdatedAt: string;
}

export type BlogSource = 'MANUAL' | 'AUTO';

export interface BlogListQuery {
  readonly page?: number;
  readonly size?: number;
  readonly source?: BlogSource;
}

export interface BlogPostSummary {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly excerpt: string;
  readonly source: BlogSource;
  readonly sourceUrl: string | null;
  readonly publishedAt: string;
  readonly translation: TranslationState;
}

export interface BlogPost {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly bodyMarkdown: string;
  readonly source: BlogSource;
  readonly sourceUrl: string | null;
  readonly publishedAt: string;
  readonly translation: TranslationState;
}

export interface Page<T> {
  readonly items: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
}

/** Derives the `readable` axis so no caller has to remember the rule. */
export function contentAvailability(
  availability: Availability,
  transfer: Transfer | null = null,
): ContentAvailability {
  return {
    availability,
    transfer,
    readable: availability !== 'NOT_DOWNLOADED',
  };
}
