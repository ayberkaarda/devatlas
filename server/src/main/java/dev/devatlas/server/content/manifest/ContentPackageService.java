package dev.devatlas.server.content.manifest;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.domain.Lesson;
import dev.devatlas.server.domain.MindMap;
import dev.devatlas.server.domain.Module;
import dev.devatlas.server.domain.Track;
import dev.devatlas.server.repository.LessonRepository;
import dev.devatlas.server.repository.MindMapRepository;
import dev.devatlas.server.repository.ModuleRepository;
import dev.devatlas.server.repository.TrackRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves {@code GET /content/{entityType}/{entityId}?version=N} to the stored package bytes, and
 * performs the version negotiation of §4.4.
 *
 * <p>Publication is checked through the owning track on every read. A lesson is only publicly
 * addressable while the track above it is published, so an unpublished track's packages have to
 * become unfetchable at the same instant they leave the manifests -- otherwise a client holding a
 * stale manifest could keep downloading content that was deliberately withdrawn.
 */
@Service
public class ContentPackageService {

  private final LessonRepository lessons;
  private final MindMapRepository mindMaps;
  private final ModuleRepository modules;
  private final TrackRepository tracks;

  public ContentPackageService(
      LessonRepository lessons,
      MindMapRepository mindMaps,
      ModuleRepository modules,
      TrackRepository tracks) {
    this.lessons = lessons;
    this.mindMaps = mindMaps;
    this.modules = modules;
    this.tracks = tracks;
  }

  /**
   * @param type the entity kind named by the lowercase path segment
   * @param entityId the entity's own identifier, the one the manifest listed
   * @param version the {@code content_version} the caller intends to download -- required, because
   *     a client always knows which version it planned for and omitting it would let a manifest and
   *     a package silently disagree
   * @throws ContentVersionSupersededException 409, when a version older than the current one is
   *     requested
   * @throws ApiException the per-type not-found code (404), when the entity is unknown, deleted,
   *     unpublished, has no stored package, or was asked for at a version newer than the current
   *     one
   */
  @Transactional(readOnly = true)
  public ServedPackage load(ContentEntityType type, UUID entityId, int version) {
    return switch (type) {
      case LESSON -> loadLesson(entityId, version);
      case MIND_MAP -> loadMindMap(entityId, version);
    };
  }

  private ServedPackage loadLesson(UUID entityId, int version) {
    Lesson lesson =
        lessons
            .findById(entityId)
            .filter(candidate -> candidate.getDeletedAt() == null)
            .filter(candidate -> candidate.getSha256() != null)
            .filter(candidate -> isPublished(candidate.getModuleId()))
            .orElseThrow(() -> notFound(ContentEntityType.LESSON));
    negotiate(ContentEntityType.LESSON, version, lesson.getContentVersion());
    return new ServedPackage(lesson.getPackageBytes(), lesson.getSha256());
  }

  private ServedPackage loadMindMap(UUID entityId, int version) {
    MindMap mindMap =
        mindMaps
            .findById(entityId)
            .filter(candidate -> candidate.getSha256() != null)
            .filter(candidate -> isTrackPublished(candidate.getTrackId()))
            .orElseThrow(() -> notFound(ContentEntityType.MIND_MAP));
    negotiate(ContentEntityType.MIND_MAP, version, mindMap.getContentVersion());
    return new ServedPackage(mindMap.getPackageBytes(), mindMap.getSha256());
  }

  /**
   * §4.4, in the order the table states it: current serves, older is superseded, newer is not
   * found.
   *
   * <p>A version newer than the current one is a 404 rather than a 409 on purpose. It does not
   * describe content the server ever had, so there is nothing to re-plan against and no current
   * version worth returning -- the client's manifest is from a future the server has not reached,
   * which in practice means a restored backup or a rollback (§8).
   */
  private static void negotiate(ContentEntityType type, int requested, int current) {
    if (requested == current) {
      return;
    }
    if (requested < current) {
      throw new ContentVersionSupersededException(requested, current);
    }
    throw notFound(type);
  }

  private static ApiException notFound(ContentEntityType type) {
    return new ApiException(
        type.notFoundCode(), "No published %s package exists for that id.".formatted(type.name()));
  }

  private boolean isPublished(UUID moduleId) {
    return modules
        .findById(moduleId)
        .map(Module::getTrackId)
        .filter(this::isTrackPublished)
        .isPresent();
  }

  private boolean isTrackPublished(UUID trackId) {
    return Optional.ofNullable(trackId)
        .flatMap(tracks::findById)
        .filter(Track::isPublished)
        .isPresent();
  }
}
