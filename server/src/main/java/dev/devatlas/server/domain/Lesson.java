package dev.devatlas.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A single lesson within a {@link Module}: its Markdown body plus the packaged, hashed bytes served
 * to clients once {@code contentVersion} has been built. Soft-deleted via {@code deletedAt} because
 * lessons are referenced by user progress history.
 */
@Entity
@Table(name = "lessons")
public class Lesson {

  @Id private UUID id;

  @Column(name = "module_id", nullable = false)
  private UUID moduleId;

  @Column(name = "slug", nullable = false, length = 80)
  private String slug;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "body_markdown", nullable = false, columnDefinition = "text")
  private String bodyMarkdown;

  @Enumerated(EnumType.STRING)
  @Column(name = "difficulty", nullable = false, length = 16)
  private Difficulty difficulty;

  @Column(name = "estimated_minutes")
  private Integer estimatedMinutes;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "content_version", nullable = false)
  private int contentVersion;

  @Column(name = "sha256", length = 64)
  private String sha256;

  @Column(name = "package_bytes")
  private byte[] packageBytes;

  @Column(name = "package_size_bytes")
  private Integer packageSizeBytes;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  public Lesson() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getModuleId() {
    return moduleId;
  }

  public void setModuleId(UUID moduleId) {
    this.moduleId = moduleId;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getBodyMarkdown() {
    return bodyMarkdown;
  }

  public void setBodyMarkdown(String bodyMarkdown) {
    this.bodyMarkdown = bodyMarkdown;
  }

  public Difficulty getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(Difficulty difficulty) {
    this.difficulty = difficulty;
  }

  public Integer getEstimatedMinutes() {
    return estimatedMinutes;
  }

  public void setEstimatedMinutes(Integer estimatedMinutes) {
    this.estimatedMinutes = estimatedMinutes;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(int displayOrder) {
    this.displayOrder = displayOrder;
  }

  public int getContentVersion() {
    return contentVersion;
  }

  public void setContentVersion(int contentVersion) {
    this.contentVersion = contentVersion;
  }

  public String getSha256() {
    return sha256;
  }

  public void setSha256(String sha256) {
    this.sha256 = sha256;
  }

  public byte[] getPackageBytes() {
    return packageBytes;
  }

  public void setPackageBytes(byte[] packageBytes) {
    this.packageBytes = packageBytes;
  }

  public Integer getPackageSizeBytes() {
    return packageSizeBytes;
  }

  public void setPackageSizeBytes(Integer packageSizeBytes) {
    this.packageSizeBytes = packageSizeBytes;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
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
