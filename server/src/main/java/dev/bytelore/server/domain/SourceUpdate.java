package dev.bytelore.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One raw item fetched from a {@link WhitelistSource}, its deduplication hash, and the ordered
 * outcome of the independent verification chain that ran against it. Append-only: there is no
 * update path once a row is written.
 */
@Entity
@Table(name = "source_updates")
public class SourceUpdate {

  @Id private UUID id;

  @Column(name = "whitelist_source_id", nullable = false)
  private UUID whitelistSourceId;

  @Column(name = "raw_content", nullable = false, columnDefinition = "text")
  private String rawContent;

  @Column(name = "version_string", nullable = false, length = 64)
  private String versionString;

  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "verify_status", nullable = false, length = 16)
  private VerifyStatus verifyStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "verify_checks", columnDefinition = "jsonb")
  private String verifyChecks;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public SourceUpdate() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getWhitelistSourceId() {
    return whitelistSourceId;
  }

  public void setWhitelistSourceId(UUID whitelistSourceId) {
    this.whitelistSourceId = whitelistSourceId;
  }

  public String getRawContent() {
    return rawContent;
  }

  public void setRawContent(String rawContent) {
    this.rawContent = rawContent;
  }

  public String getVersionString() {
    return versionString;
  }

  public void setVersionString(String versionString) {
    this.versionString = versionString;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }

  public void setFetchedAt(Instant fetchedAt) {
    this.fetchedAt = fetchedAt;
  }

  public VerifyStatus getVerifyStatus() {
    return verifyStatus;
  }

  public void setVerifyStatus(VerifyStatus verifyStatus) {
    this.verifyStatus = verifyStatus;
  }

  public String getVerifyChecks() {
    return verifyChecks;
  }

  public void setVerifyChecks(String verifyChecks) {
    this.verifyChecks = verifyChecks;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
