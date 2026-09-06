package dev.bytelore.server.domain;

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
 * A blog post, either written manually or drafted by the ingest pipeline from a verified {@link
 * SourceUpdate}. Moves through {@link BlogStatus} states under human review before publication.
 */
@Entity
@Table(name = "blog_posts")
public class BlogPost {

  @Id private UUID id;

  @Column(name = "slug", nullable = false, length = 80)
  private String slug;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "body_markdown", nullable = false, columnDefinition = "text")
  private String bodyMarkdown;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private BlogStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 8)
  private BlogSource source;

  @Column(name = "source_url", length = 2000)
  private String sourceUrl;

  @Column(name = "source_update_id")
  private UUID sourceUpdateId;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  public BlogPost() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public BlogStatus getStatus() {
    return status;
  }

  public void setStatus(BlogStatus status) {
    this.status = status;
  }

  public BlogSource getSource() {
    return source;
  }

  public void setSource(BlogSource source) {
    this.source = source;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public UUID getSourceUpdateId() {
    return sourceUpdateId;
  }

  public void setSourceUpdateId(UUID sourceUpdateId) {
    this.sourceUpdateId = sourceUpdateId;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
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
