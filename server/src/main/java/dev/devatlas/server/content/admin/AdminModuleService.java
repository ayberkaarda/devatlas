package dev.devatlas.server.content.admin;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.common.TextNormalizer;
import dev.devatlas.server.common.UuidV7;
import dev.devatlas.server.content.ContentVersionService;
import dev.devatlas.server.content.OrderNormalizer;
import dev.devatlas.server.content.admin.dto.AdminLessonResponse;
import dev.devatlas.server.content.admin.dto.AdminModuleResponse;
import dev.devatlas.server.content.admin.dto.CreateModuleRequest;
import dev.devatlas.server.content.admin.dto.UpdateModuleRequest;
import dev.devatlas.server.domain.Lesson;
import dev.devatlas.server.domain.Module;
import dev.devatlas.server.domain.Track;
import dev.devatlas.server.repository.LessonRepository;
import dev.devatlas.server.repository.ModuleRepository;
import dev.devatlas.server.repository.TrackRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoring for modules and their lesson ordering (§5.4.3). */
@Service
public class AdminModuleService {

  private final ModuleRepository modules;
  private final TrackRepository tracks;
  private final LessonRepository lessons;
  private final ContentVersionService versions;
  private final Clock clock;

  public AdminModuleService(
      ModuleRepository modules,
      TrackRepository tracks,
      LessonRepository lessons,
      ContentVersionService versions,
      Clock clock) {
    this.modules = modules;
    this.tracks = tracks;
    this.lessons = lessons;
    this.versions = versions;
    this.clock = clock;
  }

  @Transactional
  public AdminModuleResponse create(UUID trackId, CreateModuleRequest request) {
    requireTrack(trackId);

    Instant now = now();
    Module module = new Module();
    module.setId(UuidV7.randomUuid());
    module.setTrackId(trackId);
    module.setTitle(TextNormalizer.normalize(request.title()));
    module.setEstimatedMinutes(request.estimatedMinutes());
    module.setCreatedAt(now);
    module.setUpdatedAt(now);
    placeAmongSiblings(module, request.order());

    Module saved = modules.save(module);
    versions.bumpTrack(trackId);
    return toResponse(saved);
  }

  @Transactional
  public AdminModuleResponse update(UUID id, UpdateModuleRequest request) {
    Module module = requireModule(id);
    requireVersion(module.getVersion(), request.version());

    UUID oldTrackId = module.getTrackId();
    boolean moved = request.trackId() != null && !request.trackId().equals(oldTrackId);
    boolean changed = false;

    if (moved) {
      requireTrack(request.trackId());
      module.setTrackId(request.trackId());
      changed = true;
    }
    if (request.title() != null) {
      module.setTitle(TextNormalizer.normalize(request.title()));
      changed = true;
    }
    if (request.estimatedMinutes() != null) {
      module.setEstimatedMinutes(request.estimatedMinutes());
      changed = true;
    }
    if (moved || request.order() != null) {
      placeAmongSiblings(module, request.order());
      changed = true;
    }

    module.setUpdatedAt(now());
    Module saved = modules.save(module);

    if (moved) {
      compactModuleOrder(oldTrackId);
      versions.bumpTrack(oldTrackId);
      List<Lesson> moduleLessons =
          lessons.findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc(module.getId());
      for (Lesson lesson : moduleLessons) {
        versions.bumpLesson(lesson);
      }
      if (moduleLessons.isEmpty()) {
        // Every lesson bump above already bumps the destination track; with none, the module's
        // arrival must still be signalled explicitly.
        versions.bumpTrack(module.getTrackId());
      }
    } else if (changed) {
      versions.bumpTrack(module.getTrackId());
    }

    return toResponse(saved);
  }

  @Transactional
  public void delete(UUID id) {
    Module module = requireModule(id);
    if (!lessons.findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc(id).isEmpty()) {
      throw new ApiException(
          ErrorCode.PARENT_NOT_EMPTY, "Module still has lessons; delete them first.");
    }
    UUID trackId = module.getTrackId();
    modules.delete(module);
    compactModuleOrder(trackId);
    versions.bumpTrack(trackId);
  }

  @Transactional
  public List<AdminLessonResponse> reorderLessons(UUID moduleId, List<UUID> orderedIds) {
    Module module = requireModule(moduleId);
    List<Lesson> currentLessons =
        lessons.findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc(moduleId);
    List<UUID> currentIds = currentLessons.stream().map(Lesson::getId).toList();
    OrderNormalizer.requireCompleteSet(currentIds, orderedIds);

    Map<UUID, Lesson> byId = new HashMap<>();
    for (Lesson lesson : currentLessons) {
      byId.put(lesson.getId(), lesson);
    }
    for (int i = 0; i < orderedIds.size(); i++) {
      byId.get(orderedIds.get(i)).setDisplayOrder(i + 1);
    }
    List<Lesson> saved = lessons.saveAll(currentLessons);
    for (Lesson lesson : saved) {
      versions.bumpLesson(lesson);
    }

    return saved.stream()
        .sorted(Comparator.comparingInt(Lesson::getDisplayOrder))
        .map(this::toLessonResponse)
        .toList();
  }

  private void placeAmongSiblings(Module module, Integer requestedOrder) {
    List<Module> siblings = modules.findByTrackIdOrderByDisplayOrderAsc(module.getTrackId());
    List<UUID> currentIds = new ArrayList<>();
    Map<UUID, Module> byId = new HashMap<>();
    for (Module sibling : siblings) {
      byId.put(sibling.getId(), sibling);
      if (!sibling.getId().equals(module.getId())) {
        currentIds.add(sibling.getId());
      }
    }
    byId.put(module.getId(), module);

    List<UUID> ordered =
        OrderNormalizer.placeAndRenumber(currentIds, module.getId(), requestedOrder);
    Instant now = now();
    for (int i = 0; i < ordered.size(); i++) {
      Module candidate = byId.get(ordered.get(i));
      int newOrder = i + 1;
      if (candidate.getDisplayOrder() != newOrder) {
        candidate.setDisplayOrder(newOrder);
        if (candidate != module) {
          candidate.setUpdatedAt(now);
          modules.save(candidate);
        }
      }
    }
  }

  /** Closes any gap left in a track's module ordering after a module left it. */
  private void compactModuleOrder(UUID trackId) {
    List<Module> siblings = modules.findByTrackIdOrderByDisplayOrderAsc(trackId);
    Instant now = now();
    for (int i = 0; i < siblings.size(); i++) {
      Module sibling = siblings.get(i);
      int newOrder = i + 1;
      if (sibling.getDisplayOrder() != newOrder) {
        sibling.setDisplayOrder(newOrder);
        sibling.setUpdatedAt(now);
        modules.save(sibling);
      }
    }
  }

  private AdminLessonResponse toLessonResponse(Lesson lesson) {
    UUID trackId = versions.resolveTrackId(lesson.getModuleId());
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
        List.of(),
        lesson.getCreatedAt(),
        lesson.getUpdatedAt(),
        lesson.getVersion());
  }

  private AdminModuleResponse toResponse(Module module) {
    long lessonCount =
        lessons.findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc(module.getId()).size();
    return new AdminModuleResponse(
        module.getId(),
        module.getTrackId(),
        module.getTitle(),
        module.getDisplayOrder(),
        module.getEstimatedMinutes(),
        lessonCount,
        module.getCreatedAt(),
        module.getUpdatedAt(),
        module.getVersion());
  }

  private Module requireModule(UUID id) {
    return modules
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.MODULE_NOT_FOUND, "No module exists with id '%s'.".formatted(id)));
  }

  private Track requireTrack(UUID id) {
    return tracks
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.TRACK_NOT_FOUND, "No track exists with id '%s'.".formatted(id)));
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
