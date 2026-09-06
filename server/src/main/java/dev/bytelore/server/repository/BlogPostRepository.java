package dev.bytelore.server.repository;

import dev.bytelore.server.domain.BlogPost;
import dev.bytelore.server.domain.BlogSource;
import dev.bytelore.server.domain.BlogStatus;
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
   *
   * <p>{@code status} and {@code source} keep the {@code :param IS NULL OR field = :param} form
   * because Hibernate resolves each parameter's bind type from the entity attribute it is compared
   * against (an enum column), so the driver still receives an explicit type when the value is null.
   * The title filter cannot use that form: {@code titlePattern} is only ever compared through
   * {@code LOWER(...)} and {@code LIKE}, expressions whose argument type Hibernate does not always
   * resolve back to the parameter when the bound value is null, and PgJDBC's fallback for an
   * untyped null is {@code bytea} -- which then fails {@code lower(bytea)} the moment the search
   * box is empty (measured against a live server: {@code function lower(bytea) does not exist}).
   * Passing a pre-built pattern removes the failure mode at its source instead of routing around
   * it: {@code titlePattern} is always a non-null {@code String} ({@code "%"} when the caller
   * supplied no {@code q}, {@code "%term%"} otherwise -- see {@code AdminBlogPostService#list}), so
   * this predicate never binds a null parameter and Hibernate never has to guess its type.
   */
  @Query(
      """
      SELECT p FROM BlogPost p
      WHERE (:status IS NULL OR p.status = :status)
        AND (:source IS NULL OR p.source = :source)
        AND LOWER(p.title) LIKE LOWER(:titlePattern)
      """)
  Page<BlogPost> search(
      @Param("status") BlogStatus status,
      @Param("source") BlogSource source,
      @Param("titlePattern") String titlePattern,
      Pageable pageable);
}
