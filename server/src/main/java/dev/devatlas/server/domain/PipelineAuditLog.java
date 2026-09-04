package dev.devatlas.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One append-only record of a pipeline step or human decision, in the same transaction as the
 * change it describes. {@code actorUserId} is null for machine steps and non-null for every human
 * decision.
 */
@Entity
@Table(name = "pipeline_audit_log")
public class PipelineAuditLog {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "step", nullable = false, length = 16)
  private PipelineStep step;

  @Column(name = "blog_post_id")
  private UUID blogPostId;

  @Column(name = "source_update_id")
  private UUID sourceUpdateId;

  @Column(name = "whitelist_source_id")
  private UUID whitelistSourceId;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 20)
  private BlogStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", length = 20)
  private BlogStatus toStatus;

  @Column(name = "reason", length = 500)
  private String reason;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  public PipelineAuditLog() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public PipelineStep getStep() {
    return step;
  }

  public void setStep(PipelineStep step) {
    this.step = step;
  }

  public UUID getBlogPostId() {
    return blogPostId;
  }

  public void setBlogPostId(UUID blogPostId) {
    this.blogPostId = blogPostId;
  }

  public UUID getSourceUpdateId() {
    return sourceUpdateId;
  }

  public void setSourceUpdateId(UUID sourceUpdateId) {
    this.sourceUpdateId = sourceUpdateId;
  }

  public UUID getWhitelistSourceId() {
    return whitelistSourceId;
  }

  public void setWhitelistSourceId(UUID whitelistSourceId) {
    this.whitelistSourceId = whitelistSourceId;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(UUID actorUserId) {
    this.actorUserId = actorUserId;
  }

  public BlogStatus getFromStatus() {
    return fromStatus;
  }

  public void setFromStatus(BlogStatus fromStatus) {
    this.fromStatus = fromStatus;
  }

  public BlogStatus getToStatus() {
    return toStatus;
  }

  public void setToStatus(BlogStatus toStatus) {
    this.toStatus = toStatus;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }
}
