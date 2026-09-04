package dev.devatlas.server.repository;

import dev.devatlas.server.domain.UserProgress;
import dev.devatlas.server.domain.UserProgressId;
import org.springframework.data.jpa.repository.JpaRepository;

/** Per-user lesson completion state. Rows are upserted by the sync endpoint and never deleted. */
public interface UserProgressRepository extends JpaRepository<UserProgress, UserProgressId> {}
