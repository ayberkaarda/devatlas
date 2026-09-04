package dev.devatlas.server.translation;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ApiFieldError;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.common.MarkdownSanitizer;
import dev.devatlas.server.common.TextNormalizer;
import dev.devatlas.server.common.UuidV7;
import dev.devatlas.server.content.ContentVersionService;
import dev.devatlas.server.domain.BlogPost;
import dev.devatlas.server.domain.ContentTranslation;
import dev.devatlas.server.domain.Lesson;
import dev.devatlas.server.domain.Module;
import dev.devatlas.server.domain.Track;
import dev.devatlas.server.domain.TranslationEntityType;
import dev.devatlas.server.domain.UserLocale;
import dev.devatlas.server.repository.BlogPostRepository;
import dev.devatlas.server.repository.ContentTranslationRepository;
import dev.devatlas.server.repository.LessonRepository;
import dev.devatlas.server.repository.ModuleRepository;
import dev.devatlas.server.repository.TrackRepository;
import dev.devatlas.server.translation.dto.TranslationCanonical;
import dev.devatlas.server.translation.dto.TranslationGetResponse;
import dev.devatlas.server.translation.dto.TranslationItem;
import dev.devatlas.server.translation.dto.TranslationUpsertRequest;
import dev.devatlas.server.translation.dto.TranslationWriteResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading and writing {@link ContentTranslation} rows (§5.6). {@code MIND_MAP} is not a member of
 * {@link TranslationEntityType} at all (§5.2.4), so an unparseable or unsupported {@code
 * entityType} path segment -- including literally {@code "MIND_MAP"} -- is rejected by the
 * controller before it ever reaches this class.
 */
@Service
public class TranslationService {

  private static final List<String> TRANSLATABLE_LOCALES = List.of("tr", "fr", "de");

  private final ContentTranslationRepository translations;
  private final TrackRepository tracks;
  private final ModuleRepository modules;
  private final LessonRepository lessons;
  private final BlogPostRepository blogPosts;
  private final ContentVersionService versions;
  private final Clock clock;

  public TranslationService(
      ContentTranslationRepository translations,
      TrackRepository tracks,
      ModuleRepository modules,
      LessonRepository lessons,
      BlogPostRepository blogPosts,
      ContentVersionService versions,
      Clock clock) {
    this.translations = translations;
    this.tracks = tracks;
    this.modules = modules;
    this.lessons = lessons;
    this.blogPosts = blogPosts;
    this.versions = versions;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public TranslationGetResponse get(TranslationEntityType entityType, UUID entityId) {
    TranslationCanonical canonical = canonicalTextFor(entityType, entityId);
    List<ContentTranslation> rows = translations.findByEntityTypeAndEntityId(entityType, entityId);
    List<TranslationItem> items =
        rows.stream()
            .map(
                row ->
                    new TranslationItem(
                        row.getLocale(),
                        row.getTitle(),
                        row.getBody(),
                        row.getUpdatedAt(),
                        row.getVersion()))
            .toList();
    List<String> presentLocales = rows.stream().map(ContentTranslation::getLocale).toList();
    List<String> missingLocales =
        TRANSLATABLE_LOCALES.stream().filter(l -> !presentLocales.contains(l)).toList();
    return new TranslationGetResponse(entityType, entityId, canonical, items, missingLocales);
  }

  /**
   * Whether {@link #upsert} created a new translation row (for the caller's {@code 201} vs {@code
   * 200}).
   */
  public record UpsertOutcome(TranslationWriteResponse response, boolean created) {}

  @Transactional
  public UpsertOutcome upsert(
      TranslationEntityType entityType,
      UUID entityId,
      String localeParam,
      TranslationUpsertRequest request) {
    UserLocale locale = requireTranslatableLocale(localeParam);
    requireEntityExists(entityType, entityId);
    requireBodyIfNeeded(entityType, request.body());

    Optional<ContentTranslation> existing =
        translations.findByEntityTypeAndEntityIdAndLocale(entityType, entityId, locale.code());
    if (existing.isPresent()) {
      if (request.version() == null) {
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED,
            "'version' is required when updating an existing translation.");
      }
      requireVersion(existing.get().getVersion(), request.version());
    }

    String title = TextNormalizer.normalize(request.title());
    String sanitizedBody = sanitizeBody(request.body());

    boolean creating = existing.isEmpty();
    Instant now = now();
    ContentTranslation row = existing.orElseGet(ContentTranslation::new);
    if (creating) {
      row.setId(UuidV7.randomUuid());
      row.setEntityType(entityType);
      row.setEntityId(entityId);
      row.setLocale(locale.code());
      row.setCreatedAt(now);
    }
    row.setTitle(title);
    row.setBody(sanitizedBody);
    row.setUpdatedAt(now);
    ContentTranslation saved = translations.save(row);

    Integer newContentVersion = bumpOwningEntity(entityType, entityId);

    TranslationWriteResponse response =
        new TranslationWriteResponse(
            saved.getLocale(),
            saved.getTitle(),
            saved.getBody(),
            saved.getUpdatedAt(),
            saved.getVersion(),
            newContentVersion);
    return new UpsertOutcome(response, creating);
  }

  @Transactional
  public void delete(TranslationEntityType entityType, UUID entityId, String localeParam) {
    UserLocale locale = requireTranslatableLocale(localeParam);
    requireEntityExists(entityType, entityId);
    ContentTranslation row =
        translations
            .findByEntityTypeAndEntityIdAndLocale(entityType, entityId, locale.code())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.TRANSLATION_NOT_FOUND, "No translation exists for that locale."));
    translations.delete(row);
    bumpOwningEntity(entityType, entityId);
  }

  private UserLocale requireTranslatableLocale(String localeParam) {
    UserLocale locale = UserLocale.fromCode(localeParam);
    if (locale == null) {
      throw new ApiException(
          ErrorCode.UNSUPPORTED_LOCALE,
          "Locale '%s' is not one of en, tr, fr, de.".formatted(localeParam));
    }
    if (locale == UserLocale.EN) {
      throw new ApiException(
          ErrorCode.CANONICAL_LOCALE_NOT_ALLOWED,
          "English is canonical and lives in the entity's own columns; it cannot be stored as a translation.");
    }
    return locale;
  }

  private void requireBodyIfNeeded(TranslationEntityType entityType, String body) {
    boolean required =
        entityType == TranslationEntityType.LESSON || entityType == TranslationEntityType.BLOG_POST;
    if (required && (body == null || body.isBlank())) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "'body' is required for %s translations.".formatted(entityType),
          List.of(new ApiFieldError("body", "REQUIRED", "must not be blank for " + entityType)));
    }
  }

  private String sanitizeBody(String body) {
    if (body == null) {
      return null;
    }
    String normalized = TextNormalizer.normalize(body);
    return MarkdownSanitizer.sanitizeMarkdown(normalized);
  }

  private void requireEntityExists(TranslationEntityType entityType, UUID entityId) {
    switch (entityType) {
      case TRACK -> requireTrack(entityId);
      case MODULE -> requireModule(entityId);
      case LESSON -> requireLesson(entityId);
      case BLOG_POST -> requireBlogPost(entityId);
    }
  }

  private TranslationCanonical canonicalTextFor(TranslationEntityType entityType, UUID entityId) {
    return switch (entityType) {
      case TRACK -> {
        Track track = requireTrack(entityId);
        yield TranslationCanonical.of(track.getTitle(), track.getDescription());
      }
      case MODULE -> {
        Module module = requireModule(entityId);
        yield TranslationCanonical.of(module.getTitle(), null);
      }
      case LESSON -> {
        Lesson lesson = requireLesson(entityId);
        yield TranslationCanonical.of(lesson.getTitle(), lesson.getBodyMarkdown());
      }
      case BLOG_POST -> {
        BlogPost post = requireBlogPost(entityId);
        yield TranslationCanonical.of(post.getTitle(), post.getBodyMarkdown());
      }
    };
  }

  /**
   * Bumps whatever {@code content_version} counter represents this translation's entity, per
   * §5.4.1: the translated entity itself for {@code TRACK}/{@code LESSON}, the owning track for
   * {@code MODULE} (a module has no counter of its own), and nothing for {@code BLOG_POST} (blog
   * posts are not packaged content and carry no {@code content_version} column at all).
   */
  private Integer bumpOwningEntity(TranslationEntityType entityType, UUID entityId) {
    return switch (entityType) {
      case TRACK -> versions.bumpTrack(entityId).getContentVersion();
      case MODULE -> versions.bumpTrack(requireModule(entityId).getTrackId()).getContentVersion();
      case LESSON -> versions.bumpLesson(requireLesson(entityId)).getContentVersion();
      case BLOG_POST -> null;
    };
  }

  private Track requireTrack(UUID id) {
    return tracks
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.TRACK_NOT_FOUND, "No track exists with id '%s'.".formatted(id)));
  }

  private Module requireModule(UUID id) {
    return modules
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.MODULE_NOT_FOUND, "No module exists with id '%s'.".formatted(id)));
  }

  private Lesson requireLesson(UUID id) {
    return lessons
        .findById(id)
        .filter(lesson -> lesson.getDeletedAt() == null)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.LESSON_NOT_FOUND, "No lesson exists with id '%s'.".formatted(id)));
  }

  private BlogPost requireBlogPost(UUID id) {
    return blogPosts
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.BLOG_POST_NOT_FOUND,
                    "No blog post exists with id '%s'.".formatted(id)));
  }

  private static void requireVersion(long actual, long expected) {
    if (actual != expected) {
      throw new ApiException(
          ErrorCode.VERSION_CONFLICT,
          "The resource was modified concurrently; re-read it and retry.");
    }
  }

  private Instant now() {
    return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
  }
}
