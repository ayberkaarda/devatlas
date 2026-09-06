package dev.bytelore.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * An official content source the ingest pipeline is allowed to fetch from, and the URL pattern used
 * to independently re-verify a version string before it is trusted.
 */
@Entity
@Table(name = "whitelist_sources")
public class WhitelistSource {

  @Id private UUID id;

  @Column(name = "name", nullable = false, length = 120)
  private String name;

  @Column(name = "feed_url", nullable = false, length = 2000)
  private String feedUrl;

  @Column(name = "verify_url_pattern", nullable = false, length = 2000)
  private String verifyUrlPattern;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "last_fetched_at")
  private Instant lastFetchedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  public WhitelistSource() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getFeedUrl() {
    return feedUrl;
  }

  public void setFeedUrl(String feedUrl) {
    this.feedUrl = feedUrl;
  }

  public String getVerifyUrlPattern() {
    return verifyUrlPattern;
  }

  public void setVerifyUrlPattern(String verifyUrlPattern) {
    this.verifyUrlPattern = verifyUrlPattern;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Instant getLastFetchedAt() {
    return lastFetchedAt;
  }

  public void setLastFetchedAt(Instant lastFetchedAt) {
    this.lastFetchedAt = lastFetchedAt;
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
