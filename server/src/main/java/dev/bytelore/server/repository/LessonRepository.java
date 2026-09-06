package dev.bytelore.server.repository;

import dev.bytelore.server.domain.Lesson;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
