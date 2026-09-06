package dev.bytelore.server.repository;

import dev.bytelore.server.domain.MindMap;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link MindMap}. */
public interface MindMapRepository extends JpaRepository<MindMap, UUID> {

  Optional<MindMap> findByTrackId(UUID trackId);

  List<MindMap> findByTrackIdIn(Collection<UUID> trackIds);

  /** Rows with no stored package. */
  List<MindMap> findBySha256IsNull();
}
