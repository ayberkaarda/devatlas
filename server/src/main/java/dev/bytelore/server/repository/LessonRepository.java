package dev.bytelore.server.repository;

import dev.bytelore.server.domain.Lesson;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for {@link Lesson}, always scoped to non-deleted rows. */
public interface LessonRepository extends JpaRepository<Lesson, UUID> {

  Optional<Lesson> findBySlugAndDeletedAtIsNull(String slug);

  List<Lesson> findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc(UUID moduleId);

  List<Lesson> findByModuleIdInAndDeletedAtIsNull(Collection<UUID> moduleIds);

  /**
   * Rows with no stored package. Not filtered on {@code deletedAt}: the packaging state of a row is
   * a property of the row, not of its visibility, and a soft-deleted lesson that is later restored
   * should not come back undownloadable.
   */
  List<Lesson> findBySha256IsNull();

  boolean existsBySlugAndDeletedAtIsNull(String slug);

  boolean existsBySlugAndDeletedAtIsNullAndIdNot(String slug, UUID id);

  long countByModuleIdInAndDeletedAtIsNull(Collection<UUID> moduleIds);

  /**
   * The subset of {@code ids} that resolves to a live lesson.
   *
   * <p>Selects identifiers rather than entities because the only question being asked is whether a
   * reference resolves, and the caller asking it -- a progress sync batch -- asks about up to 500
   * lessons at once. Loading entities would pull up to 200,000 characters of markdown per row to
   * answer a question about a primary key.
   */
  @Query("SELECT l.id FROM Lesson l WHERE l.id IN :ids AND l.deletedAt IS NULL")
  List<UUID> findLiveIds(@Param("ids") Collection<UUID> ids);
}
