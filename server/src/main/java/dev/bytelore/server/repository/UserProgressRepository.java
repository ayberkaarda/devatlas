package dev.bytelore.server.repository;

import dev.bytelore.server.domain.UserProgress;
import dev.bytelore.server.domain.UserProgressId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Per-user lesson completion state. Rows are upserted by the sync endpoint and never deleted.
 *
 * <p>Every query here carries {@code userId} as its first predicate rather than relying on a caller
 * to filter afterwards. One person's progress is not another's to read or overwrite, and a
 * repository method that could return a row belonging to somebody else is a method one careless
 * call site turns into a leak.
 */
public interface UserProgressRepository extends JpaRepository<UserProgress, UserProgressId> {

  /** The caller's rows among a batch's lesson references, for the last-write-wins comparison. */
  List<UserProgress> findByIdUserIdAndIdLessonIdIn(UUID userId, Collection<UUID> lessonIds);

  /** The caller's whole progress set, paged. */
  Page<UserProgress> findByIdUserId(UUID userId, Pageable pageable);

  /**
   * The caller's rows written after {@code since}. The bound is exclusive, so a client can pass
   * back the newest {@code updated_at} it already holds and receive only what it has not seen -- an
   * inclusive bound would re-send that row on every poll forever.
   */
  Page<UserProgress> findByIdUserIdAndUpdatedAtGreaterThan(
      UUID userId, Instant since, Pageable pageable);
}
