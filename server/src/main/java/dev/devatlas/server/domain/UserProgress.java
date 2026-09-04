package dev.devatlas.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One person's state on one lesson.
 *
 * <p>{@code completedAt} is nullable <em>and meaningful</em>: null records "explicitly marked
 * incomplete", which is a real action a person takes and which has to survive a sync round trip as
 * such rather than being read as an absence of data.
 *
 * <p>{@code clientUpdatedAt} is the client's own clock and the value the last-write-wins comparison
 * is made on. It is untrusted input: an implausibly future value is clamped by the service rather
 * than rejected, because a device with a dead clock battery is not misbehaving and its owner's
 * completions are real.
 *
 * <p>Rows here are never deleted by any API operation, including deletion of the lesson they refer
 * to. A completion is a fact about a person's history and an editorial decision must not erase it.
 */
@Entity
@Table(name = "user_progress")
public class UserProgress {

  @EmbeddedId private UserProgressId id;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "client_updated_at", nullable = false)
  private Instant clientUpdatedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UserProgress() {}

  public UserProgressId getId() {
    return id;
  }

  public void setId(UserProgressId id) {
    this.id = id;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public Instant getClientUpdatedAt() {
    return clientUpdatedAt;
  }

  public void setClientUpdatedAt(Instant clientUpdatedAt) {
    this.clientUpdatedAt = clientUpdatedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
