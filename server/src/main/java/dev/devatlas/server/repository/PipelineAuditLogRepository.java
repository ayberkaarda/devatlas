package dev.devatlas.server.repository;

import dev.devatlas.server.domain.PipelineAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link PipelineAuditLog}. */
public interface PipelineAuditLogRepository extends JpaRepository<PipelineAuditLog, UUID> {

  List<PipelineAuditLog> findByBlogPostIdOrderByOccurredAtAsc(UUID blogPostId);
}
