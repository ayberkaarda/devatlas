package dev.devatlas.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A named group of lessons within a {@link Track}, shown to the learner in {@code displayOrder}.
 *
 * <p>Foreign keys on this and every sibling entity are stored as plain {@link UUID} columns rather
 * than {@code @ManyToOne} associations: these entities are read and written through explicit
 * service queries, not by navigating an object graph, and a lazy association would make every read
 * path depend on an open Hibernate session.
 */
@Entity
@Table(name = "modules")
public class Module {

  @Id private UUID id;

  @Column(name = "track_id", nullable = false)
  private UUID trackId;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "estimated_minutes")
  private Integer estimatedMinutes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  public Module() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getTrackId() {
    return trackId;
  }

  public void setTrackId(UUID trackId) {
    this.trackId = trackId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(int displayOrder) {
    this.displayOrder = displayOrder;
  }

  public Integer getEstimatedMinutes() {
    return estimatedMinutes;
  }

  public void setEstimatedMinutes(Integer estimatedMinutes) {
    this.estimatedMinutes = estimatedMinutes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
