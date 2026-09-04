package dev.devatlas.server.content.admin;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.common.MarkdownSanitizer;
import dev.devatlas.server.common.TextNormalizer;
import dev.devatlas.server.common.UuidV7;
import dev.devatlas.server.content.ContentVersionService;
import dev.devatlas.server.content.admin.dto.AdminMindMapResponse;
import dev.devatlas.server.content.admin.dto.MindMapNodeRequest;
import dev.devatlas.server.content.admin.dto.MindMapUpsertRequest;
import dev.devatlas.server.content.dto.MindMapNodeResponse;
import dev.devatlas.server.domain.Lesson;
import dev.devatlas.server.domain.MindMap;
import dev.devatlas.server.domain.Module;
import dev.devatlas.server.domain.Track;
import dev.devatlas.server.repository.LessonRepository;
import dev.devatlas.server.repository.MindMapRepository;
import dev.devatlas.server.repository.ModuleRepository;
import dev.devatlas.server.repository.TrackRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Authoring for the single mind map belonging to a track (§5.4.6, §5.2.4, §7.6). */
@Service
public class AdminMindMapService {

  private static final int MAX_DEPTH = 8;
  private static final int MAX_NODES = 500;

  private final MindMapRepository mindMaps;
  private final TrackRepository tracks;
  private final ModuleRepository modules;
  private final LessonRepository lessons;
  private final ContentVersionService versions;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  public AdminMindMapService(
      MindMapRepository mindMaps,
      TrackRepository tracks,
      ModuleRepository modules,
      LessonRepository lessons,
      ContentVersionService versions,
      JsonMapper jsonMapper,
      Clock clock) {
    this.mindMaps = mindMaps;
    this.tracks = tracks;
    this.modules = modules;
    this.lessons = lessons;
    this.versions = versions;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public AdminMindMapResponse get(UUID trackId) {
    requireTrack(trackId);
    return toResponse(requireMindMap(trackId));
  }

  /**
   * Whether {@link #upsert} created a new mind map (for the caller's {@code 201} vs {@code 200}).
   */
  public record UpsertOutcome(AdminMindMapResponse response, boolean created) {}

  @Transactional
  public UpsertOutcome upsert(UUID trackId, MindMapUpsertRequest request) {
    requireTrack(trackId);
    Optional<MindMap> existing = mindMaps.findByTrackId(trackId);

    if (existing.isPresent()) {
      if (request.version() == null) {
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED,
            "'version' is required when updating an existing mind map.");
      }
      requireVersion(existing.get().getVersion(), request.version());
    }

    Set<UUID> validLessonIds = collectLessonIds(trackId);
    Set<String> seenNodeIds = new HashSet<>();
    MindMapNodeResponse root =
        validate(request.root(), 1, new int[] {0}, seenNodeIds, validLessonIds);
    String rootJson = jsonMapper.writeValueAsString(root);

    if (existing.isPresent()) {
      MindMap mindMap = existing.get();
      mindMap.setRoot(rootJson);
      MindMap saved = versions.bumpMindMap(mindMap);
      return new UpsertOutcome(toResponse(saved), false);
    }

    Instant now = now();
    MindMap mindMap = new MindMap();
    mindMap.setId(UuidV7.randomUuid());
    mindMap.setTrackId(trackId);
    mindMap.setRoot(rootJson);
    mindMap.setContentVersion(1);
    mindMap.setCreatedAt(now);
    mindMap.setUpdatedAt(now);
    versions.repackageMindMap(mindMap);
    MindMap saved = mindMaps.save(mindMap);
    versions.bumpTrack(trackId);
    return new UpsertOutcome(toResponse(saved), true);
  }

  @Transactional
  public void delete(UUID trackId) {
    requireTrack(trackId);
    MindMap mindMap = requireMindMap(trackId);
    mindMaps.delete(mindMap);
    versions.bumpTrack(trackId);
  }

  /**
   * Validates and sanitizes one node and its subtree, enforcing the structural rules that cannot be
   * expressed as a field constraint: depth, total node count, id uniqueness within the map, and a
   * {@code lessonId} that belongs to the track this map is being written for.
   */
  private MindMapNodeResponse validate(
      MindMapNodeRequest node,
      int depth,
      int[] nodeCount,
      Set<String> seenNodeIds,
      Set<UUID> validLessonIds) {
    if (depth > MAX_DEPTH) {
      throw new ApiException(
          ErrorCode.MIND_MAP_INVALID,
          "Mind map exceeds the maximum depth of %d.".formatted(MAX_DEPTH));
    }
    nodeCount[0]++;
    if (nodeCount[0] > MAX_NODES) {
      throw new ApiException(
          ErrorCode.MIND_MAP_INVALID,
          "Mind map exceeds the maximum of %d nodes.".formatted(MAX_NODES));
    }
    if (!seenNodeIds.add(node.id())) {
      throw new ApiException(
          ErrorCode.MIND_MAP_INVALID,
          "Node id '%s' is used more than once in this map.".formatted(node.id()));
    }
    if (node.lessonId() != null && !validLessonIds.contains(node.lessonId())) {
      throw new ApiException(
          ErrorCode.MIND_MAP_INVALID,
          "Node '%s' references a lesson that does not belong to this track.".formatted(node.id()));
    }

    String label = MarkdownSanitizer.sanitizePlainText(TextNormalizer.normalize(node.label()));
    if (label == null || label.isBlank()) {
      throw new ApiException(
          ErrorCode.MIND_MAP_INVALID,
          "Node '%s' has no label left after sanitization.".formatted(node.id()));
    }

    List<MindMapNodeResponse> children =
        node.children().stream()
            .map(child -> validate(child, depth + 1, nodeCount, seenNodeIds, validLessonIds))
            .toList();

    return new MindMapNodeResponse(node.id(), label, node.lessonId(), children);
  }

  private Set<UUID> collectLessonIds(UUID trackId) {
    Set<UUID> lessonIds = new HashSet<>();
    for (Module module : modules.findByTrackIdOrderByDisplayOrderAsc(trackId)) {
      for (Lesson lesson :
          lessons.findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc(module.getId())) {
        lessonIds.add(lesson.getId());
      }
    }
    return lessonIds;
  }

  private AdminMindMapResponse toResponse(MindMap mindMap) {
    MindMapNodeResponse root = jsonMapper.readValue(mindMap.getRoot(), MindMapNodeResponse.class);
    return new AdminMindMapResponse(
        mindMap.getId(),
        mindMap.getTrackId(),
        mindMap.getContentVersion(),
        mindMap.getUpdatedAt(),
        root,
        mindMap.getVersion());
  }

  private Track requireTrack(UUID id) {
    return tracks
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.TRACK_NOT_FOUND, "No track exists with id '%s'.".formatted(id)));
  }

  private MindMap requireMindMap(UUID trackId) {
    return mindMaps
        .findByTrackId(trackId)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.MIND_MAP_NOT_FOUND,
                    "Track '%s' has no mind map.".formatted(trackId)));
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
