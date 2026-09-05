package dev.devatlas.server.content.manifest;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.config.JacksonConfig;
import dev.devatlas.server.content.packaging.CanonicalJson;
import dev.devatlas.server.content.packaging.LessonPackage;
import dev.devatlas.server.content.packaging.MindMapPackage;
import dev.devatlas.server.content.packaging.Sha256;
import dev.devatlas.server.domain.ContentTranslation;
import dev.devatlas.server.domain.Lesson;
import dev.devatlas.server.domain.MindMap;
import dev.devatlas.server.domain.Module;
import dev.devatlas.server.domain.Track;
import dev.devatlas.server.domain.TranslationEntityType;
import dev.devatlas.server.repository.ContentTranslationRepository;
import dev.devatlas.server.repository.LessonRepository;
import dev.devatlas.server.repository.MindMapRepository;
import dev.devatlas.server.repository.ModuleRepository;
import dev.devatlas.server.repository.TrackRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates the catalog manifest (§4.1) and the per-track manifest (§4.2) of the content sync
 * protocol.
 *
 * <p>Both are assembled as plain maps and lists and handed to {@link CanonicalJson}, never
 * serialized through the application's own object mapper. That is deliberate: §4.2 defines the
 * {@code ETag} as the SHA-256 of the canonical manifest bytes, so the bytes on the wire and the
 * bytes that were hashed have to be the same bytes, and the canonical serializer is the only one
 * that sorts object keys by code point and drops nulls the way §3.2 requires. A DTO tree would
 * serialize in field-declaration order and quietly produce a different digest for identical
 * content.
 *
 * <p>Nothing here computes a digest over content. The {@code sha256} and {@code size_bytes} an
 * entity entry advertises are read straight out of the columns the write boundary filled in (§3.3):
 * the manifest quotes the stored package, it does not re-derive it. That is what makes it
 * impossible for the manifest and the package endpoint to disagree.
 */
@Service
public class ManifestService {

  private final TrackRepository tracks;
  private final ModuleRepository modules;
  private final LessonRepository lessons;
  private final MindMapRepository mindMaps;
  private final ContentTranslationRepository translations;

  public ManifestService(
      TrackRepository tracks,
      ModuleRepository modules,
      LessonRepository lessons,
      MindMapRepository mindMaps,
      ContentTranslationRepository translations) {
    this.tracks = tracks;
    this.modules = modules;
    this.lessons = lessons;
    this.mindMaps = mindMaps;
    this.translations = translations;
  }

  /**
   * One summary row per published track (§4.1).
   *
   * <p>There is no {@code generated_at} field. A timestamp in the body would make every generation
   * differ, which would defeat the {@code ETag} entirely -- the client would revalidate, get a new
   * tag every time, and download the same catalog forever. The generation time travels in the HTTP
   * {@code Date} header, where it costs nothing.
   *
   * <p>Row order is by {@code track_id} as a string. §5 does not name a sort for this array because
   * it does not exist there, but a manifest whose row order came from the database's own {@code
   * uuid} collation would be one schema change away from reordering itself, and reordering is
   * indistinguishable from a content change once the bytes are hashed.
   */
  @Transactional(readOnly = true)
  public ManifestDocument catalog() {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Track track : tracks.findAllByPublishedTrue()) {
      TrackContent content = loadTrackContent(track);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("track_id", track.getId().toString());
      row.put("slug", track.getSlug());
      row.put("title", track.getTitle());
      row.put("content_version", track.getContentVersion());
      row.put("lesson_count", content.lessons().size());
      row.put("total_size_bytes", content.totalPackagedSizeBytes());
      row.put("updated_at", JacksonConfig.INSTANT_FORMAT.format(track.getUpdatedAt()));
      rows.add(row);
    }
    rows.sort(Comparator.comparing(row -> (String) row.get("track_id")));

    Map<String, Object> document = new LinkedHashMap<>();
    document.put("tracks", rows);
    return render(document);
  }

  /**
   * The full manifest for one track (§4.2): the structural summary the interface renders, plus the
   * flat entity list the download engine consumes.
   *
   * @throws ApiException {@code TRACK_NOT_FOUND} if the track is unknown or not published -- the
   *     client treats that as withdrawal, not as data loss, and deletes nothing (§4.2, §8)
   */
  @Transactional(readOnly = true)
  public ManifestDocument trackManifest(UUID trackId) {
    Track track =
        tracks
            .findById(trackId)
            .filter(Track::isPublished)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.TRACK_NOT_FOUND, "No published track exists with that id."));
    TrackContent content = loadTrackContent(track);

    Map<UUID, List<ContentTranslation>> moduleTranslations =
        translationsByEntity(
            TranslationEntityType.MODULE, content.modules().stream().map(Module::getId).toList());
    Map<UUID, List<ContentTranslation>> lessonTranslations =
        translationsByEntity(
            TranslationEntityType.LESSON, content.lessons().stream().map(Lesson::getId).toList());
    Map<UUID, List<Lesson>> lessonsByModule =
        content.lessons().stream().collect(Collectors.groupingBy(Lesson::getModuleId));

    Map<String, Object> document = new LinkedHashMap<>();
    document.put("track_id", track.getId().toString());
    document.put("slug", track.getSlug());
    document.put("title", track.getTitle());
    // Absent rather than null when the track has none: §3.2 rule 7.
    document.put("description", track.getDescription());
    document.put("icon", track.getIcon());
    document.put("content_version", track.getContentVersion());
    document.put(
        "translations",
        trackTranslations(
            translationsByEntity(TranslationEntityType.TRACK, List.of(track.getId()))
                .getOrDefault(track.getId(), List.of())));
    document.put(
        "modules",
        modules(content.modules(), lessonsByModule, moduleTranslations, lessonTranslations));
    document.put("entities", entities(content));
    return render(document);
  }

  /**
   * §5: {@code modules} by {@code order} then {@code module_id}, {@code lessons} by {@code order}
   * then {@code lesson_id}. The repository already returns modules and lessons in {@code order},
   * but two rows can share an order for the width of a renumbering transaction, and the tiebreaker
   * is what keeps the bytes reproducible when they do.
   */
  private static List<Map<String, Object>> modules(
      List<Module> moduleRows,
      Map<UUID, List<Lesson>> lessonsByModule,
      Map<UUID, List<ContentTranslation>> moduleTranslations,
      Map<UUID, List<ContentTranslation>> lessonTranslations) {
    return moduleRows.stream()
        .sorted(
            Comparator.comparingInt(Module::getDisplayOrder)
                .thenComparing(module -> module.getId().toString()))
        .map(
            module -> {
              Map<String, Object> entry = new LinkedHashMap<>();
              entry.put("module_id", module.getId().toString());
              entry.put("title", module.getTitle());
              entry.put("order", module.getDisplayOrder());
              entry.put("estimated_minutes", module.getEstimatedMinutes());
              entry.put(
                  "translations",
                  titleTranslations(moduleTranslations.getOrDefault(module.getId(), List.of())));
              entry.put(
                  "lessons",
                  lessons(
                      lessonsByModule.getOrDefault(module.getId(), List.of()), lessonTranslations));
              return entry;
            })
        .toList();
  }

  private static List<Map<String, Object>> lessons(
      List<Lesson> lessonRows, Map<UUID, List<ContentTranslation>> lessonTranslations) {
    return lessonRows.stream()
        .sorted(
            Comparator.comparingInt(Lesson::getDisplayOrder)
                .thenComparing(lesson -> lesson.getId().toString()))
        .map(
            lesson -> {
              Map<String, Object> entry = new LinkedHashMap<>();
              entry.put("lesson_id", lesson.getId().toString());
              entry.put("slug", lesson.getSlug());
              entry.put("title", lesson.getTitle());
              entry.put("difficulty", lesson.getDifficulty().name());
              entry.put("estimated_minutes", lesson.getEstimatedMinutes());
              entry.put("order", lesson.getDisplayOrder());
              entry.put(
                  "translations",
                  titleTranslations(lessonTranslations.getOrDefault(lesson.getId(), List.of())));
              return entry;
            })
        .toList();
  }

  /**
   * §4.2: exactly the five fields the download engine needs, sorted by {@code entity_type} then
   * {@code entity_id}.
   *
   * <p>An entity with no stored package is not listed. A manifest entry promises a digest and a
   * length, and a row whose package columns are still null has neither; advertising it would hand
   * the engine a download that can only end in a {@code 404}. The lesson still appears in the
   * structural summary above, because the summary describes what the track contains rather than
   * what can be fetched.
   */
  private static List<Map<String, Object>> entities(TrackContent content) {
    List<Map<String, Object>> entries = new ArrayList<>();
    for (Lesson lesson : content.lessons()) {
      if (lesson.getSha256() == null) {
        continue;
      }
      entries.add(
          entity(
              LessonPackage.ENTITY_TYPE,
              lesson.getId(),
              lesson.getContentVersion(),
              lesson.getSha256(),
              lesson.getPackageSizeBytes()));
    }
    MindMap mindMap = content.mindMap();
    if (mindMap != null && mindMap.getSha256() != null) {
      entries.add(
          entity(
              MindMapPackage.ENTITY_TYPE,
              mindMap.getId(),
              mindMap.getContentVersion(),
              mindMap.getSha256(),
              mindMap.getPackageSizeBytes()));
    }
    entries.sort(
        Comparator.<Map<String, Object>, String>comparing(
                entry -> (String) entry.get("entity_type"))
            .thenComparing(entry -> (String) entry.get("entity_id")));
    return entries;
  }

  private static Map<String, Object> entity(
      String entityType, UUID entityId, int contentVersion, String sha256, int sizeBytes) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("entity_type", entityType);
    entry.put("entity_id", entityId.toString());
    entry.put("content_version", contentVersion);
    entry.put("sha256", sha256);
    entry.put("size_bytes", sizeBytes);
    return entry;
  }

  /**
   * Track translations carry the translated description as {@code body} (§4.2). No package ships a
   * track's own text, so this is the only place a desktop client can read it -- without it the
   * desktop would show an English track around Turkish lessons while the web client showed both in
   * Turkish.
   */
  private static List<Map<String, Object>> trackTranslations(List<ContentTranslation> rows) {
    return sortedByLocale(rows)
        .map(
            row -> {
              Map<String, Object> entry = new LinkedHashMap<>();
              entry.put("locale", row.getLocale());
              entry.put("title", row.getTitle());
              entry.put("body", row.getBody());
              return entry;
            })
        .toList();
  }

  /**
   * Module and lesson translations carry the title only (§4.2). A module has no body, and a
   * lesson's translated body travels inside the lesson package -- repeating it here would double
   * the manifest for something the engine downloads anyway.
   */
  private static List<Map<String, Object>> titleTranslations(List<ContentTranslation> rows) {
    return sortedByLocale(rows)
        .map(
            row -> {
              Map<String, Object> entry = new LinkedHashMap<>();
              entry.put("locale", row.getLocale());
              entry.put("title", row.getTitle());
              return entry;
            })
        .toList();
  }

  /** §5: {@code translations} sorted by {@code locale}. */
  private static Stream<ContentTranslation> sortedByLocale(List<ContentTranslation> rows) {
    return rows.stream().sorted(Comparator.comparing(ContentTranslation::getLocale));
  }

  private Map<UUID, List<ContentTranslation>> translationsByEntity(
      TranslationEntityType entityType, List<UUID> entityIds) {
    if (entityIds.isEmpty()) {
      return Map.of();
    }
    return translations.findByEntityTypeAndEntityIdIn(entityType, entityIds).stream()
        .collect(Collectors.groupingBy(ContentTranslation::getEntityId));
  }

  private TrackContent loadTrackContent(Track track) {
    List<Module> trackModules = modules.findByTrackIdOrderByDisplayOrderAsc(track.getId());
    List<UUID> moduleIds = trackModules.stream().map(Module::getId).toList();
    List<Lesson> trackLessons =
        moduleIds.isEmpty() ? List.of() : lessons.findByModuleIdInAndDeletedAtIsNull(moduleIds);
    MindMap mindMap = mindMaps.findByTrackId(track.getId()).orElse(null);
    return new TrackContent(trackModules, trackLessons, mindMap);
  }

  private static ManifestDocument render(Map<String, Object> document) {
    byte[] bytes = CanonicalJson.bytes(document);
    return new ManifestDocument(bytes, "\"" + Sha256.hex(bytes) + "\"");
  }

  /** Everything under one track that a manifest describes, read once per generation. */
  private record TrackContent(List<Module> modules, List<Lesson> lessons, MindMap mindMap) {

    /**
     * §4.1: {@code total_size_bytes} is the sum of the entity package sizes, so a client can show
     * "about 1.8 MB" before committing a user to a download. Unpackaged rows contribute nothing,
     * because they are not entities the manifest offers.
     */
    long totalPackagedSizeBytes() {
      long total =
          lessons.stream()
              .filter(lesson -> lesson.getSha256() != null)
              .mapToLong(Lesson::getPackageSizeBytes)
              .sum();
      if (mindMap != null && mindMap.getSha256() != null) {
        total += mindMap.getPackageSizeBytes();
      }
      return total;
    }
  }
}
