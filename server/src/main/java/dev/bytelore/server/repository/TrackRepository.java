package dev.bytelore.server.repository;

import dev.bytelore.server.domain.Track;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Track}. */
public interface TrackRepository extends JpaRepository<Track, UUID> {

  Optional<Track> findBySlug(String slug);

  Optional<Track> findBySlugAndPublishedTrue(String slug);

  Page<Track> findByPublishedTrue(Pageable pageable);

  Page<Track> findByPublished(boolean published, Pageable pageable);

  /**
   * Every published track, for the catalog manifest. Unordered on purpose: the catalog's row order
   * is fixed in the service by {@code track_id} rendered as a string, because PostgreSQL orders
   * {@code uuid} by its sixteen bytes while {@link java.util.UUID#compareTo} compares two signed
   * longs, and the two orders disagree. A manifest whose byte stability depended on which of them
   * produced the list would not be stable at all.
   */
  List<Track> findAllByPublishedTrue();

  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, UUID id);
}
