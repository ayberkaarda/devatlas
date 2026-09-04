/**
 * The REST payloads exactly as they arrive on the wire.
 *
 * They are `snake_case` because the API is, and they are declared separately
 * from the view models so that the mapping between the two is a visible, typed
 * step in one place rather than an assumption spread across call sites. The
 * desktop command surface uses `camelCase` for the same shapes; forcing one
 * convention on both would mean fighting one framework or the other.
 */
import type { Availability, BlogSource, Difficulty, Locale, TranslationState } from './models';

/** Fields every translatable object in a response carries. */
export interface WireTranslated {
  readonly locale: string;
  readonly requested_locale: string;
  readonly is_fallback: boolean;
}

export interface WirePage<T> {
  readonly items: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly total_elements: number;
  readonly total_pages: number;
}

export interface WireTrackSummary extends WireTranslated {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly description: string | null;
  readonly icon: string | null;
  readonly order: number;
  readonly module_count: number;
  readonly lesson_count: number;
  readonly content_version: number;
  readonly updated_at: string;
}

export interface WireLessonSummary extends WireTranslated {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly difficulty: Difficulty | null;
  readonly estimated_minutes: number | null;
  readonly order: number;
  readonly content_version: number;
  readonly updated_at: string;
}

export interface WireModule extends WireTranslated {
  readonly id: string;
  readonly title: string;
  readonly order: number;
  readonly estimated_minutes: number | null;
  readonly lessons: readonly WireLessonSummary[];
}

export interface WireTrackDetail extends WireTranslated {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly description: string | null;
  readonly icon: string | null;
  readonly order: number;
  readonly content_version: number;
  readonly has_mind_map: boolean;
  readonly updated_at: string;
  readonly modules: readonly WireModule[];
}

export interface WireCodeExample {
  readonly id: string;
  readonly language: string;
  readonly code: string;
  readonly caption: string | null;
  readonly order: number;
}

export interface WireLesson extends WireTranslated {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly body_markdown: string;
  readonly difficulty: Difficulty | null;
  readonly estimated_minutes: number | null;
  readonly order: number;
  readonly content_version: number;
  readonly updated_at: string;
  readonly module: { readonly id: string; readonly title: string; readonly order: number } | null;
  readonly track: {
    readonly id: string;
    readonly slug: string;
    readonly title: string;
  } | null;
  readonly code_examples: readonly WireCodeExample[];
  readonly progress: { readonly completed_at: string | null } | null;
}

export interface WireMindMapNode {
  readonly id: string;
  readonly label: string;
  readonly lesson_id: string | null;
  readonly children: readonly WireMindMapNode[];
}

export interface WireMindMap extends WireTranslated {
  readonly id: string;
  readonly track_id: string;
  readonly content_version: number;
  readonly updated_at: string;
  readonly root: WireMindMapNode;
}

export interface WireBlogPostSummary extends WireTranslated {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly excerpt: string;
  readonly source: BlogSource;
  readonly source_url: string | null;
  readonly published_at: string;
}

export interface WireBlogPost extends WireTranslated {
  readonly id: string;
  readonly slug: string;
  readonly title: string;
  readonly body_markdown: string;
  readonly source: BlogSource;
  readonly source_url: string | null;
  readonly published_at: string;
  readonly updated_at: string;
}

export interface WireProgressItem {
  readonly lesson_id: string;
  readonly completed_at: string | null;
  readonly client_updated_at: string;
  readonly updated_at?: string;
}

export interface WireProgressPage extends WirePage<WireProgressItem> {
  readonly server_time: string;
}

export interface WireError {
  readonly code?: string;
  readonly message?: string;
}

const SUPPORTED: readonly string[] = ['en', 'tr', 'fr', 'de'];

/**
 * Narrows a wire locale string, defaulting to English rather than throwing.
 *
 * A locale the client does not recognise is a reason to render English text,
 * not a reason to fail a page that is otherwise perfectly readable.
 */
export function asLocale(value: string): Locale {
  return SUPPORTED.includes(value) ? (value as Locale) : 'en';
}

export function translationOf(wire: WireTranslated): TranslationState {
  return {
    locale: asLocale(wire.locale),
    requestedLocale: asLocale(wire.requested_locale),
    isFallback: wire.is_fallback,
  };
}

/**
 * Everything the web serves is served live and stored nowhere, so this is the
 * only availability it can honestly report. Calling it `DOWNLOADED` would be a
 * lie that some component eventually believes.
 */
export const REMOTE_AVAILABILITY: Availability = 'REMOTE';
