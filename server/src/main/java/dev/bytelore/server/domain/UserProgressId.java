package dev.bytelore.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key of {@link UserProgress}: one row per person per lesson. */
@Embeddable
public class UserProgressId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "lesson_id", nullable = false)
  private UUID lessonId;

  public UserProgressId() {}

  public UserProgressId(UUID userId, UUID lessonId) {
    this.userId = userId;
    this.lessonId = lessonId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public UUID getLessonId() {
    return lessonId;
  }

  public void setLessonId(UUID lessonId) {
    this.lessonId = lessonId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof UserProgressId that)) {
      return false;
    }
    return Objects.equals(userId, that.userId) && Objects.equals(lessonId, that.lessonId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, lessonId);
  }
}
