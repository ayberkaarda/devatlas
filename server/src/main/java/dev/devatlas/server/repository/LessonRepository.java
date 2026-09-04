package dev.devatlas.server.repository;

import dev.devatlas.server.domain.Lesson;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Lesson}, always scoped to non-deleted rows. */
public interface LessonRepository extends JpaRepository<Lesson, UUID> {

  Optional<Lesson> findBySlugAndDeletedAtIsNull(String slug);

  List<Lesson> findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc(UUID moduleId);

  boolean existsBySlugAndDeletedAtIsNull(String slug);
}
