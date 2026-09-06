package dev.bytelore.server.pipeline;

import dev.bytelore.server.common.UuidV7;
import dev.bytelore.server.domain.BlogStatus;
import dev.bytelore.server.domain.PipelineAuditLog;
import dev.bytelore.server.domain.PipelineStep;
import dev.bytelore.server.repository.PipelineAuditLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one place a {@link PipelineAuditLog} row is written. Every fetch, verify, draft, submit,
 * approve, reject, publish and unpublish goes through here -- an append-only trail with no write
 * endpoint of its own on the API (§5.7).
 */
@Component
public class PipelineAuditor {

  private final PipelineAuditLogRepository repository;
  private final Clock clock;

  public PipelineAuditor(PipelineAuditLogRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public PipelineAuditLog record(
      PipelineStep step,
      UUID blogPostId,
      UUID sourceUpdateId,
      UUID whitelistSourceId,
      UUID actorUserId,
      BlogStatus fromStatus,
      BlogStatus toStatus,
      String reason) {
    PipelineAuditLog log = new PipelineAuditLog();
    log.setId(UuidV7.randomUuid());
    log.setStep(step);
    log.setBlogPostId(blogPostId);
    log.setSourceUpdateId(sourceUpdateId);
    log.setWhitelistSourceId(whitelistSourceId);
    log.setActorUserId(actorUserId);
    log.setFromStatus(fromStatus);
    log.setToStatus(toStatus);
    log.setReason(reason);
    log.setOccurredAt(Instant.now(clock).truncatedTo(ChronoUnit.MILLIS));
    return repository.save(log);
  }
}
