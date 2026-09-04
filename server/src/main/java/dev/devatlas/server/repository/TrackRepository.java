package dev.devatlas.server.repository;

import dev.devatlas.server.domain.Track;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Track}. */
public interface TrackRepository extends JpaRepository<Track, UUID> {

  Optional<Track> findBySlug(String slug);

  Optional<Track> findBySlugAndPublishedTrue(String slug);
}
