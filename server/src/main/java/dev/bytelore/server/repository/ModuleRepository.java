package dev.bytelore.server.repository;

import dev.bytelore.server.domain.Module;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Module}. */
public interface ModuleRepository extends JpaRepository<Module, UUID> {

  List<Module> findByTrackIdOrderByDisplayOrderAsc(UUID trackId);
}
