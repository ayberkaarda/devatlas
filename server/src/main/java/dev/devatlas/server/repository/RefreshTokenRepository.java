package dev.devatlas.server.repository;

import dev.devatlas.server.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The refresh-token store. Tokens are addressed by digest; the plaintext is never stored. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenDigest(String tokenDigest);

  List<RefreshToken> findByFamilyId(UUID familyId);

  /**
   * Revokes every still-live token of one family in a single statement.
   *
   * <p>Reuse detection has to close the whole chain at once, including the currently valid token:
   * leaving one alive would let whichever party holds it keep the session the other just lost.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE RefreshToken t
         SET t.revokedAt = :revokedAt, t.revokedReason = :reason
       WHERE t.familyId = :familyId
         AND t.revokedAt IS NULL
      """)
  int revokeFamily(
      @Param("familyId") UUID familyId,
      @Param("revokedAt") Instant revokedAt,
      @Param("reason") String reason);

  /**
   * Revokes every live token of one user except those in the family named, so a password change
   * signs out every other device without signing out the device performing it.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE RefreshToken t
         SET t.revokedAt = :revokedAt, t.revokedReason = :reason
       WHERE t.userId = :userId
         AND t.revokedAt IS NULL
         AND (:keepFamilyId IS NULL OR t.familyId <> :keepFamilyId)
      """)
  int revokeAllForUserExceptFamily(
      @Param("userId") UUID userId,
      @Param("keepFamilyId") UUID keepFamilyId,
      @Param("revokedAt") Instant revokedAt,
      @Param("reason") String reason);

  /** Number of tokens in a family that were minted by the lost-response grace path. */
  int countByFamilyIdAndMintedByGraceTrue(UUID familyId);
}
