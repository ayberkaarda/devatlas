package dev.devatlas.server.content;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.common.PageQuery;
import dev.devatlas.server.common.PageResponse;
import dev.devatlas.server.content.dto.CodeExampleResponse;
import dev.devatlas.server.content.dto.LessonDetailResponse;
import dev.devatlas.server.content.dto.LessonModuleRef;
import dev.devatlas.server.content.dto.LessonSummaryResponse;
import dev.devatlas.server.content.dto.LessonTrackRef;
import dev.devatlas.server.content.dto.MindMapNodeResponse;
import dev.devatlas.server.content.dto.MindMapResponse;
import dev.devatlas.server.content.dto.ModuleSummaryResponse;
import dev.devatlas.server.content.dto.ProgressResponse;
import dev.devatlas.server.content.dto.TrackDetailResponse;
import dev.devatlas.server.content.dto.TrackListItemResponse;
import dev.devatlas.server.domain.CodeExample;
import dev.devatlas.server.domain.ContentTranslation;
import dev.devatlas.server.domain.Lesson;
import dev.devatlas.server.domain.MindMap;
import dev.devatlas.server.domain.Module;
import dev.devatlas.server.domain.Track;
import dev.devatlas.server.domain.TranslationEntityType;
import dev.devatlas.server.domain.UserLocale;
import dev.devatlas.server.domain.UserProgress;
import dev.devatlas.server.domain.UserProgressId;
import dev.devatlas.server.repository.CodeExampleRepository;
import dev.devatlas.server.repository.LessonRepository;
import dev.devatlas.server.repository.MindMapRepository;
import dev.devatlas.server.repository.ModuleRepository;
import dev.devatlas.server.repository.TrackRepository;
import dev.devatlas.server.repository.UserProgressRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-only queries behind the six public content endpoints of §5.2 (tracks, a track's detail, a
 * lesson, and a track's mind map -- blog reads live in {@link PublicBlogService}). Every method
 * here filters to published content only and resolves the caller's requested locale against {@link
 * ContentTranslation}, falling back to the canonical English columns.
 */
@Service
public class PublicContentService {

  private static final Map<String, String> TRACK_SORT_FIELDS =
      Map.of("order", "displayOrder", "title", "title", "updated_at", "updatedAt");
  private static final Sort TRACK_DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "displayOrder");

  private final TrackRepository tracks;
  private final ModuleRepository modules;
  private final LessonRepository lessons;
  private final CodeExampleRepository codeExamples;
  private final MindMapRepository mindMaps;
  private final UserProgressRepository progress;
  private final TranslationLookup translationLookup;
  private final JsonMapper jsonMapper;

  public PublicContentService(
      TrackRepository tracks,
      ModuleRepository modules,
      LessonRepository lessons,
      CodeExampleRepository codeExamples,
      MindMapRepository mindMaps,
      UserProgressRepository progress,
      TranslationLookup translationLookup,
      JsonMapper jsonMapper) {
    this.tracks = tracks;
    this.modules = modules;
    this.lessons = lessons;
    this.codeExamples = codeExamples;
    this.mindMaps = mindMaps;
    this.progress = progress;
    this.translationLookup = translationLookup;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(readOnly = true)
  public PageResponse<TrackListItemResponse> listTracks(
      Integer page, Integer size, List<String> sort, UserLocale requested) {
    Pageable pageable = PageQuery.resolve(page, size, sort, TRACK_SORT_FIELDS, TRACK_DEFAULT_SORT);
    Page<Track> result = tracks.findByPublishedTrue(pageable);
    List<TrackListItemResponse> items =
        result.getContent().stream().map(t -> toListItem(t, requested)).toList();
    return PageResponse.of(items, result.getNumber(), result.getSize(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public TrackDetailResponse getTrackDetail(String slug, UserLocale requested) {
    Track track = requirePublishedTrack(slug);
    Optional<ContentTranslation> trackTranslation =
        translationLookup.find(TranslationEntityType.TRACK, track.getId(), requested);

    List<Module> trackModules = modules.findByTrackIdOrderByDisplayOrderAsc(track.getId());
    List<ModuleSummaryResponse> moduleResponses =
        trackModules.stream().map(m -> toModuleSummary(m, requested)).toList();

    boolean hasMindMap = mindMaps.findByTrackId(track.getId()).isPresent();

    return new TrackDetailResponse(
        track.getId(),
        track.getSlug(),
        trackTranslation.map(ContentTranslation::getTitle).orElse(track.getTitle()),
        trackTranslation.map(ContentTranslation::getBody).orElse(track.getDescription()),
        track.getIcon(),
        track.getDisplayOrder(),
        localeOf(requested, trackTranslation.isPresent()),
        requested.code(),
        isFallback(requested, trackTranslation.isPresent()),
        track.getContentVersion(),
        hasMindMap,
        track.getUpdatedAt(),
        moduleResponses);
  }

  @Transactional(readOnly = true)
  public LessonDetailResponse getLesson(String slug, UserLocale requested, UUID callerUserId) {
    Lesson lesson =
        lessons
            .findBySlugAndDeletedAtIsNull(slug)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.LESSON_NOT_FOUND,
                        "No lesson exists with slug '%s'.".formatted(slug)));
    Module module =
        modules
            .findById(lesson.getModuleId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.LESSON_NOT_FOUND, "The lesson's module no longer exists."));
    Track track =
        tracks
            .findById(module.getTrackId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.LESSON_NOT_FOUND, "The lesson's track no longer exists."));
    if (!track.isPublished()) {
      throw new ApiException(
          ErrorCode.LESSON_NOT_FOUND, "No lesson exists with slug '%s'.".formatted(slug));
    }

    Optional<ContentTranslation> lessonTranslation =
        translationLookup.find(TranslationEntityType.LESSON, lesson.getId(), requested);
    Optional<ContentTranslation> moduleTranslation =
        translationLookup.find(TranslationEntityType.MODULE, module.getId(), requested);
    Optional<ContentTranslation> trackTranslation =
        translationLookup.find(TranslationEntityType.TRACK, track.getId(), requested);

    List<CodeExampleResponse> codeExampleResponses =
        codeExamples.findByLessonIdOrderByDisplayOrderAsc(lesson.getId()).stream()
            .map(this::toCodeExampleResponse)
            .toList();

    ProgressResponse progressResponse =
        callerUserId == null ? null : findProgress(callerUserId, lesson.getId());

    return new LessonDetailResponse(
        lesson.getId(),
        lesson.getSlug(),
        lessonTranslation.map(ContentTranslation::getTitle).orElse(lesson.getTitle()),
        lessonTranslation.map(ContentTranslation::getBody).orElse(lesson.getBodyMarkdown()),
        lesson.getDifficulty(),
        lesson.getEstimatedMinutes(),
        lesson.getDisplayOrder(),
        lesson.getContentVersion(),
        localeOf(requested, lessonTranslation.isPresent()),
        requested.code(),
        isFallback(requested, lessonTranslation.isPresent()),
        lesson.getUpdatedAt(),
        new LessonModuleRef(
            module.getId(),
            moduleTranslation.map(ContentTranslation::getTitle).orElse(module.getTitle()),
            module.getDisplayOrder()),
        new LessonTrackRef(
            track.getId(),
            track.getSlug(),
            trackTranslation.map(ContentTranslation::getTitle).orElse(track.getTitle())),
        codeExampleResponses,
        progressResponse);
  }

  @Transactional(readOnly = true)
  public MindMapResponse getMindMap(String trackSlug, UserLocale requested) {
    Track track = requirePublishedTrack(trackSlug);
    MindMap mindMap =
        mindMaps
            .findByTrackId(track.getId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.MIND_MAP_NOT_FOUND,
                        "Track '%s' has no mind map.".formatted(trackSlug)));
    MindMapNodeResponse root = jsonMapper.readValue(mindMap.getRoot(), MindMapNodeResponse.class);
    TranslationFallback fallback = TranslationFallback.mindMap(requested);
    return new MindMapResponse(
        mindMap.getId(),
        mindMap.getTrackId(),
        mindMap.getContentVersion(),
        mindMap.getUpdatedAt(),
        fallback.locale(),
        fallback.requestedLocale(),
        fallback.isFallback(),
        root);
  }

  private ProgressResponse findProgress(UUID userId, UUID lessonId) {
    return progress
        .findById(new UserProgressId(userId, lessonId))
        .map(UserProgress::getCompletedAt)
        .map(ProgressResponse::new)
        .orElse(null);
  }

  private TrackListItemResponse toListItem(Track track, UserLocale requested) {
    Optional<ContentTranslation> translation =
        translationLookup.find(TranslationEntityType.TRACK, track.getId(), requested);
    List<UUID> moduleIds =
        modules.findByTrackIdOrderByDisplayOrderAsc(track.getId()).stream()
            .map(Module::getId)
            .toList();
    long lessonCount =
        moduleIds.isEmpty() ? 0 : lessons.countByModuleIdInAndDeletedAtIsNull(moduleIds);
    return new TrackListItemResponse(
        track.getId(),
        track.getSlug(),
        translation.map(ContentTranslation::getTitle).orElse(track.getTitle()),
        translation.map(ContentTranslation::getBody).orElse(track.getDescription()),
        track.getIcon(),
        track.getDisplayOrder(),
        localeOf(requested, translation.isPresent()),
        requested.code(),
        isFallback(requested, translation.isPresent()),
        moduleIds.size(),
        lessonCount,
        track.getContentVersion(),
        track.getUpdatedAt());
  }

  private ModuleSummaryResponse toModuleSummary(Module module, UserLocale requested) {
    Optional<ContentTranslation> translation =
        translationLookup.find(TranslationEntityType.MODULE, module.getId(), requested);
    List<LessonSummaryResponse> lessonResponses =
        lessons.findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc(module.getId()).stream()
            .map(l -> toLessonSummary(l, requested))
            .toList();
    return new ModuleSummaryResponse(
        module.getId(),
        translation.map(ContentTranslation::getTitle).orElse(module.getTitle()),
        module.getDisplayOrder(),
        module.getEstimatedMinutes(),
        localeOf(requested, translation.isPresent()),
        requested.code(),
        isFallback(requested, translation.isPresent()),
        lessonResponses);
  }

  private LessonSummaryResponse toLessonSummary(Lesson lesson, UserLocale requested) {
    Optional<ContentTranslation> translation =
        translationLookup.find(TranslationEntityType.LESSON, lesson.getId(), requested);
    return new LessonSummaryResponse(
        lesson.getId(),
        lesson.getSlug(),
        translation.map(ContentTranslation::getTitle).orElse(lesson.getTitle()),
        lesson.getDifficulty(),
        lesson.getEstimatedMinutes(),
        lesson.getDisplayOrder(),
        lesson.getContentVersion(),
        localeOf(requested, translation.isPresent()),
        requested.code(),
        isFallback(requested, translation.isPresent()),
        lesson.getUpdatedAt());
  }

  private CodeExampleResponse toCodeExampleResponse(CodeExample example) {
    return new CodeExampleResponse(
        example.getId(),
        example.getLanguage(),
        example.getCode(),
        example.getCaption(),
        example.getDisplayOrder());
  }

  private Track requirePublishedTrack(String slug) {
    return tracks
        .findBySlugAndPublishedTrue(slug)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.TRACK_NOT_FOUND, "No track exists with slug '%s'.".formatted(slug)));
  }

  private static String localeOf(UserLocale requested, boolean translationFound) {
    return TranslationFallback.of(requested, translationFound).locale();
  }

  private static boolean isFallback(UserLocale requested, boolean translationFound) {
    return TranslationFallback.of(requested, translationFound).isFallback();
  }
}
