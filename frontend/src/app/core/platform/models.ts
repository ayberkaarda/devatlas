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

/**
 * A track's mind map, as a unit of content rather than a boolean.
 *
 * "Is there one" is the only question a boolean can answer, and it is the only
 * question the navigation link asks. Every control that offers to *acquire*
 * content needs the second axis as well: a track whose lessons are all held
 * locally but whose mind map is not is a normal state, and reducing the mind
 * map to one bit is what leaves such a track with content it can never be
 * asked to fetch.
 */
export interface MindMapSummary {
  /** Null on the web, where the read API addresses a mind map by track slug. */
  readonly id: string | null;
  readonly availability: ContentAvailability;
  readonly sizeBytes: number | null;
}

export interface TrackDetail {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly description: string | null;
  readonly icon: string | null;
  readonly contentVersion: number;
  readonly mindMap: MindMapSummary | null;
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
  /**
   * When this reader finished the lesson, or `null` when no completion is
   * recorded for them — which is also what a reader with no session gets,
   * because nobody's progress is theirs to show.
   *
   * It travels with the lesson rather than being fetched by the screen so
   * that both clients answer the question the same way while getting the
   * answer from different places: the web build reads it from the same
   * response as the lesson, and the desktop build reads it from the local
   * progress table, which is what lets a completed lesson still look
   * completed with no network and an expired session.
   */
  readonly completedAt: string | null;
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
  /**
   * Which locales the stored entity actually holds, base locale first and the
   * rest alphabetically. A package delivers a lesson and its translations
   * together, so "which language is this download in" has no single answer.
   * Empty until something is stored, and always empty where nothing is.
   */
  readonly locales: readonly string[];
  /**
   * The track this entity belongs to, and its title in the active locale
   * (with the usual fallback). A queue is a flat list, but the thing a user
   * removes to reclaim space is a track, and a screen that cannot group its
   * rows cannot offer that. `trackTitle` is null only when the track row
   * itself is missing from the store, which a queue row should never outlive.
   */
  readonly trackId: string | null;
  readonly trackTitle: string | null;
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

/** A progress row the server has not yet acknowledged. */
export interface PendingProgress {
  readonly lessonId: string;
  readonly completedAt: string | null;
  readonly clientUpdatedAt: string;
}

export type ProgressSyncStatus = 'APPLIED' | 'STALE' | 'REJECTED';

/**
 * What the server said about one row after a sync batch.
 *
 * `completedAt` is optional *and* nullable, and the two mean different
 * things: absent leaves the local completion state alone, `null` sets it to
 * "explicitly marked incomplete". A result that used `undefined` and `null`
 * interchangeably would silently un-complete a lesson on every `STALE` row,
 * so the field is written as `completedAt?: string | null` and a caller that
 * has no opinion on completion must omit the key rather than set it to
 * `undefined`.
 */
export interface ProgressSyncResult {
  readonly lessonId: string;
  readonly status: ProgressSyncStatus;
  readonly code: string | null;
  readonly serverClientUpdatedAt: string | null;
  readonly completedAt?: string | null;
}

/**
 * What a device remembers about a session across a restart.
 *
 * The three nullable fields are the difference between the platforms,
 * expressed as data rather than as a branch. The desktop signs in on a
 * channel where the refresh token arrives as a value the application holds,
 * so all four fields are populated there. The web signs in on a channel
 * where the refresh token is attached by the browser and never readable by a
 * script, so both token fields are `null` there, and `userId` is stored on
 * its own only to answer "has anyone signed in on this browser before" —
 * which is what decides whether attempting a refresh is worth it at all.
 */
export interface RememberedSession {
  readonly userId: string;
  readonly accessToken: string | null;
  readonly refreshToken: string | null;
  readonly accessTokenExpiresAt: string | null;
}

/**
 * Durable state the sync layer owns and has nowhere else to keep. Neither
 * field is a preference a person chose, which is why they live apart from
 * `Preferences` even though both are small and both survive a restart.
 */
export interface SyncBookkeeping {
  /** Stamped when a preference changes with no connectivity, cleared once the push succeeds. */
  readonly preferencesDirtyAt: string | null;
  /** The last time a sync batch was accepted, not the last time one was attempted. */
  readonly lastSyncAt: string | null;
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
