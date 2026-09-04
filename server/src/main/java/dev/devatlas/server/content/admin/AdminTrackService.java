package dev.devatlas.server.content.admin;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.common.PageQuery;
import dev.devatlas.server.common.PageResponse;
import dev.devatlas.server.common.TextNormalizer;
import dev.devatlas.server.common.UuidV7;
import dev.devatlas.server.content.ContentVersionService;
import dev.devatlas.server.content.OrderNormalizer;
import dev.devatlas.server.content.admin.dto.AdminModuleResponse;
import dev.devatlas.server.content.admin.dto.AdminTrackResponse;
import dev.devatlas.server.content.admin.dto.CreateTrackRequest;
import dev.devatlas.server.content.admin.dto.UpdateTrackRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoring for tracks and their module ordering (§5.4.2). */
@Service
public class AdminTrackService {

  private static final Map<String, String> SORT_FIELDS =
      Map.of("order", "displayOrder", "title", "title", "updated_at", "updatedAt");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "displayOrder");

  private final TrackRepository tracks;
  private final ModuleRepository modules;
  private final LessonRepository lessons;
  private final ContentVersionService versions;
  private final Clock clock;

  public AdminTrackService(
      TrackRepository tracks,
      ModuleRepository modules,
      LessonRepository lessons,
      ContentVersionService versions,
      Clock clock) {
    this.tracks = tracks;
    this.modules = modules;
    this.lessons = lessons;
    this.versions = versions;
    this.clock = clock;
  }

  @Transactional
  public AdminTrackResponse create(CreateTrackRequest request) {
    String slug = request.slug();
    if (tracks.existsBySlug(slug)) {
      throw new ApiException(
          ErrorCode.SLUG_ALREADY_EXISTS, "A track already exists with slug '%s'.".formatted(slug));
    }

    Instant now = now();
    Track track = new Track();
    track.setId(UuidV7.randomUuid());
    track.setSlug(slug);
    track.setTitle(TextNormalizer.normalize(request.title()));
    track.setDescription(TextNormalizer.normalize(request.description()));
    track.setIcon(request.icon());
    track.setPublished(request.published() != null && request.published());
    track.setContentVersion(1);
    track.setCreatedAt(now);
    track.setUpdatedAt(now);
    placeAmongSiblings(track, request.order());

    Track saved = tracks.save(track);
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminTrackResponse> list(
      Integer page, Integer size, List<String> sort, Boolean published) {
    Pageable pageable = PageQuery.resolve(page, size, sort, SORT_FIELDS, DEFAULT_SORT);
    Page<Track> result =
        published == null ? tracks.findAll(pageable) : tracks.findByPublished(published, pageable);
    List<AdminTrackResponse> items = result.getContent().stream().map(this::toResponse).toList();
    return PageResponse.of(items, result.getNumber(), result.getSize(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public AdminTrackResponse get(UUID id) {
    return toResponse(requireTrack(id));
  }

  @Transactional
  public AdminTrackResponse update(UUID id, UpdateTrackRequest request) {
    Track track = requireTrack(id);
    requireVersion(track.getVersion(), request.version());

    boolean changed = false;
    if (request.slug() != null && !request.slug().equals(track.getSlug())) {
      if (tracks.existsBySlugAndIdNot(request.slug(), id)) {
        throw new ApiException(
            ErrorCode.SLUG_ALREADY_EXISTS,
            "A track already exists with slug '%s'.".formatted(request.slug()));
      }
      track.setSlug(request.slug());
      changed = true;
    }
    if (request.title() != null) {
      track.setTitle(TextNormalizer.normalize(request.title()));
      changed = true;
    }
    if (request.description() != null) {
      track.setDescription(TextNormalizer.normalize(request.description()));
      changed = true;
    }
    if (request.icon() != null) {
      track.setIcon(request.icon());
      changed = true;
    }
    if (request.published() != null && request.published() != track.isPublished()) {
      track.setPublished(request.published());
      changed = true;
    }
    if (request.order() != null) {
      placeAmongSiblings(track, request.order());
      changed = true;
    }

    if (changed) {
      track.setContentVersion(track.getContentVersion() + 1);
    }
    track.setUpdatedAt(now());
    Track saved = tracks.save(track);
    return toResponse(saved);
  }

  @Transactional
  public void delete(UUID id) {
    Track track = requireTrack(id);
    if (!modules.findByTrackIdOrderByDisplayOrderAsc(id).isEmpty()) {
      throw new ApiException(
          ErrorCode.PARENT_NOT_EMPTY, "Track still has modules; delete them first.");
    }
    if (track.isPublished()) {
      throw new ApiException(
          ErrorCode.PUBLISHED_DELETE_BLOCKED,
          "A published track cannot be deleted; unpublish it first.");
    }
    tracks.delete(track);
  }

  @Transactional
  public List<AdminModuleResponse> reorderModules(UUID trackId, List<UUID> orderedIds) {
    Track track = requireTrack(trackId);
    List<Module> currentModules = modules.findByTrackIdOrderByDisplayOrderAsc(trackId);
    List<UUID> currentIds = currentModules.stream().map(Module::getId).toList();
    OrderNormalizer.requireCompleteSet(currentIds, orderedIds);

    Map<UUID, Module> byId = new HashMap<>();
    for (Module module : currentModules) {
      byId.put(module.getId(), module);
    }
    Instant now = now();
    for (int i = 0; i < orderedIds.size(); i++) {
      Module module = byId.get(orderedIds.get(i));
      module.setDisplayOrder(i + 1);
      module.setUpdatedAt(now);
    }
    List<Module> saved = modules.saveAll(currentModules);
    versions.bumpTrack(track.getId());

    return saved.stream()
        .sorted(Comparator.comparingInt(Module::getDisplayOrder))
        .map(this::toModuleResponse)
        .toList();
  }

  /**
   * Places {@code track} (new or existing) at {@code requestedOrder} among every other track and
   * renumbers the rest to stay 1-based and contiguous, persisting whichever siblings actually
   * moved. {@code track} itself is left for the caller to save.
   */
  private void placeAmongSiblings(Track track, Integer requestedOrder) {
    List<Track> siblings = tracks.findAll(Sort.by(Sort.Direction.ASC, "displayOrder"));

    List<UUID> currentIds = new ArrayList<>();
    Map<UUID, Track> byId = new HashMap<>();
    for (Track sibling : siblings) {
      byId.put(sibling.getId(), sibling);
      if (!sibling.getId().equals(track.getId())) {
        currentIds.add(sibling.getId());
      }
    }
    byId.put(track.getId(), track);

    List<UUID> ordered =
        OrderNormalizer.placeAndRenumber(currentIds, track.getId(), requestedOrder);

    Instant now = now();
    for (int i = 0; i < ordered.size(); i++) {
      Track candidate = byId.get(ordered.get(i));
      int newOrder = i + 1;
      if (candidate.getDisplayOrder() != newOrder) {
        candidate.setDisplayOrder(newOrder);
        if (candidate != track) {
          candidate.setUpdatedAt(now);
          tracks.save(candidate);
        }
      }
    }
  }

  private AdminModuleResponse toModuleResponse(Module module) {
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

  private AdminTrackResponse toResponse(Track track) {
    List<Module> trackModules = modules.findByTrackIdOrderByDisplayOrderAsc(track.getId());
    List<UUID> moduleIds = trackModules.stream().map(Module::getId).toList();
    long lessonCount =
        moduleIds.isEmpty() ? 0 : lessons.countByModuleIdInAndDeletedAtIsNull(moduleIds);
    return new AdminTrackResponse(
        track.getId(),
        track.getSlug(),
        track.getTitle(),
        track.getDescription(),
        track.getIcon(),
        track.getDisplayOrder(),
        track.isPublished(),
        track.getContentVersion(),
        moduleIds.size(),
        lessonCount,
        track.getCreatedAt(),
        track.getUpdatedAt(),
        track.getVersion());
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
