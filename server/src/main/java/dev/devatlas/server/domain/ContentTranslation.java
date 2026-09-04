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
 * A translated title/body pair for one entity in one locale. {@code entityId} is polymorphic across
 * the four translatable tables named by {@code entityType}; there is no foreign key, so the service
 * resolves the target entity before writing.
 */
@Entity
@Table(name = "content_translations")
public class ContentTranslation {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 16)
  private TranslationEntityType entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  // Locale values are 'tr', 'fr', 'de'. English is canonical and lives in the entity's own
  // columns, so no enum member for it exists here.
  @Column(name = "locale", nullable = false, length = 2)
  private String locale;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "body", columnDefinition = "text")
  private String body;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  public ContentTranslation() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public TranslationEntityType getEntityType() {
    return entityType;
  }

  public void setEntityType(TranslationEntityType entityType) {
    this.entityType = entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public void setEntityId(UUID entityId) {
    this.entityId = entityId;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
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
