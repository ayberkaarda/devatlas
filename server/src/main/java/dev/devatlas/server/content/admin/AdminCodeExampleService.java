package dev.devatlas.server.content.admin;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.common.TextNormalizer;
import dev.devatlas.server.common.UuidV7;
import dev.devatlas.server.content.ContentVersionService;
import dev.devatlas.server.content.OrderNormalizer;
import dev.devatlas.server.content.admin.dto.AdminCodeExampleResponse;
import dev.devatlas.server.content.admin.dto.CreateCodeExampleRequest;
import dev.devatlas.server.content.admin.dto.UpdateCodeExampleRequest;
import dev.devatlas.server.domain.CodeExample;
import dev.devatlas.server.domain.Lesson;
import dev.devatlas.server.repository.CodeExampleRepository;
import dev.devatlas.server.repository.LessonRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoring for code examples nested under a lesson (§5.4.5, §7.5). */
@Service
public class AdminCodeExampleService {

  /** The closed set of highlighter-supported language identifiers (§5.4.5). */
  private static final Set<String> SUPPORTED_LANGUAGES =
      Set.of(
          "typescript",
          "javascript",
          "java",
          "rust",
          "sql",
          "bash",
          "json",
          "yaml",
          "html",
          "css",
          "xml",
          "kotlin",
          "python",
          "csharp",
          "go",
          "php",
          "ruby",
          "text");

  private final CodeExampleRepository codeExamples;
  private final LessonRepository lessons;
  private final ContentVersionService versions;
  private final Clock clock;

  public AdminCodeExampleService(
      CodeExampleRepository codeExamples,
      LessonRepository lessons,
      ContentVersionService versions,
      Clock clock) {
    this.codeExamples = codeExamples;
    this.lessons = lessons;
    this.versions = versions;
    this.clock = clock;
  }

  @Transactional
  public AdminCodeExampleResponse create(UUID lessonId, CreateCodeExampleRequest request) {
    Lesson lesson = requireLesson(lessonId);
    String language = requireSupportedLanguage(request.language());

    Instant now = now();
    CodeExample example = new CodeExample();
    example.setId(UuidV7.randomUuid());
    example.setLessonId(lessonId);
    example.setLanguage(language);
    example.setCode(TextNormalizer.normalizeCode(request.code()));
    example.setCaption(
        request.caption() == null ? null : TextNormalizer.normalize(request.caption()));
    example.setCreatedAt(now);
    example.setUpdatedAt(now);
    placeAmongSiblings(example, request.order());

    CodeExample saved = codeExamples.save(example);
    Lesson bumped = versions.bumpLesson(lesson);
    return toResponse(saved, bumped);
  }

  @Transactional
  public AdminCodeExampleResponse update(UUID id, UpdateCodeExampleRequest request) {
    CodeExample example = requireCodeExample(id);
    requireVersion(example.getVersion(), request.version());
    Lesson lesson = requireLesson(example.getLessonId());

    boolean changed = false;
    if (request.language() != null) {
      example.setLanguage(requireSupportedLanguage(request.language()));
      changed = true;
    }
    if (request.code() != null) {
      example.setCode(TextNormalizer.normalizeCode(request.code()));
      changed = true;
    }
    if (request.caption() != null) {
      example.setCaption(TextNormalizer.normalize(request.caption()));
      changed = true;
    }
    if (request.order() != null) {
      placeAmongSiblings(example, request.order());
      changed = true;
    }
    example.setUpdatedAt(now());

    CodeExample saved = codeExamples.save(example);
    Lesson bumped = changed ? versions.bumpLesson(lesson) : lesson;
    return toResponse(saved, bumped);
  }

  @Transactional
  public void delete(UUID id) {
    CodeExample example = requireCodeExample(id);
    Lesson lesson = requireLesson(example.getLessonId());
    UUID lessonId = example.getLessonId();
    codeExamples.delete(example);
    compactOrder(lessonId);
    versions.bumpLesson(lesson);
  }

  private void placeAmongSiblings(CodeExample example, Integer requestedOrder) {
    List<CodeExample> siblings =
        codeExamples.findByLessonIdOrderByDisplayOrderAsc(example.getLessonId());
    List<UUID> currentIds = new ArrayList<>();
    Map<UUID, CodeExample> byId = new HashMap<>();
    for (CodeExample sibling : siblings) {
      byId.put(sibling.getId(), sibling);
      if (!sibling.getId().equals(example.getId())) {
        currentIds.add(sibling.getId());
      }
    }
    byId.put(example.getId(), example);

    List<UUID> ordered =
        OrderNormalizer.placeAndRenumber(currentIds, example.getId(), requestedOrder);
    Instant now = now();
    for (int i = 0; i < ordered.size(); i++) {
      CodeExample candidate = byId.get(ordered.get(i));
      int newOrder = i + 1;
      if (candidate.getDisplayOrder() != newOrder) {
        candidate.setDisplayOrder(newOrder);
        if (candidate != example) {
          candidate.setUpdatedAt(now);
          codeExamples.save(candidate);
        }
      }
    }
  }

  private void compactOrder(UUID lessonId) {
    List<CodeExample> siblings = codeExamples.findByLessonIdOrderByDisplayOrderAsc(lessonId);
    Instant now = now();
    for (int i = 0; i < siblings.size(); i++) {
      CodeExample sibling = siblings.get(i);
      int newOrder = i + 1;
      if (sibling.getDisplayOrder() != newOrder) {
        sibling.setDisplayOrder(newOrder);
        sibling.setUpdatedAt(now);
        codeExamples.save(sibling);
      }
    }
  }

  private AdminCodeExampleResponse toResponse(CodeExample example, Lesson lesson) {
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

  private static String requireSupportedLanguage(String language) {
    if (language == null || !SUPPORTED_LANGUAGES.contains(language)) {
      throw new ApiException(
          ErrorCode.UNSUPPORTED_LANGUAGE,
          "'%s' is not a supported code example language.".formatted(language));
    }
    return language;
  }

  private CodeExample requireCodeExample(UUID id) {
    return codeExamples
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.CODE_EXAMPLE_NOT_FOUND,
                    "No code example exists with id '%s'.".formatted(id)));
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
