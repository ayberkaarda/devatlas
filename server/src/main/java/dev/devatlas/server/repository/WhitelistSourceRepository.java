package dev.devatlas.server.repository;

import dev.devatlas.server.domain.WhitelistSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link WhitelistSource}. */
public interface WhitelistSourceRepository extends JpaRepository<WhitelistSource, UUID> {

  List<WhitelistSource> findByEnabledTrue();

  Optional<WhitelistSource> findByName(String name);

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);
}
