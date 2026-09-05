package dev.devatlas.server.repository;

import dev.devatlas.server.domain.BlogPost;
import dev.devatlas.server.domain.BlogSource;
import dev.devatlas.server.domain.BlogStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for {@link BlogPost}. */
public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

  Optional<BlogPost> findBySlug(String slug);

  Optional<BlogPost> findBySlugAndStatus(String slug, BlogStatus status);

  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, UUID id);

  Page<BlogPost> findByStatus(BlogStatus status, Pageable pageable);

  Page<BlogPost> findByStatusAndSource(BlogStatus status, BlogSource source, Pageable pageable);

  /**
   * The admin list (§5.4/§5.5.2, {@code GET /admin/blog/posts}): {@code status}, {@code source} and
   * {@code q} (a case-insensitive title substring) are each optional and independent. A single JPQL
   * query with a null-means-"don't filter" predicate per parameter covers every combination without
   * enumerating eight derived-query methods.
   */
  @Query(
      """
      SELECT p FROM BlogPost p
      WHERE (:status IS NULL OR p.status = :status)
        AND (:source IS NULL OR p.source = :source)
        AND (:query IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')))
      """)
  Page<BlogPost> search(
      @Param("status") BlogStatus status,
      @Param("source") BlogSource source,
      @Param("query") String query,
      Pageable pageable);
}
