package dev.devatlas.server.repository;

import dev.devatlas.server.domain.CodeExample;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link CodeExample}. */
public interface CodeExampleRepository extends JpaRepository<CodeExample, UUID> {

  List<CodeExample> findByLessonIdOrderByDisplayOrderAsc(UUID lessonId);
}
