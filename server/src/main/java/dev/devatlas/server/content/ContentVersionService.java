package dev.devatlas.server.content;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.content.packaging.CodeExamplePackageItem;
import dev.devatlas.server.content.packaging.ContentPackager;
import dev.devatlas.server.content.packaging.LessonPackage;
import dev.devatlas.server.content.packaging.MindMapPackage;
import dev.devatlas.server.content.packaging.PackagedContent;
import dev.devatlas.server.content.packaging.TranslationPackageItem;
import dev.devatlas.server.domain.CodeExample;
import dev.devatlas.server.domain.ContentTranslation;
import dev.devatlas.server.domain.Lesson;
import dev.devatlas.server.domain.MindMap;
import dev.devatlas.server.domain.Module;
import dev.devatlas.server.domain.Track;
import dev.devatlas.server.domain.TranslationEntityType;
import dev.devatlas.server.repository.CodeExampleRepository;
import dev.devatlas.server.repository.ContentTranslationRepository;
import dev.devatlas.server.repository.LessonRepository;
import dev.devatlas.server.repository.MindMapRepository;
import dev.devatlas.server.repository.ModuleRepository;
import dev.devatlas.server.repository.TrackRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The single place {@code content_version} is incremented and, for lessons and mind maps, the
 * packaged bytes and digest are recomputed -- in the service layer, in the same transaction as the
 * write that caused it (§5.4.1). Never a database trigger or generated column: a hash computed
 * outside application code cannot be reached by the determinism test and is exactly the shape of
 * bug this rule exists to rule out.
 *
 * <p>{@code tracks.content_version} is its own stored counter, never a maximum over its descendants
 * (§5.4.1): a maximum cannot represent removal, so every bump below that touches something
 * belonging to a track also bumps the track's own counter, explicitly, here.
 */
@Service
public class ContentVersionService {

  private final TrackRepository tracks;
  private final ModuleRepository modules;
  private final LessonRepository lessons;
  private final CodeExampleRepository codeExamples;
  private final MindMapRepository mindMaps;
  private final ContentTranslationRepository translations;
  private final ContentPackager packager;
  private final Clock clock;

  public ContentVersionService(
      TrackRepository tracks,
      ModuleRepository modules,
      LessonRepository lessons,
      CodeExampleRepository codeExamples,
      MindMapRepository mindMaps,
      ContentTranslationRepository translations,
      ContentPackager packager,
      Clock clock) {
    this.tracks = tracks;
    this.modules = modules;
    this.lessons = lessons;
    this.codeExamples = codeExamples;
    this.mindMaps = mindMaps;
    this.translations = translations;
    this.packager = packager;
    this.clock = clock;
  }

  /** Bumps a track's own counter and touches {@code updated_at}. */
  public Track bumpTrack(UUID trackId) {
    Track track = requireTrack(trackId);
    track.setContentVersion(track.getContentVersion() + 1);
    track.setUpdatedAt(now());
    return tracks.save(track);
  }

  /**
   * Bumps a lesson's own counter, recomputes its package and digest from the lesson's current
   * columns and current code examples, and bumps the owning track. Callers must have already
   * persisted whatever change triggered the bump (a lesson field, or a code example under it) so
   * that the code examples read back here reflect it -- Hibernate auto-flushes the pending change
   * before this method's own query against the same table runs.
   */
  public Lesson bumpLesson(Lesson lesson) {
    lesson.setContentVersion(lesson.getContentVersion() + 1);
    repackageLesson(lesson);
    lesson.setUpdatedAt(now());
    Lesson saved = lessons.save(lesson);
    bumpTrack(resolveTrackId(lesson.getModuleId()));
    return saved;
  }

  /**
   * Recomputes and stores a lesson's package and digest without incrementing its version.
   *
   * <p>The package includes the lesson's current translations (content sync protocol §4.3): a
   * translation is part of the packaged bytes, not a side note, so creating, updating or deleting
   * one already changes this digest on its own -- which is exactly why {@code content_version}
   * itself is also part of the hashed bytes (§3.3, §3.5): the digest has to describe one full,
   * exact version of the package, translations included.
   */
  public void repackageLesson(Lesson lesson) {
    List<CodeExample> examples = codeExamples.findByLessonIdOrderByDisplayOrderAsc(lesson.getId());
    List<ContentTranslation> lessonTranslations =
        translations.findByEntityTypeAndEntityId(TranslationEntityType.LESSON, lesson.getId());
    LessonPackage pkg =
        new LessonPackage(
            lesson.getId(),
            LessonPackage.ENTITY_TYPE,
            lesson.getContentVersion(),
            lesson.getSlug(),
            lesson.getTitle(),
            lesson.getBodyMarkdown(),
            lesson.getDifficulty(),
            lesson.getEstimatedMinutes(),
            lesson.getModuleId(),
            lesson.getDisplayOrder(),
            examples.stream()
                .map(
                    example ->
                        new CodeExamplePackageItem(
                            example.getLanguage(),
                            example.getCode(),
                            example.getCaption(),
                            example.getDisplayOrder()))
                .collect(Collectors.toList()),
            lessonTranslations.stream()
                .map(
                    translation ->
                        new TranslationPackageItem(
                            translation.getLocale(), translation.getTitle(), translation.getBody()))
                .collect(Collectors.toList()));
    PackagedContent packaged = packager.packageLesson(pkg);
    lesson.setSha256(packaged.sha256Hex());
    lesson.setPackageBytes(packaged.bytes());
    lesson.setPackageSizeBytes(packaged.sizeBytes());
  }

  /**
   * Bumps a mind map's own counter, recomputes its package and digest, and bumps the owning track.
   */
  public MindMap bumpMindMap(MindMap mindMap) {
    mindMap.setContentVersion(mindMap.getContentVersion() + 1);
    repackageMindMap(mindMap);
    mindMap.setUpdatedAt(now());
    MindMap saved = mindMaps.save(mindMap);
    bumpTrack(mindMap.getTrackId());
    return saved;
  }

  /** Recomputes and stores a mind map's package and digest without incrementing its version. */
  public void repackageMindMap(MindMap mindMap) {
    MindMapPackage pkg =
        new MindMapPackage(
            mindMap.getId(),
            MindMapPackage.ENTITY_TYPE,
            mindMap.getContentVersion(),
            mindMap.getRoot(),
            mindMap.getTrackId());
    PackagedContent packaged = packager.packageMindMap(pkg);
    mindMap.setSha256(packaged.sha256Hex());
    mindMap.setPackageBytes(packaged.bytes());
    mindMap.setPackageSizeBytes(packaged.sizeBytes());
  }

  /** Resolves the track a lesson lives under, through its module. */
  public UUID resolveTrackId(UUID moduleId) {
    Module module =
        modules
            .findById(moduleId)
            .orElseThrow(
                () ->
                    new ApiException(ErrorCode.MODULE_NOT_FOUND, "No module exists with that id."));
    return module.getTrackId();
  }

  private Track requireTrack(UUID trackId) {
    return tracks
        .findById(trackId)
        .orElseThrow(
            () -> new ApiException(ErrorCode.TRACK_NOT_FOUND, "No track exists with that id."));
  }

  private Instant now() {
    return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
  }
}
