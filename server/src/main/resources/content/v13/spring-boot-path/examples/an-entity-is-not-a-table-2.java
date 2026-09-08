// Spring Boot 4.1, Jakarta Persistence 3.2 (Hibernate 7.4 as the provider).
// A mapping that says out loud what the column is, rather than letting a naming
// strategy decide. Compiled against the project's dependencies; it is not run
// here, because an entity only does anything inside a persistence unit attached
// to a database whose schema already matches it.
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

enum LessonDifficulty {
  BEGINNER,
  INTERMEDIATE,
  ADVANCED
}

@Entity
@Table(name = "lessons")
class LessonRow {

  // Assigned by the application, not by the database. A key the application chooses
  // can be known before the insert, which is what lets a batch of related rows be
  // built in memory and written in one go.
  @Id private UUID id;

  // A plain UUID column, not an @ManyToOne. The association exists in the schema as
  // a foreign key; whether the object graph also carries it is a separate decision,
  // and carrying it costs a fetch every time the row is loaded.
  @Column(name = "module_id", nullable = false)
  private UUID moduleId;

  @Column(name = "slug", nullable = false, length = 80)
  private String slug;

  // `text`, not `varchar(255)`. The default column type for a String is a bounded
  // one, and a lesson body is not bounded by anything useful.
  @Column(name = "body_markdown", nullable = false, columnDefinition = "text")
  private String bodyMarkdown;

  // STRING, never ORDINAL: the column then holds the name of the constant, and
  // reordering the enum in Java cannot change what an already-stored row means.
  @Enumerated(EnumType.STRING)
  @Column(name = "difficulty", nullable = false, length = 16)
  private LessonDifficulty difficulty;

  // Integer rather than int, because the column is nullable and a primitive would
  // silently turn "not recorded" into zero.
  @Column(name = "estimated_minutes")
  private Integer estimatedMinutes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  // Optimistic locking. Two transactions that read the same row and both write it
  // do not silently overwrite one another; the second one fails.
  @Version
  @Column(name = "version", nullable = false)
  private long version;

  // A persistence provider needs a no-argument constructor to materialise a row.
  protected LessonRow() {}

  UUID getId() {
    return id;
  }

  void setId(UUID id) {
    this.id = id;
  }

  UUID getModuleId() {
    return moduleId;
  }

  void setModuleId(UUID moduleId) {
    this.moduleId = moduleId;
  }

  String getSlug() {
    return slug;
  }

  void setSlug(String slug) {
    this.slug = slug;
  }

  String getBodyMarkdown() {
    return bodyMarkdown;
  }

  void setBodyMarkdown(String bodyMarkdown) {
    this.bodyMarkdown = bodyMarkdown;
  }

  LessonDifficulty getDifficulty() {
    return difficulty;
  }

  void setDifficulty(LessonDifficulty difficulty) {
    this.difficulty = difficulty;
  }

  Integer getEstimatedMinutes() {
    return estimatedMinutes;
  }

  void setEstimatedMinutes(Integer estimatedMinutes) {
    this.estimatedMinutes = estimatedMinutes;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  long getVersion() {
    return version;
  }
}
