package dev.bytelore.server.content.admin;

import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ApiFieldError;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.common.MarkdownSanitizer;
import dev.bytelore.server.common.TextNormalizer;
import dev.bytelore.server.common.UuidV7;
import dev.bytelore.server.content.ContentVersionService;
import dev.bytelore.server.content.OrderNormalizer;
import dev.bytelore.server.content.admin.dto.AdminCodeExampleResponse;
import dev.bytelore.server.content.admin.dto.AdminLessonResponse;
import dev.bytelore.server.content.admin.dto.CreateLessonRequest;
import dev.bytelore.server.content.admin.dto.UpdateLessonRequest;
import dev.bytelore.server.domain.CodeExample;
import dev.bytelore.server.domain.Lesson;
import dev.bytelore.server.domain.Module;
import dev.bytelore.server.repository.CodeExampleRepository;
import dev.bytelore.server.repository.LessonRepository;
import dev.bytelore.server.repository.ModuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoring for lessons (§5.4.4). Soft delete only -- see §2.10. */
@Service
public class AdminLessonService {

  private final LessonRepository lessons;
  private final ModuleRepository modules;
  private final CodeExampleRepository codeExamples;
  private final ContentVersionService versions;
  private final Clock clock;

  public AdminLessonService(
      LessonRepository lessons,
      ModuleRepository modules,
      CodeExampleRepository codeExamples,
      ContentVersionService versions,
      Clock clock) {
    this.lessons = lessons;
    this.modules = modules;
    this.codeExamples = codeExamples;
    this.versions = versions;
    this.clock = clock;
  }

  @Transactional
  public AdminLessonResponse create(UUID moduleId, CreateLessonRequest request) {
    requireModule(moduleId);
    if (lessons.existsBySlugAndDeletedAtIsNull(request.slug())) {
      throw new ApiException(
          ErrorCode.SLUG_ALREADY_EXISTS,
          "A lesson already exists with slug '%s'.".formatted(request.slug()));
    }
    String body = normalizeAndValidateBody(request.bodyMarkdown());

    Instant now = now();
    Lesson lesson = new Lesson();
    lesson.setId(UuidV7.randomUuid());
    lesson.setModuleId(moduleId);
    lesson.setSlug(request.slug());
    lesson.setTitle(TextNormalizer.normalize(request.title()));
    lesson.setBodyMarkdown(body);
    lesson.setDifficulty(request.difficulty());
    lesson.setEstimatedMinutes(request.estimatedMinutes());
    lesson.setContentVersion(1);
    lesson.setCreatedAt(now);
    lesson.setUpdatedAt(now);
    placeAmongSiblings(lesson, request.order());

    versions.repackageLesson(lesson);
    Lesson saved = lessons.save(lesson);
    versions.bumpTrack(versions.resolveTrackId(moduleId));

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public AdminLessonResponse get(UUID id) {
    return toResponse(requireLesson(id));
  }

  @Transactional
  public AdminLessonResponse update(UUID id, UpdateLessonRequest request) {
    Lesson lesson = requireLesson(id);
    requireVersion(lesson.getVersion(), request.version());

    UUID oldModuleId = lesson.getModuleId();
    boolean moved = request.moduleId() != null && !request.moduleId().equals(oldModuleId);
    boolean changed = false;

    if (request.slug() != null && !request.slug().equals(lesson.getSlug())) {
      if (lessons.existsBySlugAndDeletedAtIsNullAndIdNot(request.slug(), id)) {
        throw new ApiException(
            ErrorCode.SLUG_ALREADY_EXISTS,
            "A lesson already exists with slug '%s'.".formatted(request.slug()));
      }
      lesson.setSlug(request.slug());
      changed = true;
    }
    if (request.title() != null) {
      lesson.setTitle(TextNormalizer.normalize(request.title()));
      changed = true;
    }
    if (request.bodyMarkdown() != null) {
      lesson.setBodyMarkdown(normalizeAndValidateBody(request.bodyMarkdown()));
      changed = true;
    }
    if (request.difficulty() != null) {
      lesson.setDifficulty(request.difficulty());
      changed = true;
    }
    if (request.estimatedMinutes() != null) {
      lesson.setEstimatedMinutes(request.estimatedMinutes());
      changed = true;
    }
    if (moved) {
      requireModule(request.moduleId());
      lesson.setModuleId(request.moduleId());
      changed = true;
      placeAmongSiblings(lesson, request.order());
    } else if (request.order() != null) {
      placeAmongSiblings(lesson, request.order());
      changed = true;
    }

    lesson.setUpdatedAt(now());

    if (changed) {
      Lesson saved = versions.bumpLesson(lesson);
      if (moved) {
        compactLessonOrder(oldModuleId);
        versions.bumpTrack(versions.resolveTrackId(oldModuleId));
      }
      return toResponse(saved);
    }

    return toResponse(lessons.save(lesson));
  }

  @Transactional
  public void delete(UUID id) {
    Lesson lesson = requireLesson(id);
    UUID moduleId = lesson.getModuleId();
    lesson.setDeletedAt(now());
    lesson.setUpdatedAt(now());
    lessons.save(lesson);
    compactLessonOrder(moduleId);
    versions.bumpTrack(versions.resolveTrackId(moduleId));
  }

  @Transactional
  public List<AdminCodeExampleResponse> reorderCodeExamples(UUID lessonId, List<UUID> orderedIds) {
    Lesson lesson = requireLesson(lessonId);
    List<CodeExample> current = codeExamples.findByLessonIdOrderByDisplayOrderAsc(lessonId);
    List<UUID> currentIds = current.stream().map(CodeExample::getId).toList();
    OrderNormalizer.requireCompleteSet(currentIds, orderedIds);

    Map<UUID, CodeExample> byId = new HashMap<>();
    for (CodeExample example : current) {
      byId.put(example.getId(), example);
    }
    Instant now = now();
    for (int i = 0; i < orderedIds.size(); i++) {
      CodeExample example = byId.get(orderedIds.get(i));
      example.setDisplayOrder(i + 1);
      example.setUpdatedAt(now);
    }
    List<CodeExample> saved = codeExamples.saveAll(current);
    Lesson bumped = versions.bumpLesson(lesson);

    return saved.stream()
        .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
        .map(example -> toCodeExampleResponse(example, bumped))
        .toList();
  }

  /**
   * Normalizes a lesson body and admits it, or refuses the write.
   *
   * <p>The returned value is the author's own text: normalization is the only transformation it
   * undergoes, so what was submitted is what is stored, hashed and served back. The safety check is
   * a predicate over that text, not a rewrite of it -- a body carrying markup outside the
   * allow-list is refused with the offending elements named, rather than silently altered into
   * something the author never wrote and would only discover once it rendered.
   */
  private String normalizeAndValidateBody(String rawBody) {
    String normalized = TextNormalizer.normalize(rawBody);
    if (normalized == null || normalized.isBlank()) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "'body_markdown' must not be blank.",
          List.of(new ApiFieldError("body_markdown", "REQUIRED", "must not be blank")));
    }
    MarkdownSanitizer.validateMarkdown(normalized, "body_markdown");
    return normalized;
  }

  private void placeAmongSiblings(Lesson lesson, Integer requestedOrder) {
    List<Lesson> siblings =
        lessons.findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc(lesson.getModuleId());
    List<UUID> currentIds = new ArrayList<>();
    Map<UUID, Lesson> byId = new HashMap<>();
    for (Lesson sibling : siblings) {
      byId.put(sibling.getId(), sibling);
      if (!sibling.getId().equals(lesson.getId())) {
        currentIds.add(sibling.getId());
      }
    }
    byId.put(lesson.getId(), lesson);

    List<UUID> ordered =
        OrderNormalizer.placeAndRenumber(currentIds, lesson.getId(), requestedOrder);
    Instant now = now();
    for (int i = 0; i < ordered.size(); i++) {
      Lesson candidate = byId.get(ordered.get(i));
      int newOrder = i + 1;
      if (candidate.getDisplayOrder() != newOrder) {
        candidate.setDisplayOrder(newOrder);
        if (candidate != lesson) {
          candidate.setUpdatedAt(now);
          lessons.save(candidate);
        }
      }
    }
  }

  /**
   * Closes any gap left in a module's lesson ordering after a lesson left it (moved or deleted).
   */
  private void compactLessonOrder(UUID moduleId) {
    List<Lesson> siblings =
        lessons.findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc(moduleId);
    Instant now = now();
    for (int i = 0; i < siblings.size(); i++) {
      Lesson sibling = siblings.get(i);
      int newOrder = i + 1;
      if (sibling.getDisplayOrder() != newOrder) {
        sibling.setDisplayOrder(newOrder);
        sibling.setUpdatedAt(now);
        lessons.save(sibling);
      }
    }
  }

  private AdminCodeExampleResponse toCodeExampleResponse(CodeExample example, Lesson lesson) {
    return new AdminCodeExampleResponse(
        example.getId(),
        example.getLessonId(),
        example.getLanguage(),
        example.getCode(),
        example.getCaption(),
        example.getDisplayOrder(),
        lesson.getContentVersion(),
        example.getVersion());
  }

  private AdminLessonResponse toResponse(Lesson lesson) {
    UUID trackId = versions.resolveTrackId(lesson.getModuleId());
    List<AdminCodeExampleResponse> codeExampleResponses =
        codeExamples.findByLessonIdOrderByDisplayOrderAsc(lesson.getId()).stream()
            .map(example -> toCodeExampleResponse(example, lesson))
            .toList();
    return new AdminLessonResponse(
        lesson.getId(),
        lesson.getModuleId(),
        trackId,
        lesson.getSlug(),
        lesson.getTitle(),
        lesson.getBodyMarkdown(),
        lesson.getDifficulty(),
        lesson.getEstimatedMinutes(),
        lesson.getDisplayOrder(),
        lesson.getContentVersion(),
        codeExampleResponses,
        lesson.getCreatedAt(),
        lesson.getUpdatedAt(),
        lesson.getVersion());
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

  private Module requireModule(UUID id) {
    return modules
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.MODULE_NOT_FOUND, "No module exists with id '%s'.".formatted(id)));
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
