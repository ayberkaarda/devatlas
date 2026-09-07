import { Observable } from 'rxjs';

import type {
  BatchHandle,
  BlogListQuery,
  BlogPost,
  BlogPostSummary,
  DeltaSummary,
  DownloadProgress,
  DownloadScope,
  Lesson,
  MindMap,
  Page,
  PendingProgress,
  PlatformCapabilities,
  Preferences,
  ProgressEntry,
  ProgressSyncResult,
  QueueEntry,
  RememberedSession,
  SyncBookkeeping,
  TrackDetail,
  TrackSummary,
} from './models';

/**
 * The one thing a component knows about where it is running: that there is a
 * service, and it has the data.
 *
 * Every member is abstract and none is optional. Adding a method is therefore
 * a change to three files at once — this one and both implementations — and
 * the compiler refuses anything less. An optional member would let one
 * implementation quietly lack a feature and fail at runtime instead of at
 * build time.
 *
 * A component that needs to know whether it may offer a download asks
 * `capabilities`; one that needs to know whether content can be read asks the
 * view model. Neither ever asks which implementation it was given.
 */
export abstract class PlatformService {
  abstract readonly capabilities: PlatformCapabilities;

  // Content, read
  //
  // These address content by its slug, not by its identifier. The public read
  // API exposes no route that reaches a lesson by identifier, so a
  // identifier-keyed read is implementable against a local store and not
  // against the network — and a shape only one side can satisfy is not an
  // abstraction. Slugs are the human-facing identifier, they are globally
  // unique for lessons, and they are what a deep link carries.
  //
  // Downloads keep using identifiers: a download scope names an entity in the
  // manifest, and the manifest is keyed by identifier. Both are present on
  // every view model, so neither caller has to convert.
  abstract listTracks(): Promise<TrackSummary[]>;
  abstract getTrack(trackSlug: string): Promise<TrackDetail>;
  abstract getLesson(lessonSlug: string): Promise<Lesson>;
  abstract getMindMap(trackSlug: string): Promise<MindMap>;

  // Library management — meaningful only where capabilities.canDownload
  abstract refreshLibrary(trackId?: string): Promise<DeltaSummary>;
  abstract enqueueDownload(scope: DownloadScope): Promise<BatchHandle>;
  abstract pauseDownloads(batchId?: string): Promise<void>;
  abstract resumeDownloads(batchId?: string): Promise<void>;
  abstract cancelDownload(batchId: string): Promise<void>;
  abstract retryDownload(entityId?: string): Promise<void>;
  abstract queueState(): Promise<QueueEntry[]>;
  abstract deleteLocal(scope: DownloadScope): Promise<void>;
  abstract downloadProgress(): Observable<DownloadProgress>;

  // Progress
  abstract markProgress(lessonId: string, completed: boolean): Promise<void>;
  abstract listProgress(): Promise<ProgressEntry[]>;

  // Progress sync — meaningful only where capabilities.hasLocalStore.
  //
  // On the web `markProgress` already *is* the sync: it posts a single-item
  // batch and returns once the server has it, so there is no local queue and
  // "what is pending" has no honest answer. Both methods throw
  // `UnsupportedOnWebError` there, following the same rule the library
  // methods do — a silent empty array would be indistinguishable from
  // "there is genuinely nothing pending", and that would let a bug ship
  // looking like a legitimate empty state. The service that drains the queue
  // therefore runs only where `capabilities.hasLocalStore`.
  abstract pendingProgress(): Promise<PendingProgress[]>;
  abstract applyProgressResults(results: readonly ProgressSyncResult[]): Promise<void>;

  /**
   * Writes rows pulled from the server into the local replica.
   *
   * This is the pull direction's landing place. Every other local write path
   * either stamps the current time — which is what a person clicking
   * "complete" means — or only updates a row that already exists, so a pulled
   * row for a lesson this installation has never completed had nowhere to go,
   * and a completion made on one device stayed invisible on another until
   * that device happened to complete the lesson itself.
   *
   * It takes progress entries rather than a type of its own: a row pulled
   * from the server is a progress entry, and a second identical shape would
   * only invite the two to drift. The conflict rule applied per row is stated
   * once, on the desktop command that performs the write, so the two sides
   * cannot disagree about it.
   */
  abstract absorbProgress(entries: readonly ProgressEntry[]): Promise<void>;

  // Session persistence — what this device remembers across a restart.
  //
  // `forgetSession` is deliberately not named `clearSession`: the in-memory
  // session state already has a method by that name, and the two are not the
  // same act. Signing out clears both; a rejected token refresh clears only
  // the in-memory state while a re-login from what the device remembers is
  // still possible.
  abstract loadSession(): Promise<RememberedSession | null>;
  abstract storeSession(session: RememberedSession): Promise<void>;
  abstract forgetSession(): Promise<void>;

  // Sync bookkeeping — durable state the sync layer owns and has nowhere
  // else to keep, honoured on both platforms. It is not a preference: a
  // preference is something a person chose, and these two fields are things
  // the sync layer knows.
  abstract getSyncState(): Promise<SyncBookkeeping>;
  abstract setSyncState(patch: Partial<SyncBookkeeping>): Promise<void>;

  // Blog — read over HTTP on both platforms
  abstract listBlogPosts(query: BlogListQuery): Promise<Page<BlogPostSummary>>;
  abstract getBlogPost(slug: string): Promise<BlogPost>;

  // Preferences
  abstract getPreferences(): Promise<Preferences>;
  abstract setPreferences(patch: Partial<Preferences>): Promise<void>;

  /**
   * Makes the application visible, once and only once, after the theme is on
   * the document.
   *
   * This is not part of the original abstraction and is an addition to it. It
   * exists because the desktop window is created hidden so that nothing is
   * painted before the theme is applied, and something has to reveal it. The
   * alternative — a component calling the desktop command directly — is
   * exactly the leak the abstraction exists to prevent, and the rule for that
   * situation is that the abstraction is missing a method rather than that the
   * component may branch.
   *
   * On the web it is a satisfied no-op: the document is already visible and
   * the pre-paint script has already applied the theme. That is an honest
   * value, not an empty success standing in for an unsupported operation.
   */
  abstract revealApplication(): Promise<void>;
}
