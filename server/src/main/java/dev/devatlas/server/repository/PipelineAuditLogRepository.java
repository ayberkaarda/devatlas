package dev.devatlas.server.repository;

import dev.devatlas.server.domain.PipelineAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for {@link PipelineAuditLog}. */
public interface PipelineAuditLogRepository extends JpaRepository<PipelineAuditLog, UUID> {

  List<PipelineAuditLog> findByBlogPostIdOrderByOccurredAtAsc(UUID blogPostId);

  Page<PipelineAuditLog> findByBlogPostId(UUID blogPostId, Pageable pageable);

  /**
   * Deletes one post's audit trail ahead of deleting the post itself (§2.10: a non-{@code
   * PUBLISHED} blog post is hard-deleted). {@code pipeline_audit_log.blog_post_id} has no {@code ON
   * DELETE CASCADE} on purpose -- the audit log is append-only and nothing else may write to it --
   * so the row's own deletion is the one caller allowed to take its history down with it; once the
   * post is gone there is no provenance left to reconstruct.
   */
  @Modifying
  @Query("DELETE FROM PipelineAuditLog p WHERE p.blogPostId = :blogPostId")
  void deleteByBlogPostId(@Param("blogPostId") UUID blogPostId);
}
