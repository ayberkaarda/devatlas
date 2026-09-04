package dev.devatlas.server.repository;

import dev.devatlas.server.domain.MindMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link MindMap}. */
public interface MindMapRepository extends JpaRepository<MindMap, UUID> {

  Optional<MindMap> findByTrackId(UUID trackId);
}
