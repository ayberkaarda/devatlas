package dev.devatlas.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The single mind map tree belonging to a {@link Track}, stored as a JSON document plus the
 * packaged, hashed bytes served to clients once {@code contentVersion} has been built.
 */
@Entity
@Table(name = "mind_maps")
public class MindMap {

  @Id private UUID id;

  @Column(name = "track_id", nullable = false)
  private UUID trackId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "root", nullable = false, columnDefinition = "jsonb")
  private String root;

  @Column(name = "content_version", nullable = false)
  private int contentVersion;

  @Column(name = "sha256", length = 64)
  private String sha256;

  @Column(name = "package_bytes")
  private byte[] packageBytes;

  @Column(name = "package_size_bytes")
  private Integer packageSizeBytes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  public MindMap() {}

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

  public String getRoot() {
    return root;
  }

  public void setRoot(String root) {
    this.root = root;
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
