package dev.bytelore.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One issued refresh token, stored as a digest.
 *
 * <p>The plaintext is a 256-bit random value handed to the client and never written down. What
 * lives here is its SHA-256, so the server cannot reproduce the plaintext of any token it has
 * issued -- including one it issued a second ago. That is a deliberate property with a visible
 * consequence: when a rotation response is lost, the only way to answer the retry is to mint a new
 * pair, because there is nothing to hand back.
 *
 * <p>{@code familyId} links every token descended from one sign-in. Rotation makes a token
 * single-use, and the family is what turns a replay into an actionable signal: the whole chain is
 * revoked at once, not just the value that was replayed.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "token_digest", nullable = false, length = 64)
  private String tokenDigest;

  @Column(name = "device_label", length = 64)
  private String deviceLabel;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /** Set when this token was exchanged. A non-null value makes any further use a replay. */
  @Column(name = "rotated_at")
  private Instant rotatedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_reason", length = 32)
  private String revokedReason;

  /** The token this one rotated into, so a replay can ask whether the chain moved on without it. */
  @Column(name = "successor_id")
  private UUID successorId;

  /**
   * True when this token came from the lost-response grace path rather than a normal rotation.
   * Counting these within a family is worth watching: a healthy client produces very few.
   */
  @Column(name = "minted_by_grace", nullable = false)
  private boolean mintedByGrace;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public RefreshToken() {}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public void setFamilyId(UUID familyId) {
    this.familyId = familyId;
  }

  public String getTokenDigest() {
    return tokenDigest;
  }

  public void setTokenDigest(String tokenDigest) {
    this.tokenDigest = tokenDigest;
  }

  public String getDeviceLabel() {
    return deviceLabel;
  }

  public void setDeviceLabel(String deviceLabel) {
    this.deviceLabel = deviceLabel;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public void setIssuedAt(Instant issuedAt) {
    this.issuedAt = issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getRotatedAt() {
    return rotatedAt;
  }

  public void setRotatedAt(Instant rotatedAt) {
    this.rotatedAt = rotatedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  public String getRevokedReason() {
    return revokedReason;
  }

  public void setRevokedReason(String revokedReason) {
    this.revokedReason = revokedReason;
  }

  public UUID getSuccessorId() {
    return successorId;
  }

  public void setSuccessorId(UUID successorId) {
    this.successorId = successorId;
  }

  public boolean isMintedByGrace() {
    return mintedByGrace;
  }

  public void setMintedByGrace(boolean mintedByGrace) {
    this.mintedByGrace = mintedByGrace;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
