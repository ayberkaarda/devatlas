// Spring Data JPA 4.1 (Spring Boot 4.1). Three ways to ask, in the order to reach
// for them. Compiled against the project's dependencies; not run here, because a
// repository interface has no implementation until Spring Data creates one against
// a live persistence unit.
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "lessons")
class DefendableLesson {
  @Id UUID id;
  UUID moduleId;
  String slug;
  Instant deletedAt;
  int displayOrder;
  String bodyMarkdown;
}

interface DefendableLessonRepository extends JpaRepository<DefendableLesson, UUID> {

  // Derived. The name is the query, and it is checked against the entity when the
  // application starts rather than when the method is first called.
  Optional<DefendableLesson> findBySlugAndDeletedAtIsNull(String slug);

  List<DefendableLesson> findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc(UUID moduleId);

  // JPQL, once the name would stop being readable. It names entities and fields,
  // not tables and columns, so a column rename that the entity follows does not
  // break it -- and it selects the identifier alone, because the caller is asking
  // whether a reference resolves, not for the row behind it.
  @Query("SELECT l.id FROM DefendableLesson l WHERE l.id IN :ids AND l.deletedAt IS NULL")
  List<UUID> findLiveIds(@Param("ids") Collection<UUID> ids);

  // A write. Without @Modifying this is executed as a select and silently does
  // nothing; clearAutomatically keeps the persistence context from serving a stale
  // copy of a row this statement just changed behind its back.
  @Modifying(clearAutomatically = true)
  @Query("UPDATE DefendableLesson l SET l.deletedAt = :at WHERE l.moduleId = :moduleId")
  int softDeleteByModule(@Param("moduleId") UUID moduleId, @Param("at") Instant at);

  // Native, for something the query language cannot express -- here a full-text
  // match that belongs to PostgreSQL and to nothing else. A paged native query
  // needs its own count query: Spring Data cannot reliably derive one from
  // arbitrary vendor SQL.
  @NativeQuery(
      value =
          """
          SELECT * FROM lessons
          WHERE deleted_at IS NULL
            AND to_tsvector('english', body_markdown) @@ plainto_tsquery('english', :terms)
          """,
      countQuery =
          """
          SELECT count(*) FROM lessons
          WHERE deleted_at IS NULL
            AND to_tsvector('english', body_markdown) @@ plainto_tsquery('english', :terms)
          """)
  Page<DefendableLesson> search(@Param("terms") String terms, Pageable pageable);
}
