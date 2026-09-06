package dev.bytelore.server.content.manifest;

import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.content.packaging.LessonPackage;
import dev.bytelore.server.content.packaging.MindMapPackage;
import java.util.Locale;
import java.util.Optional;

/**
 * The two downloadable entity kinds, and the one place their two spellings are related to each
 * other.
 *
 * <p>{@code entityType} is lowercase in the request path -- {@code lesson}, {@code mind_map} --
 * while the same value appears as {@code LESSON} and {@code MIND_MAP} inside every JSON body,
 * matching the enum convention used across the API (§4.3). Holding both forms here, next to the
 * not-found code each kind reports, is what stops the mapping being re-derived with a {@code
 * toLowerCase} at one call site and a {@code switch} at another.
 */
enum ContentEntityType {
  LESSON(LessonPackage.ENTITY_TYPE, ErrorCode.LESSON_NOT_FOUND),
  MIND_MAP(MindMapPackage.ENTITY_TYPE, ErrorCode.MIND_MAP_NOT_FOUND);

  private final String wireName;
  private final ErrorCode notFound;

  ContentEntityType(String wireName, ErrorCode notFound) {
    this.wireName = wireName;
    this.notFound = notFound;
  }

  /** The lowercase form used in the request path. */
  String pathToken() {
    return wireName.toLowerCase(Locale.ROOT);
  }

  /**
   * The per-type not-found code. §4.4 is explicit that this protocol introduces no generic
   * content-not-found code: an entity that does not exist, is not published, or was asked for at a
   * version newer than the current one all report the same per-type code from the shared catalogue.
   */
  ErrorCode notFoundCode() {
    return notFound;
  }

  static Optional<ContentEntityType> fromPathToken(String token) {
    if (token == null) {
      return Optional.empty();
    }
    for (ContentEntityType type : values()) {
      if (type.pathToken().equals(token)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}
