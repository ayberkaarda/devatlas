package dev.bytelore.server.repository;

import dev.bytelore.server.domain.SourceUpdate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link SourceUpdate}. */
public interface SourceUpdateRepository extends JpaRepository<SourceUpdate, UUID> {

  Optional<SourceUpdate> findByWhitelistSourceIdAndContentHash(
      UUID whitelistSourceId, String contentHash);

  boolean existsByWhitelistSourceId(UUID whitelistSourceId);
}
